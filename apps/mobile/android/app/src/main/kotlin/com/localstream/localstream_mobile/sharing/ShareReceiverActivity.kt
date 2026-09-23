package com.localstream.localstream_mobile.sharing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.localstream.localstream_mobile.MainActivity
import com.localstream.localstream_mobile.server.LocalStreamService
import com.localstream.localstream_mobile.storage.StorageManager
import java.util.concurrent.ConcurrentLinkedQueue

class ShareReceiverActivity : Activity() {

    companion object {
        // Pending shared items queue for Flutter bridge consumption if service wasn't running
        // Thread-safe, non-blocking queue for multiple concurrent share intents
        val pendingSharedUris = ConcurrentLinkedQueue<Uri>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            retainReadPermission(intent)
            val uris = ShareIntentParser.extractUris(intent)
            if (uris.isEmpty()) {
                Toast.makeText(this, "No readable media in share", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            var addedCount = 0
            val service = LocalStreamService.instance
            if (service != null) {
                for (uri in uris) {
                    try {
                        service.enqueueSharedImport(uri)
                        addedCount++
                    } catch (_: Exception) {
                        // One bad shared URI must not block the rest
                    }
                }
            } else {
                // Service not running yet: start service and queue URIs
                for (uri in uris) {
                    pendingSharedUris.add(uri)
                }
                val startServiceIntent = Intent(this, LocalStreamService::class.java).apply {
                    action = LocalStreamService.ACTION_START
                }
                startService(startServiceIntent)
                addedCount = uris.size
            }

            Toast.makeText(
                this,
                "Loading $addedCount item${if (addedCount == 1) "" else "s"} into LocalStream",
                Toast.LENGTH_SHORT
            ).show()

            // Bring LocalStream main app to front
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("from_share", true)
            }
            startActivity(launchIntent)

        } catch (_: Exception) {
            Toast.makeText(this, "Could not add shared media", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    private fun retainReadPermission(shareIntent: Intent) {
        val grantedFlags = shareIntent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (grantedFlags == 0) return

        val uris = ShareIntentParser.extractUris(shareIntent)
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(uri, grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Provider does not support persisted permission; temporary grant remains
            }
        }
    }
}
