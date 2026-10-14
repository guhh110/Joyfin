package dev.joyfin.ui.features.subtitles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.*

@Composable
internal fun SubtitleChoiceSheet(title: String, options: List<Pair<String, String>>, selected: String?, dismiss: () -> Unit, pick: (String) -> Unit) {
    val initial = options.firstOrNull { it.first == selected }?.first ?: options.firstOrNull()?.first
    RememberSelectionFocus(initial?.let { "subtitle-option:$it" } ?: "sheet:close") {
        Sheet(title, dismiss) {
            if (options.isEmpty()) Caption("没有可选项，请返回重试")
            options.forEach { (id, name) -> ChoiceRow(name, Modifier.focusTarget("subtitle-option:$id"), selected = id == selected) { pick(id) } }
        }
    }
}

