package dev.joyfin.ui.cyber.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
import java.util.Locale

/**
 * 六边形战术芯片徽章 (Hexagonal Cyber Chip)
 */
@Composable
fun CyberHexBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CyberPalette.LaserCyan,
    icon: Glyph? = null,
) {
    val shape = HexagonShape(chamferWidth = 6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(CyberPalette.PanelElevated)
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            AppIcon(icon, Modifier.size(10.dp), color = color)
        }
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

/**
 * 战术微标胶囊 (Chamfered Tactical Pill)
 */
@Composable
fun CyberPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    highlightColor: Color = CyberPalette.LaserCyan,
) {
    val shape = ChamferCornerShape.diagonal(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(CyberPalette.PanelElevated)
            .border(1.dp, highlightColor.copy(alpha = 0.4f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            AppIcon(icon, Modifier.size(11.dp), color = highlightColor)
        }
        Text(
            text = text,
            color = CyberPalette.TextBright,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

/**
 * 评分雷达芯片 (Rating Radar Chip)
 */
@Composable
fun CyberRatingChip(rating: Double, modifier: Modifier = Modifier) {
    CyberHexBadge(
        text = String.format(Locale.ROOT, "★ %.1f", rating),
        color = CyberPalette.WarningAmber,
        modifier = modifier,
    )
}
