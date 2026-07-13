package com.sonicwave.remote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers SonicWave on the local network using Android NSD (mDNS/DNS-SD).
 *
 * Looks for `_sonicwave._tcp.` services. On finding one, resolves it to get the
 * host IP and port, then calls [onFound].
 *
 * NSD on Android can be flaky — all callbacks are wrapped in try-catch.
 */
@Singleton
class NsdDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var foundCallback: ((host: String, port: Int) -> Unit)? = null

    private val SERVICE_TYPE = "_sonicwave._tcp."

    /**
     * Starts NSD discovery for SonicWave services.
     * [onFound] is called when a device is resolved (may be called multiple times).
     */
    fun startDiscovery(onFound: (host: String, port: Int) -> Unit) {
        stopDiscovery()
        foundCallback = onFound

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    nsdManager.resolveService(serviceInfo, buildResolveListener())
                } catch (_: Exception) { }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) { }
    }

    /** Stops active NSD discovery. Safe to call even if discovery was never started. */
    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) { }
    }

    /**
     * Skips NSD and returns a manually-entered host/port pair.
     * Also stops any active discovery.
     */
    fun setManualHost(ip: String, port: Int = 8082): Pair<String, Int> {
        stopDiscovery()
        return ip to port
    }

    private fun buildResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                try {
                    val host = serviceInfo.host?.hostAddress ?: return
                    // Skip IPv6 addresses — OkHttp needs plain IPv4 for simple http:// URLs
                    if (host.contains(":")) return
                    val port = serviceInfo.port
                    foundCallback?.invoke(host, port)
                } catch (_: Exception) { }
            }
        }
    }
}
