package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.entry.components.JoyfinLogo
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvHomeHeader(actions: BrowseActions, unit: Dp, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = unit * 64)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            JoyfinLogo(Modifier.size(unit * 64))
            Text("Joyfin", color = Palette.Text, fontFamily = FontFamily.SansSerif,
                fontSize = (unit.value * 32).sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.align(Alignment.Center).widthIn(max = unit * 1050)
            .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(unit * 8),
            verticalAlignment = Alignment.CenterVertically) {
            TvHomeTab("首页", "header:home", unit, onHome, selected = true)
            TvHomeTab("追剧", "header:tracking", unit, actions.tracking)
            TvHomeTab("媒体库", "header:library", unit, { actions.library(null, "媒体库") })
            TvHomeTab("字幕", "header:subtitles", unit, actions.subtitleManager)
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(unit * 28)) {
            TvHomeTool("搜索", "header:search", Glyph.Search, unit, actions.search)
            TvHomeTool("账号与设置", "header:account", Glyph.Settings, unit, actions.settings)
        }
    }
}

@Composable
private fun TvHomeTab(text: String, key: String, unit: Dp, onClick: () -> Unit, selected: Boolean = false) {
    FocusSurface(onClick, Modifier.focusTarget(key), transparent = true, selected = selected) {
        Text(text, Modifier.padding(horizontal = unit * 27, vertical = unit * 13),
            color = if (selected) Palette.Text else Palette.Muted,
            fontFamily = FontFamily.SansSerif, fontSize = (unit.value * 26).sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TvHomeTool(label: String, key: String, glyph: Glyph, unit: Dp, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.size(unit * 64).focusTarget(key)
        .semantics { contentDescription = label }, transparent = true) {
        AppIcon(glyph, Modifier.size(unit * 29))
    }
}
