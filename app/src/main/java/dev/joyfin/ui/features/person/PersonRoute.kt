package dev.joyfin.ui.features.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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

@Composable
fun PersonRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    val person = ui.person ?: return
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppHeader(actions, true, horizontalInset = 0.dp, active = "library")
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(104.dp)
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilmTitle(person.name)
                    if (person.type.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MediaPill(person.type)
                        }
                    }
                }
            }
            if (ui.personDetail == null && ui.busy) SkeletonText()
            else {
                val overview = ui.personDetail?.overview?.ifBlank { "暂无人物介绍" } ?: "人物介绍暂不可用"
                Caption(overview, maxLines = 6)
            }
            SectionTitle(if (ui.busy && ui.media.isEmpty()) "参演作品" else "参演作品 · ${ui.total}")
        }
    }
}
