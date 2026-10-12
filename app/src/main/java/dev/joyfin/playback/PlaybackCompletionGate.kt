package dev.joyfin.playback

/** A rebuild or a stale ENDED callback must never advance to the next episode. */
class PlaybackCompletionGate {
    enum class Result {
        Ignore,
        Completed,
        Premature,
    }

    private var generation = 0L
    private var ready = false
    private var consumed = false

    fun begin(): Long {
        generation++
        ready = false
        consumed = false
        return generation
    }

    fun ready(token: Long?) {
        if (token == generation) ready = true
    }

    fun ended(token: Long?, positionMs: Long, durationMs: Long, hasMediaTrack: Boolean): Result {
        if (token != generation || !ready || consumed || !hasMediaTrack || durationMs <= 0)
            return Result.Ignore
        consumed = true
        return if (positionMs >= (durationMs - 5000).coerceAtLeast(0)) Result.Completed
        else Result.Premature
    }
}
