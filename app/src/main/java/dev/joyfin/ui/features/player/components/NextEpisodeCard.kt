package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
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
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .widthIn(max = 420.dp)
            .background(Color(0xFF0C0E14).copy(alpha = 0.94f), cardShape)
            .border(0.5.dp, Palette.Line, cardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaPill("${seconds}s", icon = Glyph.Clock, highlight = true)
            Text(
                "即将播放下一集",
                color = Palette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
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
