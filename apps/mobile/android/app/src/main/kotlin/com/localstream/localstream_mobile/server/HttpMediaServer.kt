package com.localstream.localstream_mobile.server

import com.localstream.localstream_mobile.storage.MediaMetadataCache
import com.localstream.localstream_mobile.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class HttpMediaServer(
    private val storageManager: StorageManager,
    private val stateHolder: ServerStateHolder,
    private val serverEvents: ServerEvents,
    private val accessControl: AccessControl,
    private val metadataCache: MediaMetadataCache,
    private val maxConnections: Int = 32
) {
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val serverJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serverJob)
    private val connectionSemaphore = Semaphore(maxConnections)

    var boundPort: Int = 0
        private set

    /**
     * Starts listening on 0.0.0.0 (all network interfaces).
     * @param preferredPort The port to bind (default 8080). If 0 or occupied, picks available.
     * @return The actual port bound.
     */
    @Synchronized
    fun start(preferredPort: Int = 8080): Int {
        if (isRunning.get()) {
            return boundPort
        }

        var port = if (preferredPort in 1024..65535) preferredPort else 8080
        var socket: ServerSocket? = null

        // Try preferred port first, then fallback to system-assigned port (0)
        try {
            socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
        } catch (e: Exception) {
            try {
                socket?.close()
                socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", 0))
            } catch (fallbackEx: Exception) {
                throw fallbackEx
            }
        }

        serverSocket = socket
        boundPort = socket.localPort
        isRunning.set(true)
        stateHolder.updatePort(boundPort)
        stateHolder.transition(ServerState.RUNNING)

        // Main accept loop
        scope.launch {
            while (isRunning.get()) {
                try {
                    val clientSocket = socket.accept()
                    // Dispatch each client to coroutine pool bounded by semaphore
                    scope.launch {
                        connectionSemaphore.acquire()
                        try {
                            val handler = HttpRequestHandler(
                                socket = clientSocket,
                                storageManager = storageManager,
                                stateHolder = stateHolder,
                                port = boundPort,
                                serverEvents = serverEvents,
                                accessControl = accessControl,
                                metadataCache = metadataCache
                            )
                            handler.handle()
                        } finally {
                            connectionSemaphore.release()
                        }
                    }
                } catch (e: SocketException) {
                    if (!isRunning.get()) break
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }

        return boundPort
    }

    /**
     * Gracefully stops the HTTP server, closes the socket, and cancels worker coroutines.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }

        stateHolder.transition(ServerState.STOPPING)

        try {
            serverSocket?.close()
        } catch (_: Exception) {
            // Socket already closed; continuing shutdown is safe
        }
        serverSocket = null

        // Cancel running connection jobs
        serverJob.cancelChildren()

        stateHolder.updatePort(0)
        stateHolder.transition(ServerState.STOPPED)
    }

    fun isAlive(): Boolean = isRunning.get() && serverSocket?.isClosed == false
}
