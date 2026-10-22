package dev.joyfin.playback

enum class PlaybackLoadReason(val label: String) {
    INITIAL("首次进入"), USER_CHANGE("用户切换播放配置"), RETRY("手动重试"),
    FALLBACK("播放失败后自动回退"), DEVICE("显示或音频能力变化"),
    AUDIO_MODE("音频输出设置变化"), SPEED("倍速路径变化"), VIDEO_FORMAT("识别到需要转换的 HDR／DV 格式"),
    RESUME("从后台恢复"), REPLAY("重新播放"),
}

enum class PlaybackBufferKind(val label: String) {
    STARTUP("首次加载"), SEEK("跳转缓冲"), REBUFFER("播放中补缓冲"),
    PAUSED("暂停时加载"), SUBTITLE("字幕配置更新"),
}

data class PlaybackAttemptTiming(val elapsedMs: Long, val positionMs: Long,
    val reason: PlaybackLoadReason, val firstFrameMs: Long? = null)
data class PlaybackBufferTiming(val elapsedMs: Long, val positionMs: Long, val durationMs: Long,
    val kind: PlaybackBufferKind, val finished: Boolean = false, val canceled: Boolean = false)
data class PlaybackTimingSnapshot(val firstFrameMs: Long? = null, val attemptCount: Int = 0,
    val attempts: List<PlaybackAttemptTiming> = emptyList(), val buffers: List<PlaybackBufferTiming> = emptyList())

internal enum class TimingPlayerState { BUFFERING, READY, STOPPED }

/** Monotonic, deterministic accounting only. It never changes buffering or playback policy. */
internal class PlaybackTimingTracker(private val originMs: Long) {
    private var generation = -1L
    private var attemptStartMs = originMs
    private var attemptCount = 0
    private var firstFrameMs: Long? = null
    private var ready = false
    private var operation: PlaybackBufferKind? = null
    private var attempts = emptyList<PlaybackAttemptTiming>()
    private var completed = emptyList<PlaybackBufferTiming>()
    private var pending: PlaybackBufferTiming? = null

    fun attempt(owner: Long, nowMs: Long, positionMs: Long, reason: PlaybackLoadReason) {
        finish(nowMs, canceled = true)
        generation = owner
        attemptStartMs = nowMs
        attemptCount++
        ready = false
        operation = null
        attempts = (attempts + PlaybackAttemptTiming(elapsed(nowMs), positionMs.coerceAtLeast(0), reason)).takeLast(10)
    }

    fun firstFrame(owner: Long, nowMs: Long) {
        if (owner != generation || attempts.isEmpty() || attempts.last().firstFrameMs != null) return
        if (firstFrameMs == null) firstFrameMs = elapsed(nowMs)
        attempts = attempts.dropLast(1) + attempts.last().copy(firstFrameMs = (nowMs - attemptStartMs).coerceAtLeast(0))
    }

    fun operation(owner: Long, kind: PlaybackBufferKind) {
        if (owner != generation) return
        operation = kind
        // A discontinuity callback may follow STATE_BUFFERING in the same event batch.
        pending?.takeIf { it.kind != PlaybackBufferKind.STARTUP }?.let { pending = it.copy(kind = kind) }
    }

    fun state(owner: Long, nowMs: Long, state: TimingPlayerState, positionMs: Long, playWhenReady: Boolean) {
        if (owner != generation) return
        when (state) {
            TimingPlayerState.BUFFERING -> if (pending == null) {
                val kind = operation ?: when {
                    !ready -> PlaybackBufferKind.STARTUP
                    playWhenReady -> PlaybackBufferKind.REBUFFER
                    else -> PlaybackBufferKind.PAUSED
                }
                pending = PlaybackBufferTiming(elapsed(nowMs), positionMs.coerceAtLeast(0), 0, kind)
            }
            TimingPlayerState.READY -> { finish(nowMs, canceled = false); ready = true; operation = null }
            // Local subtitle preparation may pass through IDLE before preparing the same item.
            TimingPlayerState.STOPPED -> finish(nowMs, canceled = true)
        }
    }

    fun stop(nowMs: Long) { finish(nowMs, canceled = true); generation = -1L; operation = null }

    fun snapshot(nowMs: Long) = PlaybackTimingSnapshot(firstFrameMs, attemptCount, attempts,
        (completed + listOfNotNull(pending?.let { it.copy(durationMs = (elapsed(nowMs) - it.elapsedMs).coerceAtLeast(0)) })).takeLast(20))

    private fun elapsed(nowMs: Long) = (nowMs - originMs).coerceAtLeast(0)
    private fun finish(nowMs: Long, canceled: Boolean) {
        pending?.let {
            completed = (completed + it.copy(durationMs = (elapsed(nowMs) - it.elapsedMs).coerceAtLeast(0),
                finished = true, canceled = canceled)).takeLast(20)
        }
        pending = null
    }
}

fun PlaybackTimingSnapshot.reportLines(): List<String> = buildList {
    add("首次出画：${firstFrameMs?.let(::diagnosticElapsed) ?: "尚未出画"}；加载次数：$attemptCount")
    attempts.forEachIndexed { index, attempt ->
        add("+${diagnosticElapsed(attempt.elapsedMs)} 加载 ${attemptCount - attempts.size + index + 1} · ${attempt.reason.label} · 位置 ${diagnosticTime(attempt.positionMs)} · 本次出画 ${attempt.firstFrameMs?.let(::diagnosticElapsed) ?: "尚未出画"}")
    }
    buffers.forEach {
        add("+${diagnosticElapsed(it.elapsedMs)} ${it.kind.label} · 位置 ${diagnosticTime(it.positionMs)} · ${diagnosticElapsed(it.durationMs)} · ${if (!it.finished) "进行中" else if (it.canceled) "中断" else "完成"}")
    }
    add("缓冲记录为状态停留时长（含期间暂停）；最近保留 10 次加载和 20 次缓冲，不据此推断网速或服务器故障。")
}
