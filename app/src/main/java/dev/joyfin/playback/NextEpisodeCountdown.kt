package dev.joyfin.playback

import kotlin.math.ceil

/** Display time comes from the media clock. Only a genuine ENDED event advances playback. */
internal object NextEpisodeCountdown {
    fun seconds(positionMs: Long, durationMs: Long, speed: Float): Int {
        if (durationMs <= 10_000 || positionMs < 0 || positionMs >= durationMs || !speed.isFinite() || speed <= 0) return 0
        val remaining = (durationMs - positionMs).toDouble() / speed / 1000
        return if (remaining <= 5) ceil(remaining).toInt().coerceAtLeast(1) else 0
    }
}
