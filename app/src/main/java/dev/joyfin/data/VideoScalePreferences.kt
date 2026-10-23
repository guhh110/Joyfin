package dev.joyfin.data

import android.content.Context

enum class VideoScaleMode(val label: String, val description: String) {
    FIT("适应屏幕", "保持原始比例，完整显示画面，比例不一致时保留黑边"),
    CROP("铺满裁切", "保持比例铺满屏幕，画面边缘及画面内自带的字幕可能被裁切"),
}

/** Display-only preference shared by this device, independent of server/account/media choices. */
internal class VideoScalePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("playback-display", Context.MODE_PRIVATE)
    var mode: VideoScaleMode
        get() = VideoScaleMode.entries.firstOrNull { it.name == preferences.getString(KEY, null) }
            ?: VideoScaleMode.FIT
        set(value) { preferences.edit().putString(KEY, value.name).apply() }

    private companion object { const val KEY = "scale-mode" }
}
