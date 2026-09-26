package com.localstream.localstream_mobile.server

import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Server-Sent Events broadcaster. Subscribers register a raw HTTP output
 * stream after the SSE handshake; every event is framed as a named SSE push.
 */
class ServerEvents {

    private val subscribers = ConcurrentHashMap.newKeySet<OutputStream>()

    @Volatile
    var enabled: Boolean = true

    /** Registers a subscriber and returns a token used to unsubscribe with [unregister]. */
    fun register(out: OutputStream): OutputStream {
        subscribers.add(out)
        return out
    }

    fun unregister(out: OutputStream) {
        subscribers.remove(out)
    }

    fun size(): Int = subscribers.size

    /**
     * Broadcasts a named SSE event to every connected subscriber.
     * Frames are tiny; a per-subscriber synchronized write keeps partial frames impossible.
     */
    fun broadcast(event: String, dataJson: String) {
        if (!enabled) return
        val frame = buildString {
            append("event: $event\n")
            append("data: $dataJson\n\n")
        }
        val bytes = frame.toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<OutputStream>()
        for (out in subscribers) {
            try {
                synchronized(out) {
                    out.write(bytes)
                    out.flush()
                }
            } catch (_: Exception) {
                dead.add(out)
            }
        }
        for (out in dead) {
            unregister(out)
        }
    }
}