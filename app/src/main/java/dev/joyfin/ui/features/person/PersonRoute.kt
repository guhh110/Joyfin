package dev.joyfin.ui.features.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.interaction.focusTarget

@Composable
fun PersonRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    val person = ui.person ?: return
    var information by remember(person.id) { mutableStateOf(false) }
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppHeader(actions, true, horizontalInset = 0.dp, active = "library")
            PageHeader(person.name, leading = {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Palette.Elevated, CircleShape)
                        .border(1.dp, Palette.BorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Artwork(
                        images.person(person),
                        images,
                        Modifier.fillMaxSize(),
                        person.name.take(1),
                    )
                }
            }) {
                Caption(if (ui.busy && ui.media.isEmpty()) "参演作品" else "参演作品 · ${ui.total}", maxLines = 1)
                IconButton("人物介绍", Glyph.Info, { information = true }, Modifier.focusTarget("person:information"))
            }
        }
    }
    if (information) Sheet(person.name, { information = false }, initialFocus = "sheet:close") {
        if (person.type.isNotBlank()) MediaPill(person.type)
        if (ui.personDetail == null && ui.busy) SkeletonText()
        else Caption(ui.personDetail?.overview?.ifBlank { "暂无人物介绍" } ?: "人物介绍暂不可用")
    }
}
