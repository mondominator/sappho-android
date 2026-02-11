package com.sappho.audiobooks.cast.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class SsdpDevice(
    val location: String,   // URL from LOCATION header
    val usn: String,        // Unique Service Name
    val host: String,       // Extracted IP
    val port: Int,          // Extracted port
    val server: String?,    // SERVER header value
    val friendlyName: String? = null
)

/**
 * SSDP (Simple Service Discovery Protocol) discovery for finding devices
 * on the local network. Used by Roku (roku:ecp) and Kodi (UPnP MediaRenderer).
 */
class SsdpDiscovery {

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val BUFFER_SIZE = 4096
    }

    /**
     * Send M-SEARCH multicast and collect responses as a Flow.
     *
     * @param searchTarget The SSDP search target (e.g., "roku:ecp" or "urn:schemas-upnp-org:device:MediaRenderer:1")
     * @param timeoutMs How long to listen for responses
     */
    fun discover(searchTarget: String, timeoutMs: Long = 3000): Flow<SsdpDevice> = flow {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs.toInt()
                broadcast = true
            }

            val searchMessage = buildMSearchMessage(searchTarget)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            val packet = DatagramPacket(
                searchMessage.toByteArray(),
                searchMessage.length,
                address,
                SSDP_PORT
            )

            // Send M-SEARCH
            socket.send(packet)
            Log.d(TAG, "Sent M-SEARCH for '$searchTarget'")

            // Collect responses until timeout
            val seen = mutableSetOf<String>()
            val buffer = ByteArray(BUFFER_SIZE)
            val responsePacket = DatagramPacket(buffer, buffer.size)

            while (true) {
                try {
                    socket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length)
                    val device = parseResponse(response)
                    if (device != null && seen.add(device.usn)) {
                        Log.d(TAG, "Discovered: ${device.host}:${device.port} (${device.server})")
                        emit(device)
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery error", e)
        } finally {
            socket?.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildMSearchMessage(searchTarget: String): String {
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: $searchTarget\r\n" +
                "\r\n"
    }

    private fun parseResponse(response: String): SsdpDevice? {
        val headers = mutableMapOf<String, String>()
        response.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().uppercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        val location = headers["LOCATION"] ?: return null
        val usn = headers["USN"] ?: location

        // Extract host and port from LOCATION URL
        val hostPort = extractHostPort(location) ?: return null

        return SsdpDevice(
            location = location,
            usn = usn,
            host = hostPort.first,
            port = hostPort.second,
            server = headers["SERVER"]
        )
    }

    private fun extractHostPort(url: String): Pair<String, Int>? {
        return try {
            val java_url = java.net.URL(url)
            val port = if (java_url.port == -1) java_url.defaultPort else java_url.port
            Pair(java_url.host, port)
        } catch (e: Exception) {
            null
        }
    }
}
