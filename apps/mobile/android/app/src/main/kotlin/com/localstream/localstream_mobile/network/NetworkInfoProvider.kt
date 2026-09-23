package com.localstream.localstream_mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkInfoProvider(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var onNetworkChanged: ((List<String>) -> Unit)? = null

    /**
     * Enumerates non-loopback, active IPv4 addresses (Wi-Fi, Mobile Hotspot, Ethernet).
     */
    fun getLocalIpAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                // Prefer wlan (Wi-Fi/hotspot) or eth (Ethernet) interfaces
                val name = intf.name.lowercase()
                val isPreferred = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap") || name.startsWith("swlan")

                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        // Exclude link-local address (169.254.x.x)
                        if (!host.startsWith("169.254.")) {
                            if (isPreferred) {
                                result.add(0, host) // Put Wi-Fi/hotspot first
                            } else {
                                result.add(host)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to whatever addresses were collected
        }
        return result.distinct()
    }

    /**
     * Starts listening for network connectivity changes.
     */
    fun startMonitoring() {
        if (networkCallback != null || connectivityManager == null) return

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val addrs = getLocalIpAddresses()
                    onNetworkChanged?.invoke(addrs)
                }

                override fun onLost(network: Network) {
                    val addrs = getLocalIpAddresses()
                    onNetworkChanged?.invoke(addrs)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val addrs = getLocalIpAddresses()
                    onNetworkChanged?.invoke(addrs)
                }
            }

            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (_: Exception) {
            // Network monitoring is optional; HTTP server still works without it
        }
    }

    /**
     * Stops monitoring network changes.
     */
    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {
                // Unregister failure is non-fatal
            }
            networkCallback = null
        }
    }
}
