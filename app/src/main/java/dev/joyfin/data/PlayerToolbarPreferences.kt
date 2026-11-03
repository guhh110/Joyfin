package dev.joyfin.data

import android.content.Context
import android.content.res.Configuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class PlayerToolbarAction(val label: String) {
    SUBTITLE("字幕"),
    SPEED("倍速"),
    EPISODES("选集"),
    QUALITY("画质"),
    AUDIO("音轨"),
    NEXT("下一集"),
    CHAPTERS("章节"),
    VERSIONS("媒体版本"),
    VIDEO_SCALE("画面缩放"),
    MARK_INTRO("片头结束"),
    MARK_OUTRO("片尾开始"),
    UNDO_MARK("撤销标记"),
}

@Serializable
data class PlayerToolbarPreferences(
    val pinned: List<PlayerToolbarAction> = listOf(
        PlayerToolbarAction.SUBTITLE, PlayerToolbarAction.SPEED, PlayerToolbarAction.EPISODES,
    ),
) {
    fun validated() = copy(pinned = pinned.distinct().take(MAX_PINNED))
    fun toggle(action: PlayerToolbarAction): PlayerToolbarPreferences = when {
        action in pinned -> copy(pinned = pinned - action)
        pinned.size < MAX_PINNED -> copy(pinned = pinned + action)
        else -> this
    }
    fun move(action: PlayerToolbarAction, delta: Int): PlayerToolbarPreferences {
        val order = pinned.toMutableList()
        val from = order.indexOf(action)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, order.lastIndex)
        order.add(to, order.removeAt(from))
        return copy(pinned = order)
    }
    fun moreActions() = PlayerToolbarAction.entries.filterNot { it in pinned }

    companion object { const val MAX_PINNED = 4 }
}

/** Toolbar layout is local to this device and saved separately for mobile and television. */
internal class PlayerToolbarPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("player-toolbar", Context.MODE_PRIVATE)
    private val key = if ((context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION) "tv" else "mobile"

    fun load(): PlayerToolbarPreferences = runCatching {
        AppJson.decodeFromString<PlayerToolbarPreferences>(prefs.getString(key, "{}") ?: "{}").validated()
    }.getOrDefault(PlayerToolbarPreferences())

    fun save(value: PlayerToolbarPreferences) {
        prefs.edit().putString(key, AppJson.encodeToString(value.validated())).apply()
    }
}
