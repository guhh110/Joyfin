package dev.joyfin.app

import dev.joyfin.data.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** One foreground request; navigation cancels it and saves only its safe retry command. */
internal class PageRequests(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<UiState>,
    private val authenticated: () -> Boolean,
    private val expired: () -> Unit,
) {
    private var job: Job? = null
    private var generation = 0L
    var retry: (() -> Unit)? = null

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    fun run(retryAction: () -> Unit, action: suspend () -> Unit) {
        cancel()
        val token = generation
        retry = retryAction
        update(busy = true, error = null)
        job =
            scope.launch {
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    if (token != generation) return@launch
                    if (
                        e is ApiException &&
                            e.status == 401 &&
                            authenticated() &&
                            state.value.page != Page.LOGIN
                    )
                        expired()
                    else update(busy = true, error = e.message ?: "请求失败，请检查网络")
                } finally {
                    if (isActive && token == generation)
                        update(busy = false, error = currentError())
                }
            }
    }

    private fun currentError() = state.value.activeRequest.error

    private fun update(busy: Boolean, error: String?) {
        state.value = state.value.withActiveRequest(busy, error)
    }
}
