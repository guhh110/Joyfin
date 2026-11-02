package dev.joyfin.ui.features.player.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSpeedBoostFeedback(speed: Float, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.testTag("player:speed-boost")
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
}
