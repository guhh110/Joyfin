package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.data.Media
import dev.joyfin.data.seasonSummary
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun SeasonPicker(
    seasons: List<Media>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = "season-choice:${selectedId?:seasons.firstOrNull()?.id}"
    RememberScreenFocus(initial) {
        Sheet("选择季", onDismiss) {
            if (seasons.isEmpty()) Caption("暂无季信息")
            seasons.forEach { season ->
                ChoiceRow(
                    season.name,
                    subtitle = season.seasonSummary(),
                    selected = season.id == selectedId,
                    modifier = Modifier.focusTarget("season-choice:${season.id}"),
                ) {
                    onSelect(season.id)
                }
            }
        }
    }
}
