package dev.joyfin.ui.cyber.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.AppIcon
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.components.LoadingSpinner
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.cyberVisualFeedback

/**
 * 赛博异形装甲战术按键 (Cyber Action Button)
 * 45° 倒角战术卡榫，激光霓虹青能量填充，等离子刻度旋转加载。
 */
@Composable
fun CyberActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    quiet: Boolean = false,
    maxLines: Int = 1,
    loading: Boolean = false,
    chamfer: Dp = 10.dp,
    shape: ChamferCornerShape = ChamferCornerShape.diagonal(chamfer),
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val textColor = when {
        !enabled -> CyberPalette.TextDim
        primary && focused -> CyberPalette.VoidBlack
        primary -> CyberPalette.VoidBlack
        focused -> CyberPalette.LaserCyan
        selected -> CyberPalette.WarningAmber
        else -> CyberPalette.TextBright
    }

    val primaryBg = if (focused) CyberPalette.LaserCyan else CyberPalette.LaserCyan.copy(alpha = 0.90f)

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .onFocusChanged { focused = it.isFocused }
            .cyberVisualFeedback(
                focused = focused,
                enabled = enabled,
                selected = selected,
                primary = primary,
                shape = shape,
                interactionSource = interactionSource,
                customBackground = if (primary) primaryBg else if (quiet && !focused) Color.Transparent else null,
            )
            .stableAction(enabled, interactionSource, null, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                LoadingSpinner(
                    Modifier.size(16.dp),
                    color = textColor,
                )
            } else if (icon != null) {
                AppIcon(icon, Modifier.size(16.dp), color = textColor)
            }

            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (primary || focused) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
