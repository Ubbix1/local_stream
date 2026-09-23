package com.localstream.localstream_mobile.server

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe enumeration of server lifecycle states.
 */
enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR;

    fun isActive(): Boolean = this == RUNNING || this == STARTING
}

/**
 * Snapshot of all server runtime metrics.
 * Immutable — replaced atomically, never mutated in place.
 */
data class ServerStatus(
    val state: ServerState = ServerState.STOPPED,
    val port: Int = 0,
    val addresses: List<String> = emptyList(),
    val activeClients: Int = 0,
    val activeStreams: Int = 0,
    val bytesTransferred: Long = 0L,
    val uptimeMs: Long = 0L,
    val errorMessage: String? = null,
    val startedAtMs: Long = 0L
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "state" to state.name.lowercase(),
        "port" to port,
        "addresses" to addresses,
        "activeClients" to activeClients,
        "activeStreams" to activeStreams,
        "bytesTransferred" to bytesTransferred,
        "uptimeMs" to uptimeMs,
        "errorMessage" to errorMessage
    )
}

/**
 * Atomic holder for [ServerStatus]. All reads/writes are lock-free.
 * Listeners are notified on state change via [onStateChanged].
 */
class ServerStateHolder {
    private val _status = AtomicReference(ServerStatus())
    private val _clientCount = AtomicInteger(0)
    private val _streamCount = AtomicInteger(0)
    private val _bytesTotal = AtomicLong(0L)

    @Volatile
    var onStateChanged: ((ServerStatus) -> Unit)? = null

    val status: ServerStatus get() = _status.get()

    fun transition(newState: ServerState, errorMessage: String? = null) {
        val current = _status.get()
        val startedAt = if (newState == ServerState.RUNNING) System.currentTimeMillis()
        else current.startedAtMs

        val next = current.copy(
            state = newState,
            errorMessage = if (newState == ServerState.ERROR) errorMessage else null,
            startedAtMs = startedAt
        )
        _status.set(next)
        onStateChanged?.invoke(next)
    }

    fun updatePort(port: Int) {
        _status.updateAndGet { it.copy(port = port) }
    }

    fun updateAddresses(addresses: List<String>) {
        _status.updateAndGet { it.copy(addresses = addresses) }.also {
            onStateChanged?.invoke(it)
        }
    }

    fun incrementClients(): Int = _clientCount.incrementAndGet().also { n ->
        _status.updateAndGet { it.copy(activeClients = n) }
    }

    fun decrementClients(): Int = _clientCount.updateAndGet { maxOf(0, it - 1) }.also { n ->
        _status.updateAndGet { it.copy(activeClients = n) }
    }

    fun incrementStreams(): Int = _streamCount.incrementAndGet().also { n ->
        _status.updateAndGet { it.copy(activeStreams = n) }
    }

    fun decrementStreams(): Int = _streamCount.updateAndGet { maxOf(0, it - 1) }.also { n ->
        _status.updateAndGet { it.copy(activeStreams = n) }
    }

    fun addBytesTransferred(bytes: Long) {
        val total = _bytesTotal.addAndGet(bytes)
        _status.updateAndGet { it.copy(bytesTransferred = total) }
    }

    fun buildSnapshot(): ServerStatus {
        val s = _status.get()
        val uptime = if (s.state == ServerState.RUNNING && s.startedAtMs > 0)
            System.currentTimeMillis() - s.startedAtMs
        else 0L
        return s.copy(uptimeMs = uptime)
    }

    private fun AtomicReference<ServerStatus>.updateAndGet(transform: (ServerStatus) -> ServerStatus): ServerStatus {
        while (true) {
            val current = get()
            val next = transform(current)
            if (compareAndSet(current, next)) return next
        }
    }
}
