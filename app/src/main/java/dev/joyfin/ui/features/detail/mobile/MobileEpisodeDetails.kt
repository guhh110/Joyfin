package dev.joyfin.ui.features.detail.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.*
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.detail.components.MediaInformationContent
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource

/** Episode details open from the series page without replacing or scrolling its content. */
@Composable
fun MobileEpisodeDetails(
    episode: Media,
    details: SeriesResource<Media>,
    images: ArtworkSource,
    mediaInfo: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Media, Boolean, String?) -> Unit,
    onMediaInfo: () -> Unit,
    feedback: String?,
    playLabel: (Media, String) -> String,
    canPlay: (Media, String) -> Boolean,
    isPreparing: (Media, String) -> Boolean = { _, _ -> false },
) {
    RememberScreenFocus("sheet:close") {
        Sheet(
            if (mediaInfo) "媒体信息与版本" else "本集详情",
            onDismiss,
            bottom = true,
            modifier = Modifier.testTag("episode:sheet"),
            actions = {
                Row(
                    Modifier.fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .testTag("episode:sheet-actions"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!mediaInfo) {
                        val trigger = "sheet:${episode.id}:null"
                        ActionButton(
                            playLabel(episode, trigger),
                            { onPlay(episode, false, null) },
                            Modifier.weight(1f).focusTarget("episode:sheet-play"),
                            primary = true,
                            maxLines = 2,
                            enabled = canPlay(episode, trigger),
                            loading = isPreparing(episode, trigger),
                            enabledWhileLoading = true,
                            loadingLabel = "正在准备，点击取消",
                        )
                        val startTrigger = "sheet:${episode.id}:start"
                        val startLabel = playLabel(episode, startTrigger)
                        ActionButton(
                            if (startLabel.startsWith("取消") || startLabel.startsWith("重试"))
                                startLabel
                            else "从头播放",
                            { onPlay(episode, true, null) },
                            Modifier.weight(1f).testTag("episode:sheet-start"),
                            maxLines = 2,
                            enabled = canPlay(episode, startTrigger),
                            loading = isPreparing(episode, startTrigger),
                            enabledWhileLoading = true,
                            loadingLabel = "正在准备，点击取消",
                        )
                        ActionButton(
                            "媒体信息",
                            onMediaInfo,
                            Modifier.weight(1f).testTag("episode:sheet-media"),
                            maxLines = 2,
                            enabled = canPlay(episode, "media:${episode.id}"),
                        )
                    }
                    RetryAction(
                        "重试信息",
                        details.error,
                        details.loading,
                        onRetry,
                        Modifier.weight(1f).testTag("episode:sheet-retry"),
                    )
                }
            },
        ) {
            Caption("第 ${episode.season} 季 / 第 ${episode.episode} 集")
            SectionTitle(episode.name)
            MediaMetadata(episode)
            Caption(
                when {
                    episode.played -> "已看完"
                    episode.resumeTicks > 0 -> "看到 ${timeLabel(episode.resumeTicks / 10_000)}"
                    else -> "未观看"
                }
            )
            feedback?.let { Caption(browseErrorText(it)) }
            if (!mediaInfo) {
                EpisodeArtwork(episode, images, Modifier.fillMaxWidth())
                if (details.loading && episode.overview.isBlank()) SkeletonText()
                else
                    Caption(
                        episode.overview.ifBlank {
                            if (details.error != null) "本集简介读取失败" else "暂无本集简介"
                        }
                    )
            } else if (details is SeriesResource.Ready) {
                MediaInformationContent(
                    episode,
                    playLabel = { sourceId ->
                        val label = playLabel(episode, "sheet:${episode.id}:$sourceId")
                        if (label.startsWith("取消") || label.startsWith("重试")) label else "播放此版本"
                    },
                    canPlay = { sourceId ->
                        sourceId != null && canPlay(episode, "sheet:${episode.id}:$sourceId")
                    },
                    onPlay = { sourceId -> onPlay(episode, false, sourceId) },
                    isPreparing = { sourceId ->
                        isPreparing(episode, "sheet:${episode.id}:$sourceId")
                    },
                )
            } else if (details.loading) SkeletonText(lines = 5)
            details.error?.let { Caption(browseErrorText(it)) }
        }
    }
}
