package com.localstream.localstream_mobile.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localstream.localstream_mobile.MainActivity
import com.localstream.localstream_mobile.discovery.MdnsService
import com.localstream.localstream_mobile.network.NetworkInfoProvider
import com.localstream.localstream_mobile.sharing.ShareReceiverActivity
import com.localstream.localstream_mobile.storage.MediaMetadataCache
import com.localstream.localstream_mobile.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalStreamService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var storageManager: StorageManager
        private set
    lateinit var stateHolder: ServerStateHolder
        private set
    lateinit var mediaServer: HttpMediaServer
        private set
    lateinit var networkInfoProvider: NetworkInfoProvider
        private set
    lateinit var mdnsService: MdnsService
        private set
    lateinit var accessControl: AccessControl
        private set
    lateinit var serverEvents: ServerEvents
        private set
    lateinit var metadataCache: MediaMetadataCache
        private set
    private lateinit var deviceFeedbackClient: DeviceFeedbackClient

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: LocalStreamService get() = this@LocalStreamService
    }

    companion object {
        const val ACTION_START = "com.localstream.action.START"
        const val ACTION_STOP = "com.localstream.action.STOP"
        const val EXTRA_PORT = "com.localstream.extra.PORT"

        const val NOTIFICATION_CHANNEL_ID = "localstream_server_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: LocalStreamService? = null
            private set

        // Lets the bridge attach its Dart-forwarding listeners even when the
        // service is (re)created after the Flutter engine already subscribed.
        @Volatile
        var onServiceCreated: ((LocalStreamService) -> Unit)? = null

        private val serviceCreatedHooks = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Any, Boolean>()
        )

        fun addServiceCreatedHook(hook: ((LocalStreamService) -> Unit)?) {
            if (hook == null) return
            if (!serviceCreatedHooks.add(hook)) return
            val prev = onServiceCreated
            onServiceCreated = {
                prev?.invoke(it)
                hook(it)
            }
        }

        fun removeServiceCreatedHook(hook: ((LocalStreamService) -> Unit)?) {
            if (hook == null) return
            if (!serviceCreatedHooks.remove(hook)) return
            // Rebuild the chain from the surviving hooks (plus the root-driven
            // callbacks below). Dropping the removed hook cleanly is not worth
            // rebuilding identity chains; relying on add()-time dedup + the
            // bridge calling registerServiceListeners only once per service
            // keeps the behavior correct even if a stale hook fires.
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        storageManager = StorageManager(applicationContext)
        stateHolder = ServerStateHolder()
        accessControl = AccessControl(applicationContext)
        serverEvents = ServerEvents()
        metadataCache = MediaMetadataCache(applicationContext)
        mediaServer = HttpMediaServer(storageManager, stateHolder, serverEvents, accessControl, metadataCache)
        networkInfoProvider = NetworkInfoProvider(applicationContext)
        mdnsService = MdnsService(applicationContext)
        deviceFeedbackClient = DeviceFeedbackClient(applicationContext)

        // Broadcast library changes and import progress over SSE
        storageManager.onLibraryChanged = {
            serverEvents.broadcast("library", "{\"type\":\"library\"}")
            updateNotification()
        }
        storageManager.onImportProgress = { progress ->
            val json = org.json.JSONObject(progress.toMap()).toString()
            serverEvents.broadcast("import", json)
        }

        // Listen for IP address changes
        networkInfoProvider.onNetworkChanged = { ips ->
            stateHolder.updateAddresses(ips)
            updateNotification()
        }

        // Listen for state changes to update notification
        stateHolder.onStateChanged = {
            updateNotification()
        }

        // Reload persisted SAF folders
        serviceScope.launch {
            storageManager.reloadPersistedImports()
            storageManager.reloadPersistedTrees()
            drainPendingSharedImports()
            // Warm duration metadata so library listings can show runtimes
            try {
                metadataCache.warmDurations(storageManager.getAllSources())
            } catch (_: Exception) {}
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val preferredPort = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
                startServerInternal(preferredPort)
                drainPendingSharedImports()
                deviceFeedbackClient.send()
            }
            ACTION_STOP -> {
                stopServerInternal()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun enqueueSharedImport(uri: Uri) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                storageManager.importUri(uri)
            } catch (_: Exception) {
                // Import failure is isolated; server continues
            }
        }
    }

    private fun drainPendingSharedImports() {
        var uri: Uri?
        while (ShareReceiverActivity.pendingSharedUris.poll().also { uri = it } != null) {
            enqueueSharedImport(uri!!)
        }
    }

    private fun startServerInternal(port: Int) {
        if (mediaServer.isAlive()) {
            return
        }

        stateHolder.transition(ServerState.STARTING)

        // Show foreground notification immediately (Android requirement within 5 seconds)
        val initialNotification = buildNotification("Starting LocalStream server...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, initialNotification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val addresses = networkInfoProvider.getLocalIpAddresses()
                stateHolder.updateAddresses(addresses)
                networkInfoProvider.startMonitoring()

                val boundPort = mediaServer.start(port)

                // Register mDNS
                mdnsService.register(boundPort)

                updateNotification()
            } catch (e: Exception) {
                stateHolder.transition(ServerState.ERROR, e.message)
                updateNotification()
            }
        }
    }

    private fun stopServerInternal() {
        mdnsService.unregister()
        networkInfoProvider.stopMonitoring()
        mediaServer.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "LocalStream Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows LocalStream server status and connected clients"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocalStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LocalStream Media Server")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val status = stateHolder.status
        val text = when (status.state) {
            ServerState.RUNNING -> {
                val ip = status.addresses.firstOrNull() ?: "localhost"
                "Running: http://$ip:${status.port} (${status.activeClients} clients)"
            }
            ServerState.STARTING -> "Starting server..."
            ServerState.STOPPING -> "Stopping server..."
            ServerState.STOPPED -> "Server stopped"
            ServerState.ERROR -> "Server error: ${status.errorMessage ?: "Unknown"}"
        }

        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopServerInternal()
        mdnsService.unregister()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }
}
