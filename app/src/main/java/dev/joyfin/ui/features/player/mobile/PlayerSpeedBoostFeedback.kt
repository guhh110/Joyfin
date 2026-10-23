package dev.joyfin.ui.features.player.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.theme.Palette

@Composable
internal fun PlayerSpeedBoostFeedback(speed: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.testTag("player:speed-boost")
            .background(Palette.Background.copy(alpha = .94f), shape)
            .border(.5.dp, Palette.Line, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(Glyph.Play, Modifier.size(18.dp))
            Caption("${speed.toString().removeSuffix(".0")}× 倍速播放", color = Palette.Text)
        }
        Caption("松手恢复原倍速")
    }
}
