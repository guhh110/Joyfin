package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.LocalTextStyle
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/**
 * 已连接服务器信息微卡片
 * 具备在线就绪微指示灯、微边框深色底板与紧凑“更换”操作
 */
@Composable
internal fun ConnectedServerSummary(
    name: String,
    address: String,
    enabled: Boolean,
    onChange: () -> Unit,
    actionModifier: Modifier = Modifier,
) {
    val tv = LocalDeviceLayout.current.television
    // Measure the actual fallback font and letter spacing at the active font scale.
    val changeWidth = if (tv) {
        val textWidth = rememberTextMeasurer().measure(
            text = "更换",
            style = LocalTextStyle.current.merge(
                TextStyle(fontSize = 15.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            ),
            softWrap = false,
            maxLines = 1,
        ).size.width
        (with(LocalDensity.current) { textWidth.toDp() } + 32.dp).coerceAtLeast(68.dp)
    } else 68.dp
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0F121A))
            .border(0.5.dp, Palette.Line, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryServerIcon()
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 在线就绪状态微呼吸灯
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
                Text(
                    name.ifBlank { "Jellyfin" },
                    color = Palette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                address,
                color = Palette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(changeWidth)) {
            EntryButton(
                "更换",
                onChange,
                actionModifier.focusTarget(
                    "auth:change-server",
                    LocalDeviceLayout.current.television || enabled,
                ),
                enabled = enabled,
                quiet = true,
            )
        }
    }
}
