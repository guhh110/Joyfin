package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun SubtitleOffsetContent(ui: PlayerUiState, actions: PlayerActions) {
    val subtitle = ui.localSubtitle ?: return
    val seconds = rememberTextFieldState((subtitle.offsetMs / 1000.0).toString())
    var inputError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(subtitle.offsetMs) { seconds.edit { replace(0, length, (subtitle.offsetMs / 1000.0).toString()) } }
    Caption("正值让字幕延后，负值让字幕提前；范围 ±60 秒。")
    Caption("当前偏移：${subtitle.offsetMs / 1000.0} 秒")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("提前 0.5 秒", { actions.subtitleOffset((subtitle.offsetMs - 500).coerceAtLeast(-60_000)) }, enabled = !ui.subtitleApplying)
        ActionButton("延后 0.5 秒", { actions.subtitleOffset((subtitle.offsetMs + 500).coerceAtMost(60_000)) }, enabled = !ui.subtitleApplying)
        ActionButton("重置", { actions.subtitleOffset(0) }, Modifier.focusTarget("player:offset-reset"), enabled = !ui.subtitleApplying)
    }
    EntryTextField("偏移秒数", seconds, "例如 -1.25", enabled = !ui.subtitleApplying, onSubmit = {})
    ActionButton("应用偏移", {
        val value = seconds.text.toString().toDoubleOrNull()
        if (value == null || !value.isFinite() || value !in -60.0..60.0) inputError = "请输入 -60 至 60 秒"
        else { inputError = null; actions.subtitleOffset((value * 1000).toLong()) }
    }, loading = ui.subtitleApplying, enabled = !ui.subtitleApplying)
    inputError?.let { Caption(it) }
    ui.localSubtitleError?.let { Caption(it) }
}
