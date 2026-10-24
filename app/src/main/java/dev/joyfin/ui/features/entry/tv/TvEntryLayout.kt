package dev.joyfin.ui.features.entry.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvEntryLayout(
    step: String,
    title: String?,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .testTag("entry:screen")
    ) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        // 纯 Compose 渲染无界影院微光漫反射背景
        EntryArtwork(isTelevision = true)

        Row(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 56.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左翼：品牌核心与影院纯粹视听定位 (不堆砌多余口号)
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部品牌微组
                EntryBrand(22.sp)

                // 中央品牌图腾与影院定位
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    JoyfinLogo(Modifier.size(76.dp), glow = true)
                    Text(
                        "JOYFIN",
                        color = Palette.Text,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF12151F))
                            .border(0.5.dp, Palette.Line, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "BOUNDLESS CINEMA",
                            color = Palette.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        Box(Modifier.size(3.dp).background(Palette.Muted, RoundedCornerShape(1.dp)))
                        Text(
                            "PERSONAL MEDIA HUB",
                            color = Palette.Muted.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                // 底部沉稳留白与设备说明
                Text(
                    "JELLYFIN MEDIA CLIENT",
                    color = Palette.Muted.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                )
            }

            Spacer(Modifier.width(36.dp))

            // 右翼：460dp 悬浮玻璃控制台
            val consoleShape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(consoleShape)
                    .background(Color(0xFF0C0E15).copy(alpha = 0.85f))
                    .border(0.5.dp, Palette.Line, consoleShape)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                if (title != null) {
                    Text(
                        title,
                        color = Palette.Text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            subtitle,
                            color = Palette.Muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                EntryFormViewport(
                    topPadding = 4.dp,
                    bottomPadding = 8.dp,
                    width = 440.dp,
                    spacing = 8.dp,
                    arrangement = Arrangement.Top,
                    content = content,
                )
                if (!imeVisible) {
                    EntryFooter(step, bottomPadding = 4.dp)
                }
            }
        }
    }
}
