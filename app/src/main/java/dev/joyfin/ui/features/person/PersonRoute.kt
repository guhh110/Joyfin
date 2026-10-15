package dev.joyfin.ui.features.person

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
fun PersonRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    val person = ui.person ?: return
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppHeader(actions, true, horizontalInset = 0.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Artwork(
                    images.person(person),
                    images,
                    Modifier.size(104.dp).clip(CircleShape),
                    person.name.take(1),
                )
                Column(Modifier.weight(1f)) {
                    FilmTitle(person.name)
                    Caption(person.type)
                }
            }
            if (ui.personDetail == null && ui.busy) SkeletonText()
            else Caption(ui.personDetail?.overview?.ifBlank { "暂无人物介绍" } ?: "人物介绍暂不可用")
            SectionTitle(if (ui.busy && ui.media.isEmpty()) "参演作品" else "参演作品 · ${ui.total}")
        }
    }
}
