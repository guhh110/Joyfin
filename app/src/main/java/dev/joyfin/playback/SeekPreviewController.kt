package dev.joyfin.playback

import kotlinx.coroutines.*

/** Timeline preview only: never pauses playback and never reports unconfirmed progress. */
enum class SeekInput {
    REMOTE,
    SWIPE,
    TIMELINE,
}

data class SeekPreviewState(
    val baseMs: Long,
    val targetMs: Long,
    val input: SeekInput,
    val active: Boolean = true,
) {
    val deltaMs: Long
        get() = targetMs - baseMs
}

data class SeekEnvironment(val positionMs: Long, val durationMs: Long, val allowed: Boolean)

data class SeekPreviewActions(
    val adjust: (Long) -> Unit,
    val beginSwipe: () -> Unit,
    val swipeOffset: (Long) -> Unit,
    val absolute: (Long) -> Unit,
    val commit: () -> Unit,
    val cancel: () -> Unit,
)

class SeekPreviewController(
    private val scope: CoroutineScope,
    private val environment: () -> SeekEnvironment,
    private val publish: (SeekPreviewState?) -> Unit,
    private val seek: (Long) -> Unit,
) {
    var state: SeekPreviewState? = null
        private set

    private var timer: Job? = null
    val actions =
        SeekPreviewActions(
            ::adjust,
            { begin(SeekInput.SWIPE) },
            ::swipeOffset,
            ::absolute,
            ::commit,
            ::cancel,
        )

    private fun show(value: SeekPreviewState?) {
        state = value
        publish(value)
    }

    private fun begin(input: SeekInput): Boolean {
        val env = environment()
        if (!env.allowed || env.durationMs <= 0) {
            cancel()
            return false
        }
        timer?.cancel()
        val position = env.positionMs.coerceIn(0, env.durationMs)
        show(SeekPreviewState(position, position, input))
        return true
    }

    fun adjust(deltaMs: Long) {
        if (!environment().allowed) {
            cancel()
            return
        }
        if (state?.active != true || state?.input != SeekInput.REMOTE)
            if (!begin(SeekInput.REMOTE)) return
        timer?.cancel()
        val value = requireNotNull(state)
        show(
            value.copy(targetMs = (value.targetMs + deltaMs).coerceIn(0, environment().durationMs)),
        )
        timer = scope.launch {
            delay(1000)
            commit()
        }
    }

    fun swipeOffset(offsetMs: Long) {
        val value = state?.takeIf { it.active && it.input == SeekInput.SWIPE } ?: return
        if (!environment().allowed) {
            cancel()
            return
        }
        show(value.copy(targetMs = (value.baseMs + offsetMs).coerceIn(0, environment().durationMs)))
    }

    fun absolute(targetMs: Long) {
        if (state?.active != true || state?.input != SeekInput.TIMELINE)
            if (!begin(SeekInput.TIMELINE)) return
        if (!environment().allowed) {
            cancel()
            return
        }
        timer?.cancel()
        show(requireNotNull(state).copy(targetMs = targetMs.coerceIn(0, environment().durationMs)))
    }

    fun commit() {
        val value = state?.takeIf { it.active } ?: return
        timer?.cancel()
        if (!environment().allowed) {
            cancel()
            return
        }
        val target = value.targetMs.coerceIn(0, environment().durationMs)
        show(value.copy(targetMs = target, active = false))
        seek(target)
        timer = scope.launch {
            delay(600)
            show(null)
        }
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        show(null)
    }
}
