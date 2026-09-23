package com.localstream.localstream_mobile

import android.content.Intent
import com.localstream.localstream_mobile.bridge.LocalStreamMethodChannel
import com.localstream.localstream_mobile.server.LocalStreamService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private var methodChannelHandler: LocalStreamMethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val handler = LocalStreamMethodChannel(this, applicationContext, flutterEngine.dartExecutor.binaryMessenger)
        methodChannelHandler = handler

        LocalStreamService.instance?.let { service ->
            handler.registerServiceListeners(service)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (methodChannelHandler?.handleActivityResult(requestCode, resultCode, data) == true) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
