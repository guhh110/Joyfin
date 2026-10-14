package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.Palette

@Composable
fun AppHeader(actions: BrowseActions, back: Boolean = false) {
    val device = LocalDeviceLayout.current
    val narrow = !device.television && device.widthDp < 380
    if (narrow && !back) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = device.gutter, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Joyfin",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Palette.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton("搜索", Glyph.Search, actions.search, Modifier.focusTarget("header:search"))
                    IconButton(
                        "账号与设置",
                        Glyph.Account,
                        actions.settings,
                        Modifier.focusTarget("header:account"),
                    )
                }
            }
            ActionButton(
                "字幕管理",
                actions.subtitleManager,
                Modifier.focusTarget("header:subtitles"),
                quiet = true,
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = device.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (back) IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("header:back"))
            else
                Text(
                    "Joyfin",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Palette.Text,
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("字幕管理", actions.subtitleManager, Modifier.focusTarget("header:subtitles"), quiet = true)
                IconButton("搜索", Glyph.Search, actions.search, Modifier.focusTarget("header:search"))
                IconButton(
                    "账号与设置",
                    Glyph.Account,
                    actions.settings,
                    Modifier.focusTarget("header:account"),
                )
            }
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    trailing: String? = null,
    onMore: (() -> Unit)? = null,
    focusKey: String = "heading:$title:more",
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionTitle(title, Modifier.weight(1f))
        if (trailing != null && onMore != null)
            ActionButton(trailing, onMore, Modifier.focusTarget(focusKey), quiet = true)
    }
}
