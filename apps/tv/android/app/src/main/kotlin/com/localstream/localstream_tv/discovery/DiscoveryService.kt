package com.localstream.localstream_tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers LocalStream servers that advertise `_http._tcp.` on the local
 * network. The server publishes no TXT records and uses a dynamic port, so
 * every candidate still has to be verified over HTTP by the caller
 * (GET /api/v1/info, filtered on `name == "LocalStream"`).
 */
class DiscoveryService(private val context: Context) {

    /** Invoked on the main thread for every newly discovered service. */
    fun interface Listener {
        fun onServiceFound(host: String, port: Int, name: String)
    }

    companion object {
        private const val TAG = "LocalStreamTvDiscovery"
        private const val SERVICE_TYPE = "_http._tcp."
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: Listener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val resolving = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun start(listener: Listener) {
        stopLocked()
        val manager = nsdManager ?: return
        this.listener = listener
        seen.clear()
        resolving.clear()

        val dl = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "mDNS discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                val host = serviceInfo.host
                if (host != null && isUsable(host)) {
                    emitIfNew(name, host.hostAddress, serviceInfo.port)
                    return
                }
                // Some stacks report the host only after resolution.
                if (resolving.add(name)) {
                    resolve(manager, serviceInfo, name)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "mDNS discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(TAG, "mDNS start failed (errorCode=$errorCode)")
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(TAG, "mDNS stop failed (errorCode=$errorCode)")
            }
        }
        discoveryListener = dl
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, dl)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS discovery failed", e)
            discoveryListener = null
        }
    }

    /** Restarts browsing with the current listener (used by "Scan again"). */
    @Synchronized
    fun restart() {
        listener?.let { start(it) }
    }

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        val dl = discoveryListener ?: return
        discoveryListener = null
        listener = null
        val manager = nsdManager ?: return
        try {
            manager.stopServiceDiscovery(dl)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS stop error", e)
        }
    }

    private fun resolve(manager: NsdManager, info: NsdServiceInfo, name: String) {
        try {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    resolving.remove(name)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    resolving.remove(name)
                    val host = serviceInfo.host ?: return
                    if (!isUsable(host)) return
                    emitIfNew(
                        serviceInfo.serviceName ?: name,
                        host.hostAddress,
                        serviceInfo.port
                    )
                }
            })
        } catch (e: Exception) {
            resolving.remove(name)
            Log.w(TAG, "mDNS resolve error for $name", e)
        }
    }

    private fun emitIfNew(name: String, hostAddress: String, port: Int) {
        val key = "$name|$hostAddress|$port"
        if (!seen.add(key)) return
        mainHandler.post { listener?.onServiceFound(hostAddress, port, name) }
    }

    private fun isUsable(host: InetAddress): Boolean {
        if (host !is Inet4Address) return false
        val addr = host.hostAddress ?: return false
        return !addr.startsWith("169.254.") &&
            addr != "0.0.0.0" &&
            addr != "255.255.255.255"
    }
}