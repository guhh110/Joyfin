package dev.joyfin.ui.cyber.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.ui.components.Artwork
import dev.joyfin.ui.components.EpisodeArtwork
import dev.joyfin.ui.components.MediaCardLayout
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.cyberVisualFeedback
import dev.joyfin.ui.cyber.drawSegmentedGauge
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource

/**
 * 赛博异形装甲外骨骼海报仓 (Cyber Media Pod Card)
 * 45° 战术对角切角，四角激光准星锁定，分段反应堆等离子能量刻度槽。
 */
@Composable
fun CyberMediaCard(
    item: Media,
    images: ArtworkSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    focusKey: String = "media:${item.id}",
    cardWidth: Dp? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val tv = LocalDeviceLayout.current.television
    val imageUrl = remember(item, images, wide) {
        if (item.type == "Episode") null
        else if (wide) images.thumbnail(item) else images.poster(item)
    }
    val imageRatio = MediaCardLayout.ratio(wide)
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val shape = ChamferCornerShape.diagonal(14.dp)
    val width = cardWidth ?: MediaCardLayout.width(tv, wide)

    Column(
        modifier = modifier
            .width(width)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .focusTarget(focusKey)
            .onFocusChanged { focused = it.isFocused }
            .cyberVisualFeedback(
                focused = focused,
                shape = shape,
                interactionSource = interactionSource,
                focusedScale = if (tv) 1.05f else 1.00f,
                showBrackets = true,
            )
            .stableAction(true, interactionSource, onLongClick, onClick)
            .padding(4.dp),
    ) {
        // 海报视窗
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageRatio)
                .clip(shape),
        ) {
            if (item.type == "Episode") {
                EpisodeArtwork(item, images, Modifier.fillMaxWidth(), imageRatio)
            } else {
                Artwork(
                    imageUrl,
                    images,
                    Modifier.fillMaxWidth().aspectRatio(imageRatio),
                    item.name,
                )
            }

            // 右上角战术编号或状态角标
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                if (item.played) {
                    CyberHexBadge(text = "CHECK", color = CyberPalette.MatrixGreen)
                } else if (item.unplayedItemCount > 0) {
                    CyberHexBadge(text = "ACT:${item.unplayedItemCount}", color = CyberPalette.AlertCrimson)
                }
            }

            // 底部反应堆等离子能量槽 (Resume Progress)
            if (item.resumeTicks > 0 && item.ticks > 0) {
                val progress = (item.resumeTicks.toFloat() / item.ticks).coerceIn(0.05f, 1f)
                Canvas(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                ) {
                    drawSegmentedGauge(
                        progress = progress,
                        segments = 10,
                        activeColor = CyberPalette.LaserCyan,
                        inactiveColor = Color.Black.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 战术排印标题
        Text(
            text = item.name,
            color = if (focused) CyberPalette.LaserCyan else CyberPalette.TextBright,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 副标题 / 年份或集数
        val meta = item.productionYear?.toString() ?: item.seriesName ?: ""
        if (meta.isNotBlank()) {
            Text(
                text = "// $meta",
                color = CyberPalette.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
