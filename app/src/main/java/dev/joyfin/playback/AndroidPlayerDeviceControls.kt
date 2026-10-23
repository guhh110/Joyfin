package dev.joyfin.playback

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

/** Brightness belongs to this playback window; volume follows the system media stream. */
class AndroidPlayerDeviceControls(private val activity: Activity) : PlayerDeviceControls {
    private val audio = activity.getSystemService(AudioManager::class.java)

    override fun read(control: PlayerDeviceControl): PlayerDeviceLevel =
        when (control) {
            PlayerDeviceControl.BRIGHTNESS -> {
                val windowLevel = activity.window.attributes.screenBrightness
                val brightness =
                    if (windowLevel >= 0f) windowLevel
                    else Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                PlayerDeviceLevel(control, brightness.coerceIn(MIN_BRIGHTNESS, 1f))
            }
            PlayerDeviceControl.VOLUME -> readVolume()
        }

    override fun set(control: PlayerDeviceControl, fraction: Float): PlayerDeviceLevel {
        if (!fraction.isFinite()) return read(control)
        return when (control) {
            PlayerDeviceControl.BRIGHTNESS -> {
                activity.window.attributes = activity.window.attributes.apply {
                    screenBrightness = fraction.coerceIn(MIN_BRIGHTNESS, 1f)
                }
                read(control)
            }
            PlayerDeviceControl.VOLUME -> {
                val current = readVolume()
                val manager = audio
                if (current.unavailableReason != null || manager == null) current
                else try {
                    val min = manager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target = (min + fraction.coerceIn(0f, 1f) * (max - min)).roundToInt()
                    if (manager.getStreamVolume(AudioManager.STREAM_MUSIC) != target)
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    readVolume()
                } catch (_: SecurityException) {
                    current.copy(unavailableReason = "当前无法调整媒体音量")
                }
            }
        }
    }

    private fun readVolume(): PlayerDeviceLevel {
        val manager = audio
            ?: return PlayerDeviceLevel(PlayerDeviceControl.VOLUME, 0f, "当前无法读取媒体音量")
        return try {
            val min = manager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
            PlayerDeviceLevel(
                PlayerDeviceControl.VOLUME,
                if (max > min) ((current - min).toFloat() / (max - min)).coerceIn(0f, 1f) else 0f,
                if (manager.isVolumeFixed || max <= min) "音量由系统或外接设备控制" else null,
            )
        } catch (_: SecurityException) {
            PlayerDeviceLevel(PlayerDeviceControl.VOLUME, 0f, "当前无法读取媒体音量")
        }
    }

    private companion object {
        // Keep the screen and its unlock control visible even at the bottom of a swipe.
        const val MIN_BRIGHTNESS = .01f
    }
}
