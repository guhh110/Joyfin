package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlaybackNavigationState
import dev.joyfin.playback.PlayerActions
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun PlayerSkipOffer(nav: PlaybackNavigationState, actions: PlayerActions, modifier: Modifier = Modifier) {
    val undo = nav.undoPositionMs != null
    Row(
        modifier
            .background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .38f), Color.Black.copy(alpha = .24f),
            )), CircleShape)
            .border(.5.dp, Color.White.copy(alpha = .16f), CircleShape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerChromeButton(
            nav.skipLabel,
            if (undo) Glyph.Back else Glyph.Next,
            if (undo) actions.undoSkip else actions.skipSegment,
            Modifier.focusTarget("player:skip-action"),
            text = nav.skipLabel,
        )
        if (!undo) {
            Box(Modifier.width(.5.dp).height(18.dp).background(Color.White.copy(alpha = .16f)))
            PlayerChromeButton("不跳过", Glyph.Close, actions.dismissSkip,
                Modifier.focusTarget("player:skip-dismiss"))
        }
    }
}
