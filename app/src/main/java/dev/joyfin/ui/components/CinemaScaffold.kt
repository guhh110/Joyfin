package dev.joyfin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.CinemaStyle
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

/** A static light field needs neither a bitmap nor RenderEffect (Android 9+). */
fun Modifier.cinemaBackground(): Modifier = background(CinemaStyle.Background).drawWithCache {
    val light = Brush.radialGradient(
        listOf(CinemaStyle.Accent.copy(alpha = .065f), Color.Transparent),
        Offset(size.width * .7f, size.height * .22f), size.width * .7f,
    )
    onDrawBehind { drawRect(light) }
}

@Composable
fun CinemaHeader(
    title: String,
    actions: BrowseActions,
    modifier: Modifier = Modifier,
    back: Boolean = true,
    active: String? = null,
    backFocusKey: String = "header:back",
    leading: @Composable () -> Unit = {},
    tools: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    focusModifier: @Composable (Modifier, String) -> Modifier = { modifier, key -> modifier.focusTarget(key) },
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("cinema:header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (back) IconButton("返回", Glyph.Back, actions.back, focusModifier(Modifier, backFocusKey))
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (titleContent != null) titleContent()
            else {
                Text("JOYFIN / CINERAMA", color = CinemaStyle.Muted.copy(alpha = .55f),
                    fontSize = 9.sp, letterSpacing = 2.sp, maxLines = 1)
                Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
        }
        tools()
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            CinemaNav("首页", Glyph.Home, actions.home, "header:home", active == "home", focusModifier)
            CinemaNav("追剧", Glyph.Heart, actions.tracking, "header:tracking", active == "tracking", focusModifier)
            CinemaNav("媒体库", Glyph.Library, { actions.library(null, "媒体库") }, "header:library", active == "library", focusModifier)
            CinemaNav("搜索", Glyph.Search, actions.search, "header:search", active == "search", focusModifier)
            CinemaNav("设置", Glyph.Settings, actions.settings, "header:account", active == "settings", focusModifier)
        }
    }
}

@Composable
private fun CinemaNav(label: String, icon: Glyph, action: () -> Unit, key: String, selected: Boolean,
    focusModifier: @Composable (Modifier, String) -> Modifier) {
    var focused by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (selected) CinemaStyle.Accent else if (focused) Color.White else CinemaStyle.Muted,
        rememberMotionSpec(140), label = "cinemaNavTint")
    // The slot stays fixed when its label appears, so moving focus never moves neighbours.
    FocusSurface(action, focusModifier(Modifier.width(52.dp).height(52.dp), key)
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label; this.selected = selected },
        transparent = true, focusedScale = 1.02f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AppIcon(icon, Modifier.size(18.dp), tint)
            if (selected || focused) Text(label, color = tint, fontSize = 10.sp, maxLines = 1)
            else Spacer(Modifier.height(13.dp))
        }
    }
}
