package com.localstream.localstream_mobile.sharing

import android.content.Intent
import android.net.Uri
import android.os.Build

object ShareIntentParser {

    /**
     * Extracts all valid media content URIs from an incoming share Intent.
     * Compatible with Android 13 (Tiramisu) through Android 16.
     * Strictly source-agnostic: never checks sender package or app identity.
     */
    fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val uris = mutableListOf<Uri>()

        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Try EXTRA_STREAM first
                    val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }

                    if (streamUri != null) {
                        uris.add(streamUri)
                    } else if (intent.data != null) {
                        uris.add(intent.data!!)
                    }

                    // Also check clip data
                    val clipData = intent.clipData
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i).uri?.let { uris.add(it) }
                        }
                    }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    val streamUris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }

                    if (!streamUris.isNullOrEmpty()) {
                        uris.addAll(streamUris)
                    }

                    val clipData = intent.clipData
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i).uri?.let { uris.add(it) }
                        }
                    }
                }

                Intent.ACTION_VIEW -> {
                    intent.data?.let { uris.add(it) }
                }
            }
        } catch (_: Exception) {
            // Malformed intent is not a crash; caller decides how to handle empty results
        }

        return uris.distinct()
    }
}
