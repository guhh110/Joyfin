package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/** Player-specific chrome: the compact heading never consumes the results' scroll budget. */
@Composable
internal fun PlayerSubtitleSearchPanel(
    title: String,
    close: () -> Unit,
    modifier: Modifier,
    split: Boolean,
    controls: @Composable (vertical: Boolean) -> Unit,
    results: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.border(1.dp, Palette.BorderSubtle, shape).background(Palette.Surface, shape)
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), color = Palette.Text, fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton("关闭", Glyph.Close, close, Modifier.focusTarget("sheet:close"))
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (split) {
                val controlsWidth = (maxWidth * .28f).coerceIn(220.dp, 260.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(controlsWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { controls(true) }
                    Box(Modifier.weight(1f).fillMaxHeight()) { results() }
                }
            } else {
                val controlsHeight = maxHeight * .45f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { controls(false) }
                    Box(Modifier.weight(1f).fillMaxWidth()) { results() }
                }
            }
        }
    }
}
