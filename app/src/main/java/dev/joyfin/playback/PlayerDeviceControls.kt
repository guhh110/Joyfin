package dev.joyfin.playback

enum class PlayerDeviceControl { BRIGHTNESS, VOLUME }

data class PlayerDeviceLevel(
    val control: PlayerDeviceControl,
    val fraction: Float,
    val unavailableReason: String? = null,
)

/** Window/device operations are supplied by the activity, never performed by player UI. */
interface PlayerDeviceControls {
    fun read(control: PlayerDeviceControl): PlayerDeviceLevel
    fun set(control: PlayerDeviceControl, fraction: Float): PlayerDeviceLevel
}
