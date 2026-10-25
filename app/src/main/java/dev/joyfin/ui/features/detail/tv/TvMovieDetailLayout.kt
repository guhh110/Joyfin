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
    val platformScroll = LocalBringIntoViewSpec.current
    val media = ui.item
    val entryFocus = remember(ui.visit) { FocusRequester() }
    var focusedKey by remember(ui.visit) { mutableStateOf("") }
    val playRequester = remember { FocusRequester() }
    val playAgainRequester = remember { FocusRequester() }
    val favRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(ui.visit) {
        withFrameNanos {}
        try { entryFocus.requestFocus() } catch (_: Throwable) {}
    }

    val visibilityScroll = remember(platformScroll, scroll) {
        FirstViewportScrollSpec(platformScroll)
    }

    val rootModifier = modifier
        .fillMaxSize()
        .testTag("movie-detail")
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || scroll.value != 0) false
            else {
                if (focusedKey.isBlank() || focusedKey == "movie:entry") {
                    when (event.key) {
                        Key.DirectionUp -> {
                            try { backRequester.requestFocus() } catch (_: Throwable) {}
                            true
                        }
                        Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                            try { playRequester.requestFocus() } catch (_: Throwable) {}
                            true
                        }
                        else -> false
                    }
                } else false
            }
        }

    CompositionLocalProvider(LocalBringIntoViewSpec provides visibilityScroll) {
        BoxWithConstraints(rootModifier) {
            val height = maxHeight
            val gutter = LocalDeviceLayout.current.gutter
            val posterWidth = (maxWidth * .20f).coerceIn(140.dp, 230.dp)

            Box(
                Modifier
                    .size(1.dp)
                    .focusProperties {
                        down = playRequester
                        right = playRequester
                        left = playRequester
                        up = backRequester
                    }
                    .onFocusChanged { if (it.isFocused) focusedKey = "movie:entry" }
                    .focusTarget("movie:entry")
            )

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
                                Modifier.width(posterWidth).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val width = minOf(maxWidth, maxHeight / 1.5f)
                                if (media == null) {
                                    SkeletonBlock(Modifier.width(width).aspectRatio(2f / 3f))
                                } else {
                                    Artwork(
                                        images.poster(media),
                                        images,
                                        Modifier.width(width).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                                        media.name,
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                            BoxWithConstraints(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val available = maxHeight / LocalDensity.current.fontScale
                                Column(
                                    Modifier.widthIn(max = 640.dp),
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            "返回",
            Glyph.Back,
            actions.back,
            Modifier
                .focusRequester(backRequester)
                .focusProperties { down = playRequester }
                .onFocusChanged { if (it.isFocused) onFocused("header:back") }
                .focusTarget("header:back"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                "字幕管理",
                actions.subtitles,
                Modifier
                    .focusProperties { down = playRequester }
                    .onFocusChanged { if (it.isFocused) onFocused("header:subtitles") }
                    .focusTarget("header:subtitles"),
                icon = Glyph.Subtitle,
                quiet = true,
            )
            if (hasMediaSources) {
                ActionButton(
                    "版本信息",
                    onInformation,
                    Modifier
                        .focusProperties { down = playRequester }
                        .onFocusChanged { if (it.isFocused) onFocused("header:media-info") }
                        .focusTarget("header:media-info"),
                    icon = Glyph.Info,
                    quiet = true,
                )
            }
            IconButton(
                "搜索",
                Glyph.Search,
                actions.search,
                Modifier
                    .focusProperties { down = playRequester }
                    .onFocusChanged { if (it.isFocused) onFocused("header:search") }
                    .focusTarget("header:search"),
            )
            IconButton(
                "账号与设置",
                Glyph.Settings,
                actions.settings,
                Modifier
                    .focusProperties { down = playRequester }
                    .onFocusChanged { if (it.isFocused) onFocused("header:account") }
                    .focusTarget("header:account"),
            )
        }
    }
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

    Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            playLabel,
            { media?.let { actions.play(it, false, null) } },
            Modifier
                .widthIn(min = 180.dp)
                .fillMaxHeight()
                .focusRequester(playRequester)
                .focusProperties {
                    up = backRequester
                    right = if (isResume) playAgainRequester else favRequester
                }
                .onFocusChanged { if (it.isFocused) onFocused("detail:play") }
                .focusTarget("detail:play"),
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
                    .fillMaxHeight()
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
                .fillMaxHeight()
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
