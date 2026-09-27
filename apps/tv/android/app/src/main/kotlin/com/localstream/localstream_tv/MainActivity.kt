package com.localstream.localstream_tv

import com.localstream.localstream_tv.discovery.DiscoveryService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val METHOD_CHANNEL = "localstream.tv/discovery"
        private const val EVENT_CHANNEL = "localstream.tv/discovery/events"
    }

    private var discovery: DiscoveryService? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                // Browsing is driven by the EventChannel stream below; the
                // method is kept for clients that only want to restart a scan.
                "startDiscovery" -> {
                    discovery?.restart()
                    result.success(null)
                }
                "stopDiscovery" -> {
                    discovery?.stop()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(messenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                private var sink: EventChannel.EventSink? = null

                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    sink = events
                    discovery = DiscoveryService(applicationContext).also { service ->
                        service.start(
                            DiscoveryService.Listener { host, port, name ->
                                sink?.success(
                                    mapOf(
                                        "host" to host,
                                        "port" to port,
                                        "name" to name
                                    )
                                )
                            }
                        )
                    }
                }

                override fun onCancel(arguments: Any?) {
                    sink = null
                    discovery?.stop()
                    discovery = null
                }
            }
        )
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        super.onDestroy()
    }
}