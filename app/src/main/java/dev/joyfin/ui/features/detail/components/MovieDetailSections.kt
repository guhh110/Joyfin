package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.model.*

internal fun LazyListScope.movieDetailSections(
    ui: DetailUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    gutter: Dp,
    reserveRetrySlot: Boolean,
    onInformation: () -> Unit,
) {
    val media = ui.item
    item("information") {
        Box(Modifier.padding(horizontal = gutter)) {
            Column {
                RegionLoadFeedback(
                    ui.busy,
                    ui.error,
                    actions.retry,
                    ui.ready,
                    stableRetrySlot = reserveRetrySlot,
                )
                MovieIntroduction(media, !ui.ready, onInformation, loading = ui.busy) {
                    actions.play(it, false, null)
                }
            }
        }
    }
    item("cast") {
        if (!ui.ready && ui.busy && media?.people.isNullOrEmpty()) PeopleRailSkeleton()
        else PeopleRail(media?.people.orEmpty(), images, actions.openPerson)
    }
    item("related") {
        if (ui.ready)
            LoadingMediaRail(
                "相似内容",
                ui.related,
                ui.relatedLoad,
                images,
                actions,
                retry = actions.retryRelated,
            )
    }
}
