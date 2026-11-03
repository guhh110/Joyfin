package dev.joyfin.session

import dev.joyfin.data.DiscoveredServer
import dev.joyfin.data.ServerDiscoveryException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

enum class DiscoveryPhase { IDLE, SEARCHING, COMPLETE, STOPPED, FAILED }

data class ServerDiscoveryState(
    val phase: DiscoveryPhase = DiscoveryPhase.IDLE,
    val servers: List<DiscoveredServer> = emptyList(),
    val error: String? = null,
) {
    val searching get() = phase == DiscoveryPhase.SEARCHING
}

/** Results stay in memory; ownership prevents cancelled or departed screens accepting late replies. */
internal class ServerDiscoveryController(
    private val scope: CoroutineScope,
    private val discover: () -> Flow<DiscoveredServer>,
    private val available: () -> Boolean,
    private val publish: (ServerDiscoveryState) -> Unit,
) {
    private var generation = 0L
    private var job: Job? = null
    private var state = ServerDiscoveryState()
    private fun update(value: ServerDiscoveryState) { state = value; if (available()) publish(value) }

    fun start() {
        if (!available() || state.searching) return
        val owner = ++generation
        job?.cancel()
        update(state.copy(phase = DiscoveryPhase.SEARCHING, error = null))
        job = scope.launch {
            val seen = mutableSetOf<String>()
            try {
                discover().collect { server ->
                    ensureActive()
                    if (owner != generation || !available()) return@collect
                    if (server.id in seen || seen.size >= 32) return@collect
                    seen += server.id
                    val index = state.servers.indexOfFirst { it.id == server.id || it.address == server.address }
                    if (index >= 0) {
                        if (state.servers[index] != server)
                            update(state.copy(servers = state.servers.mapIndexed { i, row -> if (i == index) server else row }))
                    } else if (state.servers.size < 64)
                        update(state.copy(servers = state.servers + server))
                }
                ensureActive()
                if (owner == generation) update(state.copy(phase = DiscoveryPhase.COMPLETE,
                    servers = state.servers.filter { it.id in seen }.take(32)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (owner == generation) update(state.copy(phase = DiscoveryPhase.FAILED, error = when (e) {
                    is ServerDiscoveryException -> e.message
                    is SecurityException -> "局域网访问未获允许，请在系统应用权限中开启后重试"
                    else -> "查找暂时失败，请检查局域网连接后重试，也可以手动填写地址"
                }))
            }
        }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        if (state.searching) update(state.copy(phase = DiscoveryPhase.STOPPED))
    }

    fun clear() {
        stop()
        update(ServerDiscoveryState())
    }
}
