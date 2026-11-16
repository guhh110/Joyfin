package dev.joyfin.ui.cyber.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.HexagonShape
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV 端：星舰舰桥 HUD 战术中枢龙骨 (TV Bridge Command Spine)
 */
@Composable
fun TvBridgeCommandSpine(
    actions: BrowseActions,
    activeSector: String,
    modifier: Modifier = Modifier,
) {
    var systemTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        while (true) {
            systemTime = format.format(Date())
            delay(1000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 左翼：战术 Logo 与状态微标
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(HexagonShape(chamferWidth = 3.dp))
                    .background(CyberPalette.LaserCyan),
            )
            Text(
                text = "JOYFIN // CYBERDECK",
                color = CyberPalette.LaserCyan,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        // 中央：斜角战术多边形索引仓
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberActionButton(
                text = "[RECON // 首页]",
                onClick = actions.home,
                selected = activeSector == "home",
                quiet = activeSector != "home",
                modifier = Modifier.focusTarget("header:home"),
            )
            CyberActionButton(
                text = "[RADAR // 追剧]",
                onClick = actions.tracking,
                selected = activeSector == "tracking",
                quiet = activeSector != "tracking",
                modifier = Modifier.focusTarget("header:tracking"),
            )
            CyberActionButton(
                text = "[ARCHIVE // 媒体库]",
                onClick = { actions.library(null, "媒体库") },
                selected = activeSector == "library",
                quiet = activeSector != "library",
                modifier = Modifier.focusTarget("header:library"),
            )
            CyberActionButton(
                text = "[DATA BUS // 字幕]",
                onClick = actions.subtitleManager,
                selected = activeSector == "subtitles",
                quiet = activeSector != "subtitles",
                modifier = Modifier.focusTarget("header:subtitles"),
            )
        }

        // 右翼：量子传感器读数、系统时间与快速检索
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CyberActionButton(
                text = "[SEARCH]",
                onClick = actions.search,
                icon = Glyph.Search,
                chamfer = 6.dp,
                modifier = Modifier.focusTarget("header:search"),
            )
            CyberActionButton(
                text = "[SETTINGS]",
                onClick = actions.settings,
                icon = Glyph.Settings,
                chamfer = 6.dp,
                modifier = Modifier.focusTarget("header:account"),
            )

            // 战术时间读数
            if (systemTime.isNotBlank()) {
                Box(
                    Modifier
                        .clip(ChamferCornerShape.diagonal(6.dp))
                        .background(CyberPalette.PanelElevated)
                        .border(1.dp, CyberPalette.ArmorBorder, ChamferCornerShape.diagonal(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "SYS.T $systemTime",
                        color = CyberPalette.LaserCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 手机端：悬浮双翼赛博甲板底栏 (Mobile Twin-Wing Tactical Cyberdeck)
 */
@Composable
fun MobileTwinWingCyberdeck(
    actions: BrowseActions,
    activeSector: String,
    modifier: Modifier = Modifier,
) {
    val shape = ChamferCornerShape(topLeft = 16.dp, topRight = 16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CyberPalette.PanelSurface.copy(alpha = 0.95f))
                .border(1.dp, CyberPalette.LaserCyan.copy(alpha = 0.4f), shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            CyberdeckTabItem(
                label = "RECON",
                icon = Glyph.Home,
                selected = activeSector == "home",
                onClick = actions.home,
            )
            CyberdeckTabItem(
                label = "RADAR",
                icon = Glyph.Heart,
                selected = activeSector == "tracking",
                onClick = actions.tracking,
            )
            CyberdeckTabItem(
                label = "ARCHIVE",
                icon = Glyph.Library,
                selected = activeSector == "library",
                onClick = { actions.library(null, "媒体库") },
            )
            CyberdeckTabItem(
                label = "BUS",
                icon = Glyph.Subtitle,
                selected = activeSector == "subtitles",
                onClick = actions.subtitleManager,
            )
            CyberdeckTabItem(
                label = "SCAN",
                icon = Glyph.Search,
                selected = activeSector == "search",
                onClick = actions.search,
            )
        }
    }
}

@Composable
private fun CyberdeckTabItem(
    label: String,
    icon: Glyph,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) CyberPalette.LaserCyan else CyberPalette.TextSecondary
    val shape = ChamferCornerShape.diagonal(6.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppIcon(icon, Modifier.size(18.dp), color = color)
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
