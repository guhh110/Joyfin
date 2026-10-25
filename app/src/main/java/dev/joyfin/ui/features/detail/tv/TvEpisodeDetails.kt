package dev.joyfin.ui.features.detail.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.joyfin.browse.*
import dev.joyfin.data.Media
import dev.joyfin.data.fileSizeLabel
import dev.joyfin.data.runtimeLabel
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

/** A bounded TV information card: no scroll containers, including the version view. */
@Composable
fun TvEpisodeDetails(
    episode: Media,
    details: SeriesResource<Media>,
    images: ArtworkSource,
    mediaInfo: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Media, Boolean, String?) -> Unit,
    onMediaInfo: () -> Unit,
    onDetails: () -> Unit,
    feedback: String?,
    playLabel: (Media, String) -> String,
    canPlay: (Media, String) -> Boolean,
    isPreparing: (Media, String) -> Boolean = { _, _ -> false },
) {
    var version by rememberSaveable(episode.id) { mutableIntStateOf(0) }
    val index = version.coerceIn(0, (episode.sources.size - 1).coerceAtLeast(0))
    val source = episode.sources.getOrNull(index)
    val sourceId = source?.id?.takeIf { it.isNotBlank() }
    val trigger = "sheet:${episode.id}:${if (mediaInfo) sourceId else "null"}"
    val dismissView = if (mediaInfo) onDetails else onDismiss
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val height = minOf(screenHeight * .85f, (screenHeight * .48f).coerceIn(270.dp, 360.dp) + 58.dp + 48.dp * (fontScale - 1f))
    RememberScreenFocus("episode:sheet-play") {
        val memory = LocalScreenFocus.current
        LaunchedEffect(mediaInfo) {
            withFrameNanos {}
            memory?.requesters?.get("episode:sheet-play")?.requestFocus()
        }
        Dialog(
            onDismissRequest = dismissView,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.matchParentSize().pointerInput(dismissView) {
                        detectTapGestures { dismissView() }
                    }
                )
                val sheetShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.widthIn(max = 960.dp)
                        .fillMaxWidth(.9f)
                        .height(height)
                        .testTag("episode:sheet")
                        .border(1.dp, Palette.BorderSubtle, sheetShape)
                        .background(Palette.Surface, sheetShape),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (mediaInfo) "媒体信息与版本" else "本集详情",
                                Modifier.weight(1f),
                                color = Palette.Text,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                            IconButton(
                                "关闭",
                                Glyph.Close,
                                onDismiss,
                                Modifier.focusTarget("sheet:close"),
                            )
                        }
                        Row(
                            Modifier.weight(1f)
                                .fillMaxWidth()
                                .clipToBounds()
                                .testTag("episode:card-content"),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            BoxWithConstraints(
                                Modifier.weight(.34f).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EpisodeArtwork(
                                    episode,
                                    images,
                                    Modifier.width(minOf(maxWidth, maxHeight * 16f / 9f)),
                                )
                            }
                            Column(
                                Modifier.weight(.66f).fillMaxHeight().padding(end = 36.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CardText(
                                    "第 ${episode.season} 季 · 第 ${episode.episode} 集",
                                    emphasized = true,
                                )
                                CardText(
                                    episode.name,
                                    title = true,
                                    lines =
                                        if (height < 360.dp || LocalDensity.current.fontScale > 1.2f) 1
                                        else 2,
                                )
                                if (mediaInfo) {
                                    if (source == null && details.loading)
                                        SkeletonTextLine(12.sp, 19.sp)
                                    else
                                        CardText(
                                            if (source == null) "暂无媒体版本"
                                            else
                                                "版本 ${index + 1} / ${episode.sources.size} · ${source.name.ifBlank { source.container.uppercase() }}"
                                        )
                                    val streams = source?.streams.orEmpty()
                                    val video = streams.firstOrNull { it.type == "Video" }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        video?.codec?.uppercase()?.takeIf { it.isNotBlank() }?.let { QualityBadge(it) }
                                        video?.width?.takeIf { it > 0 }?.let {
                                            if (it >= 3840) QualityBadge("4K")
                                            else if (it >= 1920) QualityBadge("1080P")
                                            else QualityBadge("${it}×${video.height}")
                                        }
                                        source?.bitrate?.takeIf { it > 0 }?.let {
                                            MediaPill("${it / 1_000_000} Mbps")
                                        }
                                        source?.size?.let(::fileSizeLabel)?.let {
                                            MediaPill(it)
                                        }
                                    }
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                        val tracks =
                                            streams.filter {
                                                it.type in setOf("Audio", "Subtitle")
                                            }
                                        val text =
                                            tracks
                                                .joinToString("\n") {
                                                    val type =
                                                        if (it.type == "Audio") "音轨"
                                                        else "字幕"
                                                    "$type · ${it.displayTitle.ifBlank { listOf(it.language, it.codec).filter(String::isNotBlank).joinToString(" · ") }}"
                                                }
                                                .ifBlank { "暂无音轨／字幕信息" }
                                        if (details.loading && tracks.isEmpty()) SkeletonText(lines = 3)
                                        else BoundedCardBody(text, maxHeight.value)
                                    }
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        when {
                                            episode.played -> MediaPill("已看完", icon = Glyph.Check, highlight = true)
                                            episode.resumeTicks > 0 -> MediaPill("看到 ${timeLabel(episode.resumeTicks / 10_000)}", highlight = true)
                                            else -> MediaPill("未观看")
                                        }
                                        runtimeLabel(episode.ticks)?.let {
                                            MediaPill(it, icon = Glyph.Clock)
                                        }
                                        if (has4kQuality(episode)) {
                                            QualityBadge("4K")
                                        }
                                    }
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                        if (details.loading && episode.overview.isBlank())
                                            SkeletonText(lines = 3)
                                        else
                                            BoundedCardBody(
                                                episode.overview.ifBlank {
                                                    if (details.error != null) "本集简介读取失败" else "暂无本集简介"
                                                },
                                                maxHeight.value,
                                            )
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().height(38.dp * fontScale),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardText((feedback ?: details.error)?.let(::browseErrorText) ?: " ", Modifier.weight(1f), lines = 2)
                        }
                        FixedActionArea {
                            Row(
                                Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 50.dp).testTag("episode:sheet-actions"),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val label = playLabel(episode, trigger)
                                val playWidth =
                                    if (mediaInfo) Modifier.width(200.dp) else Modifier.weight(1.3f)
                                ActionButton(
                                    if (mediaInfo && !label.startsWith("取消") && !label.startsWith("重试"))
                                        "播放此版本"
                                    else label,
                                    { onPlay(episode, false, if (mediaInfo) sourceId else null) },
                                    playWidth.fillMaxHeight().focusTarget("episode:sheet-play"),
                                    icon = Glyph.Play,
                                    primary = true,
                                    enabled = (!mediaInfo || sourceId != null) && canPlay(episode, trigger),
                                    loading = isPreparing(episode, trigger),
                                    enabledWhileLoading = true,
                                    loadingLabel = "正在准备，点击取消",
                                    maxLines = 2,
                                )
                                if (mediaInfo) {
                                    val browse = canPlay(episode, "media:${episode.id}")
                                    if (episode.sources.size > 1) {
                                        ActionButton(
                                            "上一版本",
                                            { version = index - 1 },
                                            Modifier.width(140.dp)
                                                .fillMaxHeight()
                                                .focusTarget("episode:version-prev"),
                                            icon = Glyph.Back,
                                            enabled = browse && index > 0,
                                            maxLines = 2,
                                        )
                                        ActionButton(
                                            "下一版本",
                                            { version = index + 1 },
                                            Modifier.width(140.dp)
                                                .fillMaxHeight()
                                                .focusTarget("episode:version-next"),
                                            icon = Glyph.Next,
                                            enabled = browse && index + 1 < episode.sources.size,
                                            maxLines = 2,
                                        )
                                    }
                                    ActionButton(
                                        "返回集详情",
                                        onDetails,
                                        Modifier.width(160.dp)
                                            .fillMaxHeight()
                                            .focusTarget("episode:sheet-details"),
                                        icon = Glyph.Back,
                                        maxLines = 2,
                                    )
                                } else {
                                    val start = playLabel(episode, "sheet:${episode.id}:start")
                                    ActionButton(
                                        if (start.startsWith("取消") || start.startsWith("重试")) start
                                        else "从头播放",
                                        { onPlay(episode, true, null) },
                                        Modifier.weight(1f)
                                            .fillMaxHeight()
                                            .focusTarget("episode:sheet-start"),
                                        icon = Glyph.Refresh,
                                        enabled = canPlay(episode, "sheet:${episode.id}:start"),
                                        loading = isPreparing(episode, "sheet:${episode.id}:start"),
                                        enabledWhileLoading = true,
                                        loadingLabel = "正在准备，点击取消",
                                        maxLines = 2,
                                    )
                                    ActionButton(
                                        "媒体信息",
                                        onMediaInfo,
                                        Modifier.weight(1f)
                                            .fillMaxHeight()
                                            .focusTarget("episode:sheet-media"),
                                        icon = Glyph.Info,
                                        enabled = canPlay(episode, "media:${episode.id}"),
                                        maxLines = 2,
                                    )
                                }
                                RetryAction("重试信息", details.error, details.loading, onRetry,
                                    Modifier.width(140.dp).fillMaxHeight().focusTarget("episode:sheet-retry"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoundedCardBody(text: String, heightDp: Float) {
    val lines = (heightDp / (22f * LocalDensity.current.fontScale)).toInt().coerceIn(1, 5)
    Text(
        text,
        Modifier.fillMaxWidth().testTag("episode:card-body"),
        color = Palette.Muted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardText(
    text: String,
    modifier: Modifier = Modifier,
    title: Boolean = false,
    lines: Int = 1,
    emphasized: Boolean = false,
) {
    Text(
        text,
        modifier,
        color = if (emphasized || title) Palette.Text else Palette.Muted,
        fontSize = if (title) 20.sp else 12.sp,
        lineHeight = if (title) 25.sp else 19.sp,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}
