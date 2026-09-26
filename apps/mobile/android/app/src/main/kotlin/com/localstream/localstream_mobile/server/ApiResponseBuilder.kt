package com.localstream.localstream_mobile.server

import android.os.Build
import com.localstream.localstream_mobile.storage.MediaSource
import org.json.JSONArray
import org.json.JSONObject

object ApiResponseBuilder {

    const val PROTOCOL_VERSION = "1"
    const val SERVER_NAME = "LocalStream"
    const val SERVER_VERSION = "0.1.0"

    fun buildInfoJson(port: Int): String {
        val json = JSONObject()
        json.put("name", SERVER_NAME)
        json.put("version", SERVER_VERSION)
        json.put("deviceName", Build.MODEL ?: "Android Device")
        json.put("serverVersion", SERVER_VERSION)
        json.put("port", port)
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildStatusJson(status: ServerStatus): String {
        val json = JSONObject()
        json.put("state", status.state.name.lowercase())
        json.put("port", status.port)
        json.put("activeClients", status.activeClients)
        json.put("uptimeMs", status.uptimeMs)
        json.put("activeStreams", status.activeStreams)
        json.put("bytesTransferred", status.bytesTransferred)
        json.put("protocolVersion", PROTOCOL_VERSION)
        val addrArray = JSONArray()
        for (addr in status.addresses) {
            addrArray.put(addr)
        }
        json.put("addresses", addrArray)
        return json.toString()
    }

    fun buildFilesJson(sources: List<MediaSource>): String {
        val json = JSONObject()
        val itemsArray = JSONArray()
        for (source in sources) {
            val item = JSONObject()
            item.put("id", source.id)
            item.put("name", source.displayName)
            item.put("type", source.mediaType)
            item.put("mimeType", source.mimeType)
            item.put("size", source.sizeBytes ?: -1L)
            item.put("available", source.exists())
            itemsArray.put(item)
        }
        json.put("items", itemsArray)
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildFileDetailJson(source: MediaSource): String {
        val json = JSONObject()
        json.put("id", source.id)
        json.put("name", source.displayName)
        json.put("type", source.mediaType)
        json.put("mimeType", source.mimeType)
        json.put("size", source.sizeBytes ?: -1L)
        json.put("source", source.sourceKind)
        json.put("available", source.exists())
        json.put("streamUrl", "/api/v1/stream/${source.id}")
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildErrorJson(code: String, message: String): String {
        val root = JSONObject()
        val errorObj = JSONObject()
        errorObj.put("code", code)
        errorObj.put("message", message)
        root.put("error", errorObj)
        root.put("protocolVersion", PROTOCOL_VERSION)
        return root.toString()
    }

    /**
     * Browser client entry point (GET / and GET /index.html).
     * The page loads the library itself via /api/v1/files so it is always fresh.
     */
    fun buildWebIndexHtml(): String = WebClientHtml.render()

    /**
     * Browser player entry point (GET /watch/:id).
     * The shell deep-links directly into the embedded player for that item.
     */
    fun buildWebPlayerHtml(source: MediaSource): String = WebClientHtml.render(source.id)
}