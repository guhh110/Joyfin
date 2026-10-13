package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
fun NextEpisodeCard(
    item: Media,
    seconds: Int,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 420.dp)
            .background(Palette.Surface, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Caption("${seconds} 秒后播放下一集", color = Palette.Gold)
        SectionTitle(item.label)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                "立即播放",
                onNext,
                Modifier.focusProperties {
                        left = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .focusTarget("player:confirm-next"),
                icon = Glyph.Play,
                primary = true,
            )
            ActionButton(
                "取消连播",
                onCancel,
                Modifier.focusProperties {
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .focusTarget("player:cancel-next"),
            )
        }
    }
}
