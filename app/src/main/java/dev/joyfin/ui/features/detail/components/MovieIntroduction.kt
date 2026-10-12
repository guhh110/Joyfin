package dev.joyfin.ui.features.detail.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import java.util.Locale

/** Movie playback and synopsis content shared by both layouts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MovieIntroduction(
    media: Media?,
    busy: Boolean,
    onInformation: (() -> Unit)? = null,
    loading: Boolean = busy,
    onPlay: (Media) -> Unit,
) {
    var expanded by rememberSaveable(media?.id) { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val tv = LocalDeviceLayout.current.television
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FocusSurface(
            { media?.let(onPlay) },
            Modifier.then(if (tv) Modifier.width(340.dp) else Modifier.fillMaxWidth())
                .focusTarget("detail:play", tv || (media != null && !busy))
                .semantics {
                    if (loading) {
                        contentDescription = "播放"
                        stateDescription = "正在加载"
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                },
            enabled = media != null && !busy,
            primary = true,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                        .then(
                            if (loading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(Glyph.Play, color = Palette.Background)
                        Text(
                            if (busy && !loading) "播放信息暂不可用"
                            else if (media == null) "暂无可播放内容"
                            else if (media.resumeTicks > 0) "继续观看" else "播放",
                            color = Palette.Background,
                            fontSize = 14.sp,
                        )
                    }
                    media
                        ?.takeIf { it.resumeTicks > 0 }
                        ?.let {
                            Text(
                                "看到 ${timeLabel(it.resumeTicks / 10_000)}",
                                color = Palette.Background.copy(alpha = .65f),
                                fontSize = 11.sp,
                                maxLines = 2,
                            )
                        }
                }
                if (loading) LoadingSpinner(tint = Palette.Background)
            }
        }
        if (!tv && loading && media?.overview.isNullOrBlank())
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column {
                    repeat(3) { index ->
                        Box(
                            Modifier.fillMaxWidth().height(24.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            SkeletonBlock(
                                Modifier.fillMaxWidth(if (index == 2) .6f else 1f).height(14.dp)
                            )
                        }
                    }
                }
                // Reserve the quiet "expand synopsis" action, without a focus/click target.
                Box(
                    Modifier.height(48.dp).padding(horizontal = 19.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SkeletonBlock(Modifier.width(72.dp).height(14.dp))
                }
            }
        else if (!tv && busy && media?.overview.isNullOrBlank()) Caption("剧情信息暂不可用")
        else
            Column(
                Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    media?.overview.orEmpty().ifBlank { "暂无剧情简介" },
                    color = Palette.Muted,
                    fontSize = 13.sp,
                    lineHeight = 24.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                )
                if (tv || !media?.overview.isNullOrBlank())
                    ActionButton(
                        if (expanded) "收起简介" else "展开简介",
                        { expanded = !expanded },
                        Modifier.focusTarget("detail:synopsis"),
                        enabled = !tv || (!media?.overview.isNullOrBlank() && !loading),
                        quiet = true,
                    )
            }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (
                loading &&
                    media?.people.isNullOrEmpty() &&
                    media?.locations.isNullOrEmpty() &&
                    media?.language.isNullOrBlank()
            )
                SkeletonBlock(Modifier.width(128.dp).height(18.dp))
            media?.people?.firstOrNull { it.type == "Director" }?.let { Caption("导演  ${it.name}") }
            if (!media?.locations.isNullOrEmpty())
                Caption("地区  ${media.locations.take(2).joinToString(" / ")}")
            if (!media?.language.isNullOrBlank())
                Caption(
                    "语言  ${Locale.forLanguageTag(media.language).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE).ifBlank { media.language }}"
                )
        }
        if (onInformation != null)
            ActionButton(
                "媒体信息与版本",
                onInformation,
                Modifier.focusTarget("media-info"),
                Glyph.Info,
                quiet = true,
            )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Muted.copy(alpha = .15f)))
    }
}
