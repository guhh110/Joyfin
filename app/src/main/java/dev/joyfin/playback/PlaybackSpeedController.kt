package dev.joyfin.playback

data class SpeedBoostActions(
    val begin: () -> Long? = { null },
    val end: (Long) -> Unit = {},
)

/** A hold owns a temporary override, never the user's selected speed. */
internal class PlaybackSpeedController(
    initialSpeed: Float,
    private val apply: (speed: Float, boosting: Boolean) -> Unit,
) {
    var selectedSpeed = initialSpeed
        private set
    private var sequence = 0L
    private var hold: Long? = null
    val boosting: Boolean get() = hold != null

    fun select(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        hold = null
        selectedSpeed = speed
        apply(speed, false)
    }

    fun begin(): Long? {
        if (hold != null) return null
        val token = ++sequence
        hold = token
        apply(maxOf(2f, selectedSpeed), true)
        return token
    }

    fun end(token: Long) {
        if (hold == token) cancel()
    }

    fun cancel() {
        if (hold == null) return
        hold = null
        apply(selectedSpeed, false)
    }
}
