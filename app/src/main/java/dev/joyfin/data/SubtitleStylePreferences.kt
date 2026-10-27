package dev.joyfin.data

import android.content.Context

enum class SubtitleSize(val label: String, val scale: Float, val description: String) {
    STANDARD("标准 (100%)", 1.0f, "系统默认字号，适合手机或常规视距"),
    LARGE("偏大 (120%)", 1.2f, "字号适度放大，观看更轻松"),
    EXTRA_LARGE("超大 (140%)", 1.4f, "大字清晰醒目，适合大屏电视远距离观看"),
    COMPACT("紧凑 (80%)", 0.8f, "字体较小，减少对画面主体的遮挡"),
}

/** Display preference for subtitle presentation shared by this device. */
internal class SubtitleStylePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("playback-display", Context.MODE_PRIVATE)

    var size: SubtitleSize
        get() = SubtitleSize.entries.firstOrNull { it.name == preferences.getString(KEY_SIZE, null) }
            ?: SubtitleSize.STANDARD
        set(value) { preferences.edit().putString(KEY_SIZE, value.name).apply() }

    var hasBackground: Boolean
        get() = preferences.getBoolean(KEY_BACKGROUND, false)
        set(value) { preferences.edit().putBoolean(KEY_BACKGROUND, value).apply() }

    private companion object {
        const val KEY_SIZE = "subtitle-size"
        const val KEY_BACKGROUND = "subtitle-background"
    }
}
