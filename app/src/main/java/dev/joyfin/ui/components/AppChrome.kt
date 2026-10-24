package dev.joyfin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    horizontalInset: androidx.compose.ui.unit.Dp? = null, active: String? = null) {
    val device = LocalDeviceLayout.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (statusBarInset && !device.television) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = horizontalInset ?: device.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (device.television) 28.dp else 12.dp),
    ) {
        if (back) {
            IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("header:back"))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dev.joyfin.ui.features.entry.components.JoyfinLogo(
                    Modifier.size(if (device.television) 26.dp else 22.dp),
                    glow = true,
                )
                Text(
                    "JOYFIN",
                    color = Palette.Text,
                    fontSize = if (device.television) 22.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
        if (device.television) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("首页", actions.home, Modifier.focusTarget("header:home"), icon = Glyph.Home, quiet = true, selected = active == "home")
                ActionButton("媒体库", { actions.library(null, "媒体库") }, Modifier.focusTarget("header:library"), icon = Glyph.Library, quiet = true, selected = active == "library")
                ActionButton("字幕", actions.subtitleManager, Modifier.focusTarget("header:subtitles"), icon = Glyph.Subtitle, quiet = true, selected = active == "subtitles")
            }
        } else Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton("搜索", Glyph.Search, actions.search, Modifier.focusTarget("header:search"))
            IconButton("账号与设置", Glyph.Settings, actions.settings, Modifier.focusTarget("header:account"))
        }
    }
}

/** Modern, refined bottom navigation for mobile with active pill indicator and smooth color animation. */
@Composable
fun MobileBrowseNavigation(actions: BrowseActions, active: String) {
    val destinations = listOf(
        Triple("home", "首页", Glyph.Home),
        Triple("library", "媒体库", Glyph.Library),
        Triple("subtitles", "字幕", Glyph.Subtitle),
        Triple("search", "搜索", Glyph.Search),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Palette.Line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { (id, label, glyph) ->
                val current = active == id
                val itemBgColor by animateColorAsState(
                    targetValue = if (current) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = MotionTokens.Easing),
                    label = "navTabBg",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (current) Palette.Text else Palette.Muted,
                    animationSpec = tween(durationMillis = 180, easing = MotionTokens.Easing),
                    label = "navTabContent",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!current) when (id) {
                                "home" -> actions.home()
                                "library" -> actions.library(null, "媒体库")
                                "subtitles" -> actions.subtitleManager()
                                else -> actions.search()
                            }
                        }
                        .padding(vertical = 4.dp)
                        .semantics {
                            role = Role.Tab
                            selected = current
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .background(itemBgColor, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(glyph, color = contentColor)
                    }
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Modern segmented pill & filter chip; independent white outline carries TV focus without underline clash. */
@Composable
fun TextTab(
    text: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val active = selected
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(if (compact) 14.dp else 20.dp)
    FocusSurface(
        onClick,
        modifier
            .heightIn(min = if (compact) 34.dp else 42.dp)
            .semantics {
                role = Role.Tab
                this.selected = active
            },
        enabled = enabled,
        transparent = true,
        focusedScale = MotionTokens.FixedScale,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (active) {
                        Modifier
                            .background(Color(0xFF1B202D), pillShape)
                            .border(0.5.dp, Palette.Line, pillShape)
                    } else if (compact) {
                        Modifier
                            .background(Color(0xFF0F1118), pillShape)
                            .border(0.5.dp, Color(0xFF1A1C24), pillShape)
                    } else Modifier
                )
                .padding(
                    horizontal = if (compact) 10.dp else 16.dp,
                    vertical = if (compact) 6.dp else 8.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (active) Palette.Text else Palette.Muted,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
