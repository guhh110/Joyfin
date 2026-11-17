package dev.joyfin.ui.features.detail.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.model.FirstViewportScrollSpec

/**
 * TV Movie Detail layout: full-viewport introductory screen matching the series experience,
 * followed by Cast and Related rails.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvMovieDetailLayout(
    ui: DetailUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    modifier: Modifier = Modifier,
    onInformation: () -> Unit,
) {
    val scroll = rememberScrollState()
    val media = ui.item
    var focusedKey by remember(ui.visit) { mutableStateOf("") }
    val playRequester = remember { FocusRequester() }
    val playAgainRequester = remember { FocusRequester() }
    val favRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }

    val platformScroll = LocalBringIntoViewSpec.current
    val visibilityScroll = remember(platformScroll, scroll, focusedKey) {
        FirstViewportScrollSpec(platformScroll) {
            scroll.value == 0 && (focusedKey.startsWith("header:") || focusedKey in setOf(
                "movie:entry",
                "header:back",
                "header:subtitles",
                "header:media-info",
                "header:search",
                "header:account",
                "detail:play",
                "detail:play-again",
                "favorite",
            ))
        }
    }

    val rootModifier = modifier.fillMaxSize().testTag("movie-detail")

    CompositionLocalProvider(LocalBringIntoViewSpec provides visibilityScroll) {
        BoxWithConstraints(rootModifier) {
            val height = maxHeight
            val gutter = LocalDeviceLayout.current.gutter

            Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("detail:scroll")) {
                HeroBackdrop(
                    media,
                    images,
                    0.dp,
                    Modifier.height(height).testTag("detail:hero"),
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = 16.dp)) {
                        TvMovieHeader(
                            actions = actions,
                            onInformation = onInformation,
                            hasMediaSources = media?.sources?.isNotEmpty() == true,
                            backRequester = backRequester,
                            playRequester = playRequester,
                            onFocused = { focusedKey = it },
                        )
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth(.48f).fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val available = maxHeight / LocalDensity.current.fontScale
                                Column(
                                    Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TvMovieIntroduction(ui, images, available)
                                    if (ui.error != null) {
                                        RegionLoadFeedback(
                                            loading = ui.busy,
                                            error = ui.error,
                                            onRetry = actions.retry,
                                            hasContent = ui.ready,
                                            reserveSpace = false,
                                        )
                                    }
                                    TvMovieActions(
                                        ui = ui,
                                        actions = actions,
                                        onInformation = onInformation,
                                        playRequester = playRequester,
                                        playAgainRequester = playAgainRequester,
                                        favRequester = favRequester,
                                        backRequester = backRequester,
                                        onFocused = { focusedKey = it },
                                    )
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 36.dp),
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
                                modifier = Modifier.padding(horizontal = gutter),
                                reserveSpace = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMovieHeader(
    actions: BrowseActions,
    onInformation: () -> Unit,
    hasMediaSources: Boolean,
    backRequester: FocusRequester,
    playRequester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    CinemaHeader("电影详情", actions, tools = {
            IconButton("本片字幕", Glyph.Subtitle, actions.subtitles,
                Modifier.focusProperties { down = playRequester }.focusTarget("detail:subtitles"))
            if (hasMediaSources) {
                IconButton("版本信息", Glyph.Info, onInformation,
                    Modifier.focusProperties { down = playRequester }.focusTarget("header:media-info"))
            }
        }, focusModifier = { modifier, key ->
            modifier.then(if (key == "header:back") Modifier.focusRequester(backRequester) else Modifier)
                .focusProperties { down = playRequester }
                .onFocusChanged { if (it.isFocused) onFocused(key) }.focusTarget(key)
        })
}

@Composable
private fun TvMovieIntroduction(ui: DetailUiState, images: ArtworkSource, available: Dp) {
    val media = ui.item
    val compact = available < 330.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MediaPill("电影", icon = Glyph.Film)
    }
    if (media?.name.isNullOrBlank()) {
        SkeletonBlock(Modifier.fillMaxWidth(.7f).height(if (compact) 40.dp else 56.dp))
    } else {
        MediaTitle(media, images, large = !compact, maxLines = if (compact) 1 else 2,
            logoWidth = 420.dp, logoHeight = if (compact) 40.dp else 88.dp)
    }
    if (media != null) {
        if (!compact && !hasMediaLogo(media, images) && media.originalTitle.isNotBlank() && media.originalTitle != media.name) {
            Caption(media.originalTitle, maxLines = 1)
        }
        if (available > 220.dp) {
            MediaMetadata(media)
        }
    }
    if (available > 260.dp) {
        if (ui.busy && media?.overview.isNullOrBlank()) {
            SkeletonText(lines = if (compact) 2 else 3)
        } else {
            Caption(media?.overview.orEmpty().ifBlank { "暂无剧情简介" }, maxLines = if (compact) 2 else 3)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvMovieActions(
    ui: DetailUiState,
    actions: BrowseActions,
    onInformation: () -> Unit,
    playRequester: FocusRequester,
    playAgainRequester: FocusRequester,
    favRequester: FocusRequester,
    backRequester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    val media = ui.item
    val isResume = (media?.resumeTicks ?: 0L) > 0L
    val playLabel = if (isResume) "继续播放" else "播放电影"

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            playLabel,
            { media?.let { actions.play(it, false, null) } },
            Modifier
                .widthIn(min = 144.dp)
                .focusRequester(playRequester)
                .focusProperties {
                    up = backRequester
                    right = if (isResume) playAgainRequester else favRequester
                }
                .onFocusChanged { if (it.isFocused) onFocused("detail:play") }
                .focusTarget("detail:play", enabled = ui.ready),
            icon = Glyph.Play,
            primary = true,
            enabled = media != null && ui.ready,
            loading = ui.busy,
            maxLines = 1,
        )
        if (isResume) {
            ActionButton(
                "从头播放",
                { media?.let { actions.play(it, true, null) } },
                Modifier
                        .focusRequester(playAgainRequester)
                    .focusProperties {
                        up = backRequester
                        left = playRequester
                        right = favRequester
                    }
                    .onFocusChanged { if (it.isFocused) onFocused("detail:play-again") }
                    .focusTarget("detail:play-again"),
                icon = Glyph.Refresh,
                quiet = false,
                enabled = media != null && ui.ready,
                maxLines = 1,
            )
        }
        ActionButton(
            if (media?.favorite == true) "已收藏" else "收藏",
            actions.favorite,
            Modifier
                .focusRequester(favRequester)
                .focusProperties {
                    up = backRequester
                    left = if (isResume) playAgainRequester else playRequester
                }
                .onFocusChanged { if (it.isFocused) onFocused("favorite") }
                .focusTarget("favorite"),
            icon = if (media?.favorite == true) Glyph.Check else Glyph.Heart,
            selected = media?.favorite == true,
            enabled = ui.ready && media != null,
            loading = ui.favoriteBusy,
            maxLines = 1,
        )
    }
}
