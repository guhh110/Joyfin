@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback.output

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.joyfin.data.VideoScaleMode

/** Resizes only presentation, retaining the video surface and the subtitle renderer instances. */
internal class PlayerVideoViewport(private val view: PlayerView) {
    private var mode = VideoScaleMode.FIT
    private val subtitles = view.subtitleView
    private val originalSubtitleLayout = (subtitles?.layoutParams as? FrameLayout.LayoutParams)
        ?.let { FrameLayout.LayoutParams(it) }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateSubtitleViewport()
    }

    init { view.addOnLayoutChangeListener(layoutListener) }

    fun apply(mode: VideoScaleMode) {
        this.mode = mode
        view.resizeMode = when (mode) {
            VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoScaleMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        updateSubtitleViewport()
    }

    private fun updateSubtitleViewport() {
        val subtitles = subtitles ?: return
        val original = originalSubtitleLayout ?: return
        val current = subtitles.layoutParams as? FrameLayout.LayoutParams ?: return
        val crop = mode == VideoScaleMode.CROP && view.width > 0 && view.height > 0
        // Media3 puts subtitles inside its resizable content frame. Keep them centered in the
        // visible window when that frame extends beyond it, without detaching the ASS renderer.
        val width = if (crop) view.width else original.width
        val height = if (crop) view.height else original.height
        val gravity = if (crop) Gravity.CENTER else original.gravity
        if (current.width == width && current.height == height && current.gravity == gravity) return
        subtitles.layoutParams = FrameLayout.LayoutParams(original).apply {
            this.width = width
            this.height = height
            this.gravity = gravity
        }
    }

    fun release() { view.removeOnLayoutChangeListener(layoutListener) }
}
