@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback.quality

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.media3.exoplayer.audio.AudioCapabilities
import dev.joyfin.playback.AudioPlaybackCapabilities

/** Observes device capabilities, not media. Device IDs are internal change keys, never report data. */
internal class PlaybackDeviceMonitor(private val context: Context, private val onChange: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val displays = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private var running = false
    private var route: AudioDeviceInfo? = null
    private data class Signature(val displayId: Int?, val display: DisplayPlaybackCapabilities,
        val routeId: Int?, val audio: AudioCapabilities)
    private var signature: Signature? = null
    var revision = 0L
        private set
    val currentRoute: AudioDeviceInfo? get() = route?.takeIf { device ->
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.id == device.id } == true
    }
    private val audioListener = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = changed()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = changed()
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = changed()
        override fun onDisplayRemoved(displayId: Int) = changed()
        override fun onDisplayChanged(displayId: Int) = changed()
    }

    @Suppress("DEPRECATION")
    private fun display(): Display? = if (Build.VERSION.SDK_INT >= 30)
        runCatching { context.display }.getOrNull() ?: displays?.getDisplay(Display.DEFAULT_DISPLAY)
    else (context as? Activity)?.windowManager?.defaultDisplay ?: displays?.getDisplay(Display.DEFAULT_DISPLAY)

    fun displayCapabilities(): DisplayPlaybackCapabilities {
        val screen = display() ?: return DisplayPlaybackCapabilities()
        val types = runCatching { screen.hdrCapabilities?.supportedHdrTypes }.getOrNull()
            ?: return DisplayPlaybackCapabilities()
        return DisplayPlaybackCapabilities(true, types.asSequence().mapNotNull {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> VideoRange.HDR10
                Display.HdrCapabilities.HDR_TYPE_HLG -> VideoRange.HLG
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> VideoRange.HDR10_PLUS
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> VideoRange.DOLBY_VISION
                else -> null
            }
        }.toSet())
    }
    private fun capture() = Signature(display()?.displayId, displayCapabilities(), currentRoute?.id,
        AudioCapabilities.getCapabilities(context, AudioPlaybackCapabilities.attributes, currentRoute, emptyList()))

    fun routed(device: AudioDeviceInfo?) {
        if (!running || device == null || route?.id == device.id) return
        val first = currentRoute == null
        route = device
        val fresh = capture()
        val old = signature
        if (first && old != null && fresh.display == old.display && fresh.audio == old.audio) signature = fresh
        else changed()
    }
    private fun changed() {
        if (!running) return
        val fresh = capture()
        if (fresh == signature) return
        signature = fresh
        revision++
        onChange()
    }
    fun refresh() = changed()
    fun start() {
        if (running) return
        running = true
        val before = signature
        signature = capture()
        audio?.registerAudioDeviceCallback(audioListener, main)
        displays?.registerDisplayListener(displayListener, main)
        if (before != null && before != signature) { revision++; onChange() }
    }
    fun stop() {
        if (!running) return
        running = false
        audio?.unregisterAudioDeviceCallback(audioListener)
        displays?.unregisterDisplayListener(displayListener)
    }
}
