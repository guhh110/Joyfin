package dev.joyfin.ui.cyber.sectors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Home
import dev.joyfin.data.Media
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.components.CyberHexBadge
import dev.joyfin.ui.cyber.components.CyberMediaCard
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

/**
 * Sector Beta: 磁悬浮弹仓滑轨阵列 (Magnetic Sled Arrays)
 * 彻底颠覆平庸单调的列表滚动，将内容组织为装甲磁浮滑道，支持装弹进膛式锁定。
 */
@Composable
fun MagneticSledSector(
    home: Home,
    images: ArtworkSource,
    actions: BrowseActions,
    onSelectMedia: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 快速点火滑轨 (QUICK-IGNITION SLED // 继续观看)
        if (home.resume.isNotEmpty()) {
            MagneticRail(
                title = "QUICK-IGNITION SLED // 继续观看",
                items = home.resume,
                images = images,
                actions = actions,
                onSelectMedia = onSelectMedia,
                badgeColor = CyberPalette.LaserCyan,
                wide = true,
            )
        }

        // 2. 最新装填滑轨 (NEW ARRIVALS SLED // 最新加入)
        if (home.latest.isNotEmpty()) {
            MagneticRail(
                title = "TACTICAL PAYLOAD // 最新加入",
                items = home.latest,
                images = images,
                actions = actions,
                onSelectMedia = onSelectMedia,
                badgeColor = CyberPalette.WarningAmber,
                wide = false,
            )
        }

        // 3. 媒体库各分类滑道
        home.rails.forEach { rail ->
            if (rail.items.isNotEmpty()) {
                MagneticRail(
                    title = "SECTOR // ${rail.library.name.uppercase()}",
                    items = rail.items,
                    images = images,
                    actions = actions,
                    onSelectMedia = onSelectMedia,
                    badgeColor = CyberPalette.TextSecondary,
                    wide = false,
                )
            }
        }
    }
}

/**
 * 单条装甲磁浮滑道 (Magnetic Rail)
 */
@Composable
private fun MagneticRail(
    title: String,
    items: List<Media>,
    images: ArtworkSource,
    actions: BrowseActions,
    onSelectMedia: (Media) -> Unit,
    badgeColor: androidx.compose.ui.graphics.Color,
    wide: Boolean,
) {
    val railShape = ChamferCornerShape(topLeft = 8.dp, bottomRight = 8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 滑轨战术标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(badgeColor),
                )
                Text(
                    text = title,
                    color = CyberPalette.TextBright,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            CyberHexBadge(text = "COUNT:${items.size}", color = badgeColor)
        }

        // 横向磁浮滑动槽
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { media ->
                CyberMediaCard(
                    item = media,
                    images = images,
                    wide = wide,
                    onClick = { onSelectMedia(media) },
                )
            }
        }
    }
}
