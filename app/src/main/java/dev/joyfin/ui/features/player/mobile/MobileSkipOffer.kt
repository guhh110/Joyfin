package dev.joyfin.ui.features.player.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlaybackNavigationState
import dev.joyfin.ui.features.player.components.PlayerSkipOffer

@Composable
internal fun MobileSkipOffer(nav: PlaybackNavigationState, actions: PlayerActions, modifier: Modifier = Modifier) {
    PlayerSkipOffer(nav, actions, modifier)
}
