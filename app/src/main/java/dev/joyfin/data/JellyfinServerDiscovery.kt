package dev.joyfin.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.os.Build
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class DiscoveredServer(val id: String, val name: String, val address: String)

@Serializable
private data class DiscoveryReply(
    @SerialName("Id") val id: String,
    @SerialName("Address") val address: String,
    @SerialName("Name") val name: String = "",
)

internal class ServerDiscoveryException(message: String) : IOException(message)

/** Jellyfin's UDP discovery protocol. No authentication, HTTP probing or persistence occurs here. */
internal class JellyfinServerDiscovery(context: Context) {
    private val application = context.applicationContext
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private data class Target(val network: Network, val broadcasts: List<InetAddress>)

    fun discover(): Flow<DiscoveredServer> = channelFlow {
        if (permissionDenied()) throw ServerDiscoveryException("局域网访问未获允许，请在系统应用权限中开启后重试")
        val targets = withContext(Dispatchers.IO) { targets() }
        if (targets.isEmpty()) throw ServerDiscoveryException("请连接 Wi-Fi 或有线局域网后重新查找，也可以手动填写服务器地址")
        val deadline = SystemClock.elapsedRealtime() + 6000
        val outcomes = targets.map { target -> async(Dispatchers.IO) {
            try {
                scan(target, deadline) { trySend(it) }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                Result.failure<Unit>(e)
            }
        } }.awaitAll()
        if (outcomes.none { it.isSuccess }) {
            if (permissionDenied() || outcomes.any { it.exceptionOrNull() is SecurityException })
                throw ServerDiscoveryException("局域网访问未获允许，请在系统应用权限中开启后重试")
            throw ServerDiscoveryException("暂时无法查找局域网服务器，请检查 Wi-Fi 或网线连接后重试")
        }
    }

    private fun permissionDenied() = Build.VERSION.SDK_INT >= 37 &&
        application.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED

    private fun targets(): List<Target> {
        val manager = connectivity ?: return emptyList()
        return manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return@mapNotNull null
            val links = manager.getLinkProperties(network)?.linkAddresses.orEmpty()
                .filter { it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isAnyLocalAddress }
            if (links.isEmpty()) return@mapNotNull null
            val directed = links.mapNotNull addresses@ { link ->
                if (link.prefixLength !in 1..30) return@addresses null
                val bytes = link.address.address
                val address = bytes.fold(0) { result, byte -> (result shl 8) or (byte.toInt() and 255) }
                val broadcast = address or (-1 shl (32 - link.prefixLength)).inv()
                InetAddress.getByAddress(ByteArray(4) { index -> (broadcast ushr (24 - index * 8)).toByte() })
            }
            Target(network, (directed + InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))).distinct())
        }
    }

    private suspend fun scan(target: Target, deadline: Long, found: (DiscoveredServer) -> Unit) = coroutineScope {
        val socket = DatagramSocket(null)
        // Closing on parent cancellation wakes a blocked receive immediately.
        val closer = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { socket.close() }
        }
        try {
            target.network.bindSocket(socket)
            socket.broadcast = true
            socket.bind(InetSocketAddress(0))
            val probe = "who is JellyfinServer?".toByteArray(Charsets.UTF_8)
            val started = SystemClock.elapsedRealtime()
            var probes = 0
            val bytes = ByteArray(8193)
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now >= deadline) break
                if (probes < 3 && now >= started + probes * 1500L) {
                    var sent = false
                    target.broadcasts.forEach { address ->
                        ensureActive()
                        try {
                            socket.send(DatagramPacket(probe, probe.size, address, 7359))
                            sent = true
                        } catch (_: IOException) { ensureActive() }
                    }
                    if (!sent) throw IOException("Discovery broadcast unavailable")
                    probes++
                }
                socket.soTimeout = (deadline - now).coerceIn(1, 250).toInt()
                val packet = DatagramPacket(bytes, bytes.size)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                ensureActive()
                if (packet.length > 8192) continue
                decode(packet)?.let(found)
            }
            ensureActive()
        } finally {
            socket.close()
            closer.cancel()
        }
    }

    private fun decode(packet: DatagramPacket): DiscoveredServer? = runCatching {
        val reply = AppJson.decodeFromString<DiscoveryReply>(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        require(reply.id.isNotBlank() && reply.id.length <= 256 && reply.address.length <= 2048)
        val address = JellyfinApi.normalizeServer(reply.address)
        val name = reply.name.filterNot { it.isISOControl() }.trim().take(120).ifBlank { "Jellyfin 服务器" }
        DiscoveredServer(reply.id, name, address)
    }.getOrNull()
}
