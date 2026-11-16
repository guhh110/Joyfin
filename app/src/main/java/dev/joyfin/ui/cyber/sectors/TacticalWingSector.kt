package dev.joyfin.ui.cyber.sectors

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.CyberScanlines
import dev.joyfin.ui.cyber.WingType
import dev.joyfin.ui.cyber.components.CyberActionButton
import dev.joyfin.ui.cyber.components.CyberHexBadge
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions

/**
 * Sector Delta: 折叠式战术副翼 (Deployable Tactical Wings)
 * 从屏幕边缘液压展开的战术副翼，承接搜索、字幕总线与系统设置。
 */
@Composable
fun TacticalWingSector(
    activeWing: WingType,
    actions: BrowseActions,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = activeWing != WingType.NONE,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val wingShape = ChamferCornerShape(topLeft = 20.dp, bottomLeft = 20.dp)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .clip(wingShape)
                .background(CyberPalette.SlateMetal)
                .border(1.dp, CyberPalette.LaserCyan, wingShape)
                .padding(20.dp),
        ) {
            CyberScanlines(Modifier.fillMaxSize())

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 副翼标题栏与收起按键
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val title = when (activeWing) {
                        WingType.SEARCH_RADAR -> "QUANTUM RADAR // 搜索"
                        WingType.SUBTITLE_BUS -> "DATA BUS // 字幕总线"
                        WingType.MAINFRAME_SETTINGS -> "MAINFRAME // 中控设置"
                        else -> "TACTICAL WING"
                    }
                    CyberHexBadge(text = title, color = CyberPalette.LaserCyan)

                    CyberActionButton(
                        text = "[RETRACT // 收起]",
                        onClick = onRetract,
                        icon = Glyph.Close,
                        chamfer = 6.dp,
                        modifier = Modifier.focusTarget("wing:close"),
                    )
                }

                // 副翼内容
                when (activeWing) {
                    WingType.SEARCH_RADAR -> {
                        SearchRadarContent(actions = actions)
                    }
                    WingType.SUBTITLE_BUS -> {
                        SubtitleBusContent(actions = actions)
                    }
                    WingType.MAINFRAME_SETTINGS -> {
                        MainframeSettingsContent(actions = actions)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SearchRadarContent(actions: BrowseActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "// INITIALIZE SCAN PROTOCOL",
            color = CyberPalette.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        CyberActionButton(
            text = "[OPEN FULL RADAR // 启动全域雷达]",
            onClick = actions.search,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubtitleBusContent(actions: BrowseActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "// HOT-SWAPPABLE DATA CHIPS",
            color = CyberPalette.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        CyberActionButton(
            text = "[MANAGE DATA BUS // 管理字幕总线]",
            onClick = actions.subtitleManager,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MainframeSettingsContent(actions: BrowseActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "// CENTRAL PROCESSOR TELEMETRY",
            color = CyberPalette.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        CyberActionButton(
            text = "[OPEN MAINFRAME // 进入中控机房]",
            onClick = actions.settings,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
