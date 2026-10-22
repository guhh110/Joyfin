package dev.joyfin.ui.features.player.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlaybackNavigationState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvSkipOffer(nav: PlaybackNavigationState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Row(modifier.background(Palette.Surface.copy(alpha = .94f), RoundedCornerShape(10.dp)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(if (nav.undoPositionMs != null) "撤销跳过" else "跳过${nav.offer?.label.orEmpty()}",
            if (nav.undoPositionMs != null) actions.undoSkip else actions.skipSegment,
            Modifier.focusTarget("player:skip-action"), primary = true)
        if (nav.undoPositionMs == null) ActionButton("不跳过", actions.dismissSkip,
            Modifier.focusTarget("player:skip-dismiss"))
    }
}
