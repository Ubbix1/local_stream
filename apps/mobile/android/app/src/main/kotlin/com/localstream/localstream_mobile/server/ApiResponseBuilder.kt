package com.localstream.localstream_mobile.server

import android.os.Build
import com.localstream.localstream_mobile.storage.FolderNode
import com.localstream.localstream_mobile.storage.MediaSource
import com.localstream.localstream_mobile.storage.SubtitleTrack
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

    fun buildFilesJson(
        sources: List<MediaSource>,
        durationMs: (MediaSource) -> Long? = { null },
        subtitles: (MediaSource) -> List<SubtitleTrack> = { emptyList() }
    ): String {
        val json = JSONObject()
        json.put("items", buildItemsArray(sources, durationMs, subtitles))
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    /**
     * Folder-oriented browsing response: direct child folders + direct items of a folder.
     */
    fun buildFolderViewJson(
        nodes: List<FolderNode>,
        items: List<MediaSource>,
        durationMs: (MediaSource) -> Long? = { null },
        subtitles: (MediaSource) -> List<SubtitleTrack> = { emptyList() }
    ): String {
        val json = JSONObject()
        json.put("folders", buildFoldersArray(nodes))
        json.put("items", buildItemsArray(items, durationMs, subtitles))
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildFoldersJson(nodes: List<FolderNode>): String {
        val json = JSONObject()
        json.put("folders", buildFoldersArray(nodes))
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    private fun buildFoldersArray(nodes: List<FolderNode>): JSONArray {
        val arr = JSONArray()
        for (node in nodes) {
            val folder = JSONObject()
            folder.put("id", node.id)
            folder.put("name", node.name)
            folder.put("parentId", node.parentId ?: JSONObject.NULL)
            folder.put("itemCount", node.itemCount)
            arr.put(folder)
        }
        return arr
    }

    private fun buildItemsArray(
        sources: List<MediaSource>,
        durationMs: (MediaSource) -> Long?,
        subtitles: (MediaSource) -> List<SubtitleTrack>
    ): JSONArray {
        val itemsArray = JSONArray()
        for (source in sources) {
            val item = JSONObject()
            item.put("id", source.id)
            item.put("name", source.displayName)
            item.put("type", source.mediaType)
            item.put("mimeType", source.mimeType)
            item.put("size", source.sizeBytes ?: -1L)
            item.put("available", source.exists())
            item.put("folderId", source.folderId ?: JSONObject.NULL)
            item.put("folderPath", source.folderPath ?: JSONObject.NULL)
            val duration = durationMs(source)
            if (duration != null) item.put("durationMs", duration) else item.put("durationMs", JSONObject.NULL)
            if (source.mediaType == "video" || source.mediaType == "audio" || source.mediaType == "image") {
                item.put("thumbUrl", "/api/v1/thumb/${source.id}")
            }
            val tracks = subtitles(source)
            if (tracks.isNotEmpty()) {
                val subArray = JSONArray()
                for (track in tracks) {
                    val t = JSONObject()
                    t.put("id", track.id)
                    t.put("name", track.name)
                    t.put("mimeType", track.mimeType)
                    subArray.put(t)
                }
                item.put("subtitles", subArray)
            }
            itemsArray.put(item)
        }
        return itemsArray
    }

    fun buildFileDetailJson(
        source: MediaSource,
        durationMs: Long? = null,
        subtitles: List<SubtitleTrack> = emptyList()
    ): String {
        val json = JSONObject()
        json.put("id", source.id)
        json.put("name", source.displayName)
        json.put("type", source.mediaType)
        json.put("mimeType", source.mimeType)
        json.put("size", source.sizeBytes ?: -1L)
        json.put("source", source.sourceKind)
        json.put("available", source.exists())
        json.put("folderId", source.folderId ?: JSONObject.NULL)
        json.put("folderPath", source.folderPath ?: JSONObject.NULL)
        if (durationMs != null) json.put("durationMs", durationMs) else json.put("durationMs", JSONObject.NULL)
        if (source.mediaType == "video" || source.mediaType == "audio" || source.mediaType == "image") {
            json.put("thumbUrl", "/api/v1/thumb/${source.id}")
        }
        if (subtitles.isNotEmpty()) {
            val subArray = JSONArray()
            for (track in subtitles) {
                val t = JSONObject()
                t.put("id", track.id)
                t.put("name", track.name)
                t.put("mimeType", track.mimeType)
                subArray.put(t)
            }
            json.put("subtitles", subArray)
        }
        json.put("streamUrl", "/api/v1/stream/${source.id}")
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildTranscodeStatusJson(sourceId: String): String {
        val json = JSONObject()
        json.put("id", sourceId)
        json.put("supported", false)
        json.put("status", "transcoding_not_configured")
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