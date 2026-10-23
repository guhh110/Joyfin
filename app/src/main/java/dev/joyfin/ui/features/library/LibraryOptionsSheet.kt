package dev.joyfin.ui.features.library

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget

internal enum class LibraryOptionsPanel { SORT, WATCHED }

@Composable
internal fun LibraryOptionsSheet(panel: LibraryOptionsPanel, value: LibraryBrowseOptions,
    change: (LibraryBrowseOptions) -> Unit, dismiss: () -> Unit) {
    val initial = remember {
        if (panel == LibraryOptionsPanel.SORT) "library:sort:${value.sort}" else "library:watched:${value.watched}"
    }
    RememberSelectionFocus(initial) {
        Sheet(if (panel == LibraryOptionsPanel.SORT) "作品排序" else "观看状态", dismiss,
            side = LocalDeviceLayout.current.television) {
            Caption("选择后立即生效，并为当前账号的这个媒体库保存。")
            if (panel == LibraryOptionsPanel.SORT) {
                LibrarySort.entries.forEach { sort ->
                    ChoiceRow(sort.label, Modifier.focusTarget("library:sort:$sort"),
                        selected = value.sort == sort) { change(value.selectSort(sort)) }
                }
                ChoiceRow("降序", Modifier.focusTarget("library:order:descending"),
                    subtitle = "新到旧、高到低或名称倒序", selected = value.descending) {
                    change(value.copy(descending = true))
                }
                ChoiceRow("升序", Modifier.focusTarget("library:order:ascending"),
                    subtitle = "旧到新、低到高或名称正序", selected = !value.descending) {
                    change(value.copy(descending = false))
                }
            } else LibraryWatched.entries.forEach { watched ->
                ChoiceRow(watched.label, Modifier.focusTarget("library:watched:$watched"),
                    selected = value.watched == watched) { change(value.copy(watched = watched)) }
            }
        }
    }
}
