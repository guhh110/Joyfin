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
    val remoteStepMs: Long? = null,
    val requiresConfirmation: Boolean = false,
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
    val remoteDown: (Int) -> Unit = { adjust(it * 10_000L) },
    val remoteUp: (Int) -> Unit = {},
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
    private var remoteDirection = 0
    val actions =
        SeekPreviewActions(
            ::adjust,
            { begin(SeekInput.SWIPE) },
            ::swipeOffset,
            ::absolute,
            ::commit,
            ::cancel,
            ::remoteDown,
            ::remoteUp,
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
        remoteDirection = 0
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
        remoteDirection = 0
        val value = requireNotNull(state)
        show(
            value.copy(targetMs = (value.targetMs + deltaMs).coerceIn(0, environment().durationMs),
                remoteStepMs = null, requiresConfirmation = false),
        )
        scheduleCommit()
    }

    /** Own the repeat clock so different remotes' native repeat rates behave identically. */
    fun remoteDown(direction: Int) {
        if (direction != -1 && direction != 1) return
        val env = environment()
        if (!env.allowed || env.durationMs <= 0) {
            cancel()
            return
        }
        if (remoteDirection == direction) return
        if (state?.active != true || state?.input != SeekInput.REMOTE)
            if (!begin(SeekInput.REMOTE)) return
        timer?.cancel()
        remoteDirection = direction
        fun step(stepMs: Long, repeated: Boolean) {
            val value = requireNotNull(state)
            show(value.copy(
                targetMs = (value.targetMs + direction * stepMs).coerceIn(0, environment().durationMs),
                remoteStepMs = stepMs,
                requiresConfirmation = value.requiresConfirmation || repeated,
            ))
        }
        step(10_000, false)
        timer = scope.launch {
            var heldMs = 500L
            delay(heldMs)
            while (isActive && remoteDirection == direction) {
                val current = environment()
                if (!current.allowed || current.durationMs <= 0) {
                    this@SeekPreviewController.cancel()
                    return@launch
                }
                step(when {
                    heldMs >= 3000 -> 60_000
                    heldMs >= 1500 -> 30_000
                    else -> 10_000
                }, true)
                delay(250)
                heldMs += 250
            }
        }
    }

    fun remoteUp(direction: Int) {
        // A late key-up from the previous direction must not stop a reversed hold.
        if (remoteDirection != direction || direction == 0) return
        timer?.cancel()
        remoteDirection = 0
        val value = state?.takeIf { it.active && it.input == SeekInput.REMOTE } ?: return
        show(value.copy(remoteStepMs = null))
        if (!value.requiresConfirmation) scheduleCommit()
    }

    private fun scheduleCommit() {
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
        remoteDirection = 0
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
        remoteDirection = 0
        show(value.copy(targetMs = target, active = false, remoteStepMs = null))
        seek(target)
        timer = scope.launch {
            delay(600)
            show(null)
        }
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        remoteDirection = 0
        show(null)
    }
}
