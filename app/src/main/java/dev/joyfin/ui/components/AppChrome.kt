package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

@Composable
fun AppHeader(actions: BrowseActions, back: Boolean = false, statusBarInset: Boolean = false,
    horizontalInset: androidx.compose.ui.unit.Dp? = null) {
    val device = LocalDeviceLayout.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (statusBarInset && !device.television) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = horizontalInset ?: device.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (device.television) 28.dp else 12.dp),
    ) {
        if (back) IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("header:back"))
        else Text("Joyfin", color = Palette.Text, fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
        if (device.television) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("首页", actions.home, Modifier.focusTarget("header:home"), icon = Glyph.Home, quiet = true)
                ActionButton("媒体库", { actions.library(null, "媒体库") }, Modifier.focusTarget("header:library"), icon = Glyph.Library, quiet = true)
                ActionButton("字幕", actions.subtitleManager, Modifier.focusTarget("header:subtitles"), icon = Glyph.Subtitle, quiet = true)
            }
        } else Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton("搜索", Glyph.Search, actions.search, Modifier.focusTarget("header:search"))
            IconButton("账号与设置", Glyph.Settings, actions.settings, Modifier.focusTarget("header:account"))
        }
    }
}

/** Navigation belongs to the same page focus scope; returning can restore the tapped tab. */
@Composable
fun MobileBrowseNavigation(actions: BrowseActions, active: String) {
    val destinations = listOf(
        Triple("home", "首页", Glyph.Home), Triple("library", "媒体库", Glyph.Library),
        Triple("subtitles", "字幕", Glyph.Subtitle), Triple("search", "搜索", Glyph.Search),
    )
    Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.forEach { (id, label, glyph) ->
                val current = active == id
                FocusSurface({
                    if (!current) when (id) {
                        "home" -> actions.home()
                        "library" -> actions.library(null, "媒体库")
                        "subtitles" -> actions.subtitleManager()
                        else -> actions.search()
                    }
                }, Modifier.weight(1f).heightIn(min = 48.dp).focusTarget("navigation:$id")
                    .semantics { selected = current }, transparent = true,
                    focusedScale = MotionTokens.FixedScale) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val color = if (current) Palette.Text else Palette.Muted
                        AppIcon(glyph, color = color)
                        Text(label, color = color, fontSize = 11.sp, lineHeight = 16.sp,
                            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** An underline carries the selected state; the independent white outline carries TV focus. */
@Composable
fun TextTab(text: String, onClick: () -> Unit, selected: Boolean, modifier: Modifier = Modifier,
    enabled: Boolean = true, compact: Boolean = false) {
    val active = selected
    FocusSurface(onClick, modifier.heightIn(min = 48.dp).semantics {
        role = Role.Tab
        this.selected = active
    }, enabled = enabled, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, Modifier.padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 7.dp),
                color = if (active) Palette.Text else Palette.Muted, fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) Palette.Text else Color.Transparent))
        }
    }
}

@Composable
fun SectionHeading(title: String, trailing: String? = null, onMore: (() -> Unit)? = null,
    focusKey: String = "heading:$title:more") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, Modifier.weight(1f))
        if (trailing != null && onMore != null)
            ActionButton(trailing, onMore, Modifier.focusTarget(focusKey), quiet = true, maxLines = 1)
    }
}
