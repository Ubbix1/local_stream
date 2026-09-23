package com.localstream.localstream_mobile.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class MdnsService(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    @Volatile
    private var isRegistered = false
    private var registrationListener: NsdManager.RegistrationListener? = null

    /**
     * Registers the LocalStream HTTP service on the local network via mDNS/NSD.
     * Non-blocking and failure-isolated: NSD failure never terminates the HTTP server.
     */
    @Synchronized
    fun register(port: Int, serviceName: String = "LocalStream") {
        if (nsdManager == null) {
            return
        }

        if (isRegistered) {
            unregister()
        }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_http._tcp."
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredService: NsdServiceInfo) {
                isRegistered = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                isRegistered = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
            }
        }

        registrationListener = listener

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            isRegistered = false
        }
    }

    /**
     * Unregisters the advertised mDNS service cleanly.
     */
    @Synchronized
    fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null

        if (nsdManager != null && isRegistered) {
            try {
                nsdManager.unregisterService(listener)
            } catch (_: IllegalArgumentException) {
                // Known Android NSD quirk if listener was not currently registered
            } catch (_: Exception) {
                // Unregister failure is non-fatal
            } finally {
                isRegistered = false
            }
        }
    }
}
