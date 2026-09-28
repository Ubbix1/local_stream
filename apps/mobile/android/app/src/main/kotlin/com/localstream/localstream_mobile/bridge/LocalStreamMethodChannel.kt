package com.localstream.localstream_mobile.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.localstream.localstream_mobile.BuildConfig
import com.localstream.localstream_mobile.server.LocalStreamService
import com.localstream.localstream_mobile.server.ServerState
import com.localstream.localstream_mobile.server.ServerStatus
import com.localstream.localstream_mobile.sharing.ShareReceiverActivity
import com.localstream.localstream_mobile.storage.StorageManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalStreamMethodChannel(
    private val activity: Activity,
    private val context: Context,
    messenger: BinaryMessenger
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val methodChannel = MethodChannel(messenger, "localstream/mobile")
    private val eventChannel = EventChannel(messenger, "localstream/mobile/events")
    private val scope = CoroutineScope(Dispatchers.Main)

    private var eventSink: EventChannel.EventSink? = null
    private var serviceCreatedHook: ((LocalStreamService) -> Unit)? = null
    private var pendingPickerResult: MethodChannel.Result? = null

    init {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
    }

    private val storageManager: StorageManager?
        get() = LocalStreamService.instance?.storageManager

    // Only wire each service once. Chaining (instead of replacing) preserves the
    // callbacks already installed by LocalStreamService.onCreate (e.g. the one
    // that updates the foreground notification), so subscribing from Flutter no
    // longer breaks the status notification.
    private val registeredServices =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<LocalStreamService, Boolean>())

    fun registerServiceListeners(service: LocalStreamService) {
        if (!registeredServices.add(service)) return

        val prevStateChanged = service.stateHolder.onStateChanged
        service.stateHolder.onStateChanged = { status ->
            prevStateChanged?.invoke(status)
            sendEvent(mapOf("type" to "status_changed", "data" to status.toMap()))
        }

        val prevLibraryChanged = service.storageManager.onLibraryChanged
        service.storageManager.onLibraryChanged = {
            prevLibraryChanged?.invoke()
            sendEvent(mapOf("type" to "library_changed"))
        }

        val prevImportProgress = service.storageManager.onImportProgress
        service.storageManager.onImportProgress = { progress ->
            prevImportProgress?.invoke(progress)
            sendEvent(progress.toMap())
        }

        val prevNetworkChanged = service.networkInfoProvider.onNetworkChanged
        service.networkInfoProvider.onNetworkChanged = { ips ->
            prevNetworkChanged?.invoke(ips)
            sendEvent(mapOf("type" to "network_changed", "addresses" to ips))
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "pickMediaFiles" -> {
                openMediaPicker(result)
            }

            "startServer" -> {
                val port = call.argument<Int>("port") ?: 8080
                val intent = Intent(context, LocalStreamService::class.java).apply {
                    action = LocalStreamService.ACTION_START
                    putExtra(LocalStreamService.EXTRA_PORT, port)
                }
                context.startService(intent)
                result.success(true)
            }

            "stopServer" -> {
                val intent = Intent(context, LocalStreamService::class.java).apply {
                    action = LocalStreamService.ACTION_STOP
                }
                context.startService(intent)
                result.success(true)
            }

            "getStatus" -> {
                val service = LocalStreamService.instance
                val status = service?.stateHolder?.buildSnapshot() ?: ServerStatus(state = ServerState.STOPPED)
                result.success(status.toMap())
            }

            "getAppVersion" -> {
                result.success(
                    mapOf(
                        "versionName" to BuildConfig.VERSION_NAME,
                        "versionCode" to BuildConfig.VERSION_CODE
                    )
                )
            }

            "getClients" -> {
                val service = LocalStreamService.instance
                val clients = service?.clientTracker?.snapshots() ?: emptyList()
                result.success(clients.map { it.toMap() })
            }

            "removeClient" -> {
                val ip = call.argument<String>("ip")
                val service = LocalStreamService.instance
                val removed = service?.clientTracker?.removeClient(ip) ?: false
                result.success(removed)
            }

            "getNetworkAddresses" -> {
                val service = LocalStreamService.instance
                val ips = service?.networkInfoProvider?.getLocalIpAddresses() ?: emptyList()
                result.success(ips)
            }

            "listFiles" -> {
                val sm = storageManager
                if (sm == null) {
                    result.success(emptyList<Map<String, Any?>>())
                    return
                }
                val files = sm.getAllSources().map { it.toMap() }
                result.success(files)
            }

            "addSafFolder" -> {
                val uriStr = call.argument<String>("uri")
                if (uriStr.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENT", "URI cannot be empty", null)
                    return
                }

                val sm = storageManager
                if (sm == null) {
                    result.error("SERVICE_UNAVAILABLE", "LocalStream service is not running", null)
                    return
                }

                scope.launch {
                    try {
                        val count = sm.addSafTreeUri(Uri.parse(uriStr))
                        result.success(count)
                    } catch (e: Exception) {
                        result.error("SAF_ERROR", "Could not add folder: ${e.message}", null)
                    }
                }
            }

            "removeMediaItem" -> {
                val id = call.argument<String>("id")
                if (id.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENT", "ID cannot be empty", null)
                    return
                }
                val success = storageManager?.removeSource(id) ?: false
                result.success(success)
            }

            "importMediaItem" -> {
                val uriStr = call.argument<String>("uri")
                val targetName = call.argument<String>("targetName")
                if (uriStr.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENT", "URI cannot be empty", null)
                    return
                }

                val sm = storageManager
                if (sm == null) {
                    result.error("SERVICE_UNAVAILABLE", "LocalStream service is not running", null)
                    return
                }

                scope.launch {
                    try {
                        val source = sm.importUri(Uri.parse(uriStr), targetName)
                        result.success(source.toMap())
                    } catch (e: Exception) {
                        result.error("IMPORT_ERROR", "Could not import: ${e.message}", null)
                    }
                }
            }

            "getPendingShares" -> {
                val pending = mutableListOf<String>()
                var uri: Uri?
                while (ShareReceiverActivity.pendingSharedUris.poll().also { uri = it } != null) {
                    pending.add(uri!!.toString())
                }
                result.success(pending)
            }

            "getPinState" -> {
                val required = LocalStreamService.instance?.accessControl?.pinRequired ?: false
                result.success(mapOf("required" to required))
            }

            "setAccessPin" -> {
                val pin = call.argument<String>("pin") ?: ""
                val service = LocalStreamService.instance
                if (service == null) {
                    result.error("SERVICE_UNAVAILABLE", "LocalStream service is not running", null)
                } else {
                    service.accessControl.setPin(pin)
                    result.success(true)
                }
            }

            "clearAccessPin" -> {
                val service = LocalStreamService.instance
                if (service == null) {
                    result.error("SERVICE_UNAVAILABLE", "LocalStream service is not running", null)
                } else {
                    service.accessControl.clearPin()
                    result.success(true)
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun openMediaPicker(result: MethodChannel.Result) {
        if (pendingPickerResult != null) {
            result.error("PICKER_BUSY", "Media picker is already open", null)
            return
        }

        pendingPickerResult = result

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "video/*",
                    "audio/*",
                    "image/*",
                    "application/octet-stream",
                    "application/x-matroska"
                )
            )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        try {
            activity.startActivityForResult(
                Intent.createChooser(intent, "Select media for LocalStream"),
                REQUEST_PICK_MEDIA
            )
        } catch (e: Exception) {
            pendingPickerResult = null
            result.error("PICKER_ERROR", "Could not open media picker: ${e.message}", null)
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK_MEDIA) return false

        val result = pendingPickerResult
        pendingPickerResult = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            result?.success(0)
            return true
        }

        val uris = extractPickerUris(data)
        if (uris.isEmpty()) {
            result?.success(0)
            return true
        }

        for (uri in uris) {
            retainPickerPermission(data, uri)
        }

        val service = LocalStreamService.instance
        if (service != null) {
            for (uri in uris) {
                service.enqueueSharedImport(uri)
            }
        } else {
            for (uri in uris) {
                ShareReceiverActivity.pendingSharedUris.add(uri)
            }
            val startServiceIntent = Intent(context, LocalStreamService::class.java).apply {
                action = LocalStreamService.ACTION_START
            }
            context.startService(startServiceIntent)
        }

        result?.success(uris.size)
        return true
    }

    private fun extractPickerUris(data: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        return uris.distinct()
    }

    private fun retainPickerPermission(data: Intent, uri: Uri) {
        val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (flags == 0) return

        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Provider does not support persisted permission; temporary grant remains
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.eventSink = events

        // Attach listeners to the service now if it already exists...
        LocalStreamService.instance?.let { service ->
            registerServiceListeners(service)
            service.storageManager.currentImportProgress?.let { progress ->
                sendEvent(progress.toMap())
            }
        }

        // ...and ensure events also flow when the service is created AFTER
        // Flutter subscribes (e.g. the first time the user presses Start while
        // the Flutter engine was already listening). Without this the running
        // transition could only reach Flutter through the Dart-side poll.
        // Store the exact hook instance we register so onCancel can remove it.
        serviceCreatedHook = { service ->
            registerServiceListeners(service)
        }
        LocalStreamService.addServiceCreatedHook(serviceCreatedHook)
    }

    override fun onCancel(arguments: Any?) {
        this.eventSink = null
        serviceCreatedHook?.let { LocalStreamService.removeServiceCreatedHook(it) }
        serviceCreatedHook = null
    }

    private fun hasActiveSink(): Boolean = this.eventSink != null

    private fun sendEvent(event: Map<String, Any?>) {
        scope.launch(Dispatchers.Main) {
            try {
                eventSink?.success(event)
            } catch (_: Exception) {
                // Failure to deliver an event to Flutter must not affect the server
            }
        }
    }

    companion object {
        private const val REQUEST_PICK_MEDIA = 4201
    }
}
