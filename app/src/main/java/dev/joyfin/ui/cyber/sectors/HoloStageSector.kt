package dev.joyfin.ui.cyber.sectors

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.ui.components.Artwork
import dev.joyfin.ui.components.Glyph
import dev.joyfin.ui.cyber.ChamferCornerShape
import dev.joyfin.ui.cyber.CyberPalette
import dev.joyfin.ui.cyber.CyberScanlines
import dev.joyfin.ui.cyber.DeckStance
import dev.joyfin.ui.cyber.components.CyberActionButton
import dev.joyfin.ui.cyber.components.CyberHexBadge
import dev.joyfin.ui.cyber.components.CyberPill
import dev.joyfin.ui.cyber.components.CyberRatingChip
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

/**
 * Sector Alpha: 全息主舞台视窗 (Holo-Stage Sector)
 * 1. 待机态 (STANDBY)：主视觉全息流、战术雷达标尺、核心点火键；
 * 2. 战术解构态 (DECONSTRUCT)：原地展开战术绝密档案，全息数据舱、军规参数与单集数据仓 (Data Pods)。
 */
@Composable
fun HoloStageSector(
    featuredMedia: Media?,
    focusedTarget: Media?,
    stance: DeckStance,
    images: ArtworkSource,
    actions: BrowseActions,
    onDismissDeconstruct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeMedia = if (stance == DeckStance.DECONSTRUCT && focusedTarget != null) {
        focusedTarget
    } else {
        featuredMedia
    }

    val shape = ChamferCornerShape(topLeft = 0.dp, topRight = 0.dp, bottomRight = 24.dp, bottomLeft = 24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CyberPalette.SlateMetal)
            .border(1.dp, CyberPalette.LaserCyan.copy(alpha = 0.35f), shape),
    ) {
        // 背景全息图
        if (activeMedia != null) {
            val bgUrl = images.backdrop(activeMedia)
            Artwork(
                url = bgUrl,
                images = images,
                modifier = Modifier.fillMaxSize(),
                contentDescription = activeMedia.name,
            )
            // 战术能量渐变与全息扫描线
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                CyberPalette.VoidBlack.copy(alpha = 0.3f),
                                CyberPalette.SlateMetal.copy(alpha = 0.75f),
                                CyberPalette.VoidBlack.copy(alpha = 0.95f),
                            )
                        )
                    )
            )
            CyberScanlines(Modifier.fillMaxSize())
        }

        // 视窗内容区
        if (activeMedia != null) {
            AnimatedContent(
                targetState = stance,
                transitionSpec = {
                    fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()
                },
                label = "HoloStageContent",
            ) { targetStance ->
                if (targetStance == DeckStance.DECONSTRUCT) {
                    // === 战术档案解构态 (Tactical Dossier) ===
                    TacticalDossierView(
                        media = activeMedia,
                        images = images,
                        actions = actions,
                        onDismiss = onDismissDeconstruct,
                    )
                } else {
                    // === 待机全息巡航态 (Standby Holo Cruise) ===
                    StandbyHoloView(
                        media = activeMedia,
                        actions = actions,
                    )
                }
            }
        }
    }
}

/**
 * 待机态主视觉 HUD
 */
@Composable
private fun StandbyHoloView(
    media: Media,
    actions: BrowseActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 战术编号与类型
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CyberHexBadge(text = "SECTOR // FEATURED", color = CyberPalette.LaserCyan)
            if (media.rating != null && media.rating > 0) {
                CyberRatingChip(rating = media.rating)
            }
            if (media.year > 0) {
                CyberPill(text = "YEAR:${media.year}")
            }
        }

        // 大标题
        Text(
            text = media.name,
            color = CyberPalette.TextBright,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 剧情简介
        if (media.overview.isNotBlank()) {
            Text(
                text = media.overview,
                color = CyberPalette.TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }

        Spacer(Modifier.height(4.dp))

        // 点火按钮
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CyberActionButton(
                text = "[ENGAGE CORE // 启动核心]",
                onClick = { actions.play(media, false, null) },
                primary = true,
                icon = Glyph.Play,
                modifier = Modifier.focusTarget("hero:play"),
            )
            CyberActionButton(
                text = "[DOSSIER // 检视档案]",
                onClick = { actions.open(media) },
                icon = Glyph.Info,
                modifier = Modifier.focusTarget("hero:info"),
            )
        }
    }
}

/**
 * 战术解构态：绝密档案库与数据舱 (Tactical Dossier)
 */
@Composable
private fun TacticalDossierView(
    media: Media,
    images: ArtworkSource,
    actions: BrowseActions,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 顶部控制与退出按键
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CyberHexBadge(text = "TACTICAL DOSSIER // 绝密档案", color = CyberPalette.WarningAmber)
                Text(
                    text = "ID: ${media.id.take(8).uppercase()}",
                    color = CyberPalette.LaserCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            CyberActionButton(
                text = "[COLLAPSE // 收回档案]",
                onClick = onDismiss,
                icon = Glyph.Close,
                chamfer = 6.dp,
                modifier = Modifier.focusTarget("dossier:close"),
            )
        }

        // 中央：海报与档案规格并列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 左翼海报数据舱
            val posterShape = ChamferCornerShape.diagonal(14.dp)
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(190.dp)
                    .clip(posterShape)
                    .border(1.dp, CyberPalette.LaserCyan, posterShape),
            ) {
                Artwork(
                    url = images.poster(media),
                    images = images,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = media.name,
                )
            }

            // 右翼战术规格面板
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = media.name,
                    color = CyberPalette.TextBright,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                )

                // 战术参数芯片流
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (media.rating != null && media.rating > 0) {
                        CyberRatingChip(rating = media.rating)
                    }
                    if (media.year > 0) {
                        CyberPill(text = "PROD:${media.year}")
                    }
                    CyberPill(text = "TYPE:${media.type.uppercase()}")
                }

                // 剧情摘要
                Text(
                    text = media.overview.ifBlank { "// NO ENCRYPTED BRIEFING DETECTED" },
                    color = CyberPalette.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))

                // 操作组
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberActionButton(
                        text = "[ENGAGE CORE // 点火启动]",
                        onClick = { actions.play(media, false, null) },
                        primary = true,
                        icon = Glyph.Play,
                        modifier = Modifier.focusTarget("dossier:play"),
                    )
                    if (media.resumeTicks > 0) {
                        CyberActionButton(
                            text = "[RESTART // 重置]",
                            onClick = { actions.play(media, true, null) },
                            icon = Glyph.Refresh,
                            modifier = Modifier.focusTarget("dossier:restart"),
                        )
                    }
                    CyberActionButton(
                        text = if (media.favorite) "[SAVED // 追踪中]" else "[TRACK // 标记追剧]",
                        onClick = actions.favorite,
                        icon = Glyph.Heart,
                        selected = media.favorite,
                        modifier = Modifier.focusTarget("dossier:favorite"),
                    )
                }
            }
        }
    }
}
