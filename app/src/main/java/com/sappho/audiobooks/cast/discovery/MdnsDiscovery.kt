package com.sappho.audiobooks.cast.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class MdnsDevice(
    val name: String,
    val host: String,
    val port: Int,
    val type: String
)

/**
 * mDNS/Bonjour discovery using Android's NsdManager.
 * Used for AirPlay device discovery (_airplay._tcp).
 */
class MdnsDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "MdnsDiscovery"
    }

    /**
     * Discover mDNS services of the given type.
     *
     * @param serviceType The mDNS service type (e.g., "_airplay._tcp.")
     */
    fun discover(serviceType: String): Flow<MdnsDevice> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: errorCode=$errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val device = MdnsDevice(
                    name = serviceInfo.serviceName,
                    host = host,
                    port = serviceInfo.port,
                    type = serviceType
                )
                Log.d(TAG, "Resolved: ${device.name} at ${device.host}:${device.port}")
                trySend(device)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: errorCode=$errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: errorCode=$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                try {
                    nsdManager.resolveService(serviceInfo, resolveListener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving service", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mDNS discovery", e)
            close(e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping mDNS discovery", e)
            }
        }
    }
}
