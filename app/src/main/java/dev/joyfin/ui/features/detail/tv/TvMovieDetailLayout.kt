package dev.joyfin.ui.features.detail.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.model.*

@Composable
internal fun TvMovieDetailLayout(
    ui: DetailUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    onInformation: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item("hero") {
            MovieHero(
                ui.item,
                images,
                actions,
                ready = ui.ready,
                favoriteBusy = ui.favoriteBusy,
                height = 360.dp,
                topPadding = 130.dp,
                gutter = 48.dp,
                titleSize = 54.sp,
                titleLineHeight = 65.sp,
            )
        }
        movieDetailSections(ui, actions, images, 48.dp, true, onInformation)
    }
}
