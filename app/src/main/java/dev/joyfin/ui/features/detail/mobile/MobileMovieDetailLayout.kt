package dev.joyfin.ui.features.detail.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette

/**
 * Phone Movie Detail layout matching the boundless cinema standard of MobileSeriesDetailLayout.
 */
@Composable
internal fun MobileMovieDetailLayout(
    ui: DetailUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    modifier: Modifier = Modifier,
    onInformation: () -> Unit,
) {
    val scroll = rememberScrollState()
    val media = ui.item

    BoxWithConstraints(modifier.fillMaxSize()) {
        val artworkHeight = (maxWidth * .85f).coerceIn(240.dp, 420.dp)
        val favoriteWidth = 112.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)

        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("detail:scroll")) {
            HeroBackdrop(
                media,
                images,
                0.dp,
                Modifier.testTag("detail:hero"),
                artworkHeight = artworkHeight,
            ) {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MobileMovieHeader(actions, onInformation, hasMediaSources = media?.sources?.isNotEmpty() == true)
                    Spacer(Modifier.height(artworkHeight * .48f))
                    MobileMovieIntroduction(ui, images)
                    if (ui.error != null) {
                        RegionLoadFeedback(
                            loading = ui.busy,
                            error = ui.error,
                            onRetry = actions.retry,
                            hasContent = ui.ready,
                            reserveSpace = false,
                        )
                    }
                    val isResume = (media?.resumeTicks ?: 0L) > 0L
                    val playLabel = if (isResume) "继续播放" else "播放电影"
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionButton(
                            playLabel,
                            { media?.let { actions.play(it, false, null) } },
                            Modifier.weight(1f).fillMaxHeight(),
                            icon = Glyph.Play,
                            primary = true,
                            enabled = media != null && ui.ready,
                            loading = ui.busy,
                            maxLines = 1,
                        )
                        ActionButton(
                            if (media?.favorite == true) "已收藏" else "收藏",
                            actions.favorite,
                            Modifier.width(favoriteWidth).fillMaxHeight(),
                            icon = if (media?.favorite == true) Glyph.Check else Glyph.Heart,
                            selected = media?.favorite == true,
                            enabled = ui.ready && media != null,
                            loading = ui.favoriteBusy,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (ui.busy && !ui.ready && media?.people.isNullOrEmpty()) {
                    PeopleRailSkeleton()
                } else {
                    PeopleRail(media?.people.orEmpty(), images, actions.openPerson)
                }
                if (ui.related.isNotEmpty()) {
                    MediaRail("相似作品", ui.related, images, actions.open)
                }
                if (ui.relatedLoad.error != null || ui.relatedLoad.loading) {
                    RegionLoadFeedback(
                        loading = ui.relatedLoad.loading,
                        error = ui.relatedLoad.error,
                        onRetry = actions.retryRelated,
                        hasContent = ui.related.isNotEmpty(),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        reserveSpace = false,
                    )
                }
            }
        }
        if (scroll.value > 0) {
            Box(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Palette.Background)
            )
        }
    }
}

@Composable
private fun MobileMovieHeader(
    actions: BrowseActions,
    onInformation: () -> Unit,
    hasMediaSources: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton("返回", Glyph.Back, actions.back)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("字幕管理", Glyph.Subtitle, actions.subtitles)
            if (hasMediaSources) {
                IconButton("版本信息", Glyph.Info, onInformation)
            }
            IconButton("搜索", Glyph.Search, actions.search)
            IconButton("账号与设置", Glyph.Settings, actions.settings)
        }
    }
}

@Composable
private fun MobileMovieIntroduction(ui: DetailUiState, images: ArtworkSource) {
    val media = ui.item
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MediaPill("电影", icon = Glyph.Film)
        }
        if (media?.name.isNullOrBlank()) {
            SkeletonBlock(Modifier.fillMaxWidth(.7f).height(40.dp))
        } else {
            MediaTitle(media, images, maxLines = Int.MAX_VALUE)
        }
        if (media != null) {
            if (!hasMediaLogo(media, images) && media.originalTitle.isNotBlank() && media.originalTitle != media.name) {
                Caption(media.originalTitle)
            }
            MediaMetadata(media)
        }
        if (ui.busy && media?.overview.isNullOrBlank()) {
            SkeletonText(lines = 3)
        } else {
            Caption(media?.overview.orEmpty().ifBlank { "暂无剧情简介" })
        }
    }
}
