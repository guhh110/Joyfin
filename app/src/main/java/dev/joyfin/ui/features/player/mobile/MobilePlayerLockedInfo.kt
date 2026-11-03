package dev.joyfin.ui.features.player.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.components.timeLabel
import dev.joyfin.ui.features.player.components.PlayerBattery
import dev.joyfin.ui.features.player.components.PlayerClock
import dev.joyfin.ui.features.player.components.drawSkipTimeline
import dev.joyfin.ui.features.player.components.skipTimelineDescription
import dev.joyfin.ui.theme.Palette

/** Read-only information; taps pass through to the locked video surface. */
@Composable
internal fun MobilePlayerLockedInfo(ui: PlayerUiState, modifier: Modifier = Modifier) {
    val fraction = if (ui.durationMs > 0)
        (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxSize().testTag("player:locked-info")
            .background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = .65f), Color.Transparent, Color.Black.copy(alpha = .7f),
            )))
            .safeDrawingPadding().padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caption(ui.title, color = Palette.Text, maxLines = 1)
                if (ui.subtitle.isNotBlank()) Caption(ui.subtitle, maxLines = 1)
            }
            PlayerClock()
            PlayerBattery()
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(Glyph.Lock, Modifier.size(14.dp), color = Palette.Muted)
                val status = when {
                    ui.error != null -> "播放失败，解锁后可重试"
                    ui.nextSeconds > 0 -> "${ui.nextSeconds} 秒后播放下一集"
                    !ui.playing && !ui.loading -> "已锁定 · 已暂停"
                    else -> "屏幕已锁定"
                }
                Caption(status, Modifier.weight(1f), maxLines = 1)
                Caption(
                    "${timeLabel(ui.positionMs)} / ${if (ui.durationMs > 0) timeLabel(ui.durationMs) else "--:--"}",
                    color = Palette.Text,
                    maxLines = 1,
                )
            }
            Canvas(
                Modifier.fillMaxWidth().height(16.dp)
                    .semantics {
                        contentDescription = listOf("播放进度", skipTimelineDescription(ui.navigation.ranges))
                            .filter { it.isNotBlank() }.joinToString("，")
                        progressBarRangeInfo = if (ui.durationMs > 0) ProgressBarRangeInfo(fraction, 0f..1f)
                            else ProgressBarRangeInfo.Indeterminate
                    },
            ) {
                val y = 3.dp.toPx()
                drawLine(Palette.Text.copy(alpha = .2f), Offset(0f, y), Offset(size.width, y),
                    3.dp.toPx(), StrokeCap.Round)
                if (fraction > 0f) drawLine(Palette.Text, Offset(0f, y), Offset(size.width * fraction, y),
                    3.dp.toPx(), StrokeCap.Round)
                drawSkipTimeline(ui.navigation.ranges, ui.durationMs, ui.positionMs, 11.dp.toPx())
            }
        }
    }
}
