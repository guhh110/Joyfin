package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.SeriesFocusCoordinator
import dev.joyfin.ui.interaction.seriesFocus
import dev.joyfin.ui.interaction.seriesPersonFocusKey
import dev.joyfin.ui.model.*

@Composable
internal fun SeriesOverviewSections(content: SeriesOverviewContent) {
    val ui = content.ui
    val images = content.images
    val actions = content.actions
    val focus = content.focus
    val previewList = content.previewList
    val peopleList = content.peopleList
    val relatedList = content.relatedList
    val onSeason = content.onSeason
    val onPreview = content.onPreview
    val media = ui.item
    val recommendation = ui.series?.recommendation
    val layout = LocalDeviceLayout.current
    Column(
        Modifier.fillMaxWidth().padding(top = if (layout.television) 24.dp else 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SeriesEpisodePreview(ui, images, actions, focus, previewList, onSeason, onPreview,
            subtitleAction = { SeriesSubtitleAction(content) })
        if (ui.error != null || ui.busy || recommendation?.error != null || recommendation?.loading == true) Row(
            Modifier.padding(horizontal = layout.gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RetryAction(
                "重试剧信息",
                ui.error,
                ui.busy,
                actions.retry,
                Modifier.seriesFocus("detail:retry", focus),
            )
            RetryAction(
                "重试续看",
                recommendation?.error,
                recommendation?.loading == true,
                actions.retrySeriesRecommendation,
                Modifier.seriesFocus("recommendation:retry", focus),
            )
        }
        if (ui.busy && !ui.ready && media?.people.isNullOrEmpty()) PeopleRailSkeleton()
        else
            PeopleRail(media?.people.orEmpty(), images, actions.openPerson, peopleList) { person ->
                Modifier.seriesFocus(seriesPersonFocusKey(person), focus)
            }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.related.isNotEmpty()) {
                MediaRail(
                    "相似作品",
                    ui.related,
                    images,
                    actions.open,
                    focusPrefix = "series:related",
                    state = relatedList,
                ) { item ->
                    Modifier.seriesFocus("series:related:${item.id}", focus)
                }
            } else {
                SectionTitle("相似作品", Modifier.padding(horizontal = layout.gutter))
                if (ui.relatedLoad.loading) RailSkeleton(false)
                else if (ui.relatedLoad.loaded && ui.relatedLoad.error == null)
                    Caption("暂无内容", Modifier.padding(horizontal = layout.gutter))
            }
            RegionLoadFeedback(
                ui.relatedLoad.loading,
                ui.relatedLoad.error,
                null,
                ui.related.isNotEmpty(),
                Modifier.padding(horizontal = layout.gutter),
                reserveSpace = false,
            )
            RetryAction(
                "重试相似作品",
                ui.relatedLoad.error,
                ui.relatedLoad.loading,
                actions.retryRelated,
                Modifier.padding(horizontal = layout.gutter)
                    .seriesFocus("series:related:retry", focus),
            )
        }
    }
}

@Composable
private fun SeriesEpisodePreview(
    ui: DetailUiState,
    images: ArtworkSource,
    actions: BrowseActions,
    focus: SeriesFocusCoordinator,
    list: LazyListState,
    onSeason: () -> Unit,
    onPreview: (Media) -> Unit,
    subtitleAction: @Composable () -> Unit,
) {
    val episodes = ui.series?.episodes
    val seasons = ui.series?.seasons
    // Season discovery precedes the episode request; retain the same skeleton across both.
    val awaitingSeason =
        episodes is SeriesResource.Idle &&
            ui.series.seasonId == null &&
            ui.series.entryError == null &&
            !seasons?.data.isNullOrEmpty()
    val previewLoading =
        episodes?.loading == true ||
            seasons?.loading == true ||
            ui.busy && !ui.ready ||
            awaitingSeason
    val items = episodes?.data.orEmpty()
    val gutter = LocalDeviceLayout.current.gutter
    Column(Modifier.testTag("series:preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header keeps the same composition and focus identity across cold loads and season
        // changes.
        Box(Modifier.padding(horizontal = gutter)) { SeriesPreviewHeading(ui, focus, onSeason, subtitleAction) }
        if (items.isNotEmpty()) {
            MediaRail(
                "剧集预览",
                items,
                images,
                onPreview,
                wide = true,
                focusPrefix = "series:preview",
                state = list,
                showHeading = false,
                preferSeriesTitle = false,
            ) { item ->
                Modifier.seriesFocus("series:preview:${item.id}", focus)
            }
        } else {
            if (previewLoading) RailSkeleton(true, Modifier.testTag("series:preview-skeleton"))
            else if (seasons is SeriesResource.Ready && seasons.error == null && seasons.data.isNullOrEmpty())
                Caption("这部作品还没有季或剧集", Modifier.padding(horizontal = gutter))
            else if (episodes is SeriesResource.Ready && episodes.error == null && seasons?.error == null)
                Caption("本季暂无剧集", Modifier.padding(horizontal = gutter))
        }
        RegionLoadFeedback(
            previewLoading,
            episodes?.error ?: seasons?.error ?: ui.series?.entryError,
            null,
            items.isNotEmpty(),
            Modifier.padding(horizontal = gutter),
            reserveSpace = false,
        )
        RetryAction(
            "重试季信息",
            ui.series?.seasons?.error,
            ui.series?.seasons?.loading == true,
            actions.retrySeriesSeasons,
            Modifier.padding(horizontal = gutter).seriesFocus("series:seasons:retry", focus),
        )
        RetryAction(
            "重试剧集预览",
            episodes?.error,
            episodes?.loading == true,
            actions.retrySeriesSeason,
            Modifier.padding(horizontal = gutter).seriesFocus("series:preview:retry", focus),
        )
    }
}

@Composable
private fun SeriesPreviewHeading(
    ui: DetailUiState,
    focus: SeriesFocusCoordinator,
    onSeason: () -> Unit,
    subtitleAction: @Composable () -> Unit,
) {
    val seasons = ui.series?.seasons
    val selected = seasons?.data?.find { it.id == ui.series.seasonId }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("剧集", Modifier.weight(1f))
        subtitleAction()
        ActionButton(
            selected?.name ?: "选择季",
            onSeason,
            Modifier.widthIn(max = if (LocalDeviceLayout.current.television) 200.dp else 140.dp)
                .seriesFocus("series:season", focus),
            icon = Glyph.Down,
            enabled = !seasons?.data.isNullOrEmpty(),
            quiet = true,
            maxLines = 1,
            loading = seasons?.loading == true && selected == null,
        )
    }
}
