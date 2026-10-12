package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.SeekPreviewState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.theme.Palette
import kotlin.math.abs

@Composable
fun SeekTooltip(state: SeekPreviewState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .testTag("seek-preview")
            .background(Palette.Background.copy(alpha = .92f), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(
            "${if (state.deltaMs < 0) "快退" else "快进"} ${abs(state.deltaMs) / 1000} 秒 · ${timeLabel(state.targetMs)}"
        )
        Caption(
            if (!state.active) "已跳转"
            else if (state.input == dev.joyfin.playback.SeekInput.REMOTE)
                "停止操作 1 秒后跳转 · 确认立即跳转 · 返回取消"
            else "松手跳转"
        )
    }
}
