package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.theme.Palette

/**
 * 官方【纯粹微笑光弧 · Joy Smile (No Dot)】高保真矢量渲染组件
 * 基于 512x512 黄金比例标准坐标系统，支持任意尺寸无损缩放
 */
@Composable
fun JoyfinLogo(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
) {
    Canvas(modifier) {
        val s = size.minDimension / 512f
        scale(s, s, Offset.Zero) {
            // 背景漫射微光 (可选微辉光)
            if (glow) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color(0xFFE2E8F0).copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = Offset(270f, 250f),
                        radius = 220f,
                    ),
                    radius = 220f,
                    center = Offset(270f, 250f),
                )
            }

            // 播放器三角 ▶ (黄金分割等边圆角三角)
            val trianglePath = Path().apply {
                moveTo(234f, 187f)
                cubicTo(234f, 174f, 249f, 166f, 260f, 173f)
                lineTo(351f, 226f)
                cubicTo(362f, 233f, 362f, 249f, 351f, 256f)
                lineTo(260f, 309f)
                cubicTo(249f, 316f, 234f, 308f, 234f, 295f)
                close()
            }
            drawPath(
                path = trianglePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFE2E8F0), Color(0xFF94A3B8)),
                    start = Offset(234f, 170f),
                    end = Offset(362f, 310f),
                ),
            )

            // Joy 微笑托底光弧
            val smilePath = Path().apply {
                moveTo(151f, 202f)
                cubicTo(151f, 302f, 206f, 372f, 291f, 372f)
                cubicTo(344f, 372f, 386f, 344f, 401f, 324f)
            }
            drawPath(
                path = smilePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFE2E8F0), Color(0xFF64748B)),
                    start = Offset(151f, 372f),
                    end = Offset(401f, 202f),
                ),
                style = Stroke(
                    width = 13f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * 纯 Compose Canvas 渲染的无界影院微光漫反射背景
 * 替代旧版昏暗的本地码头位图，带来沉浸、纯粹的黑白灰光影氛围
 */
@Composable
internal fun EntryArtwork(
    widthFraction: Float = 1f,
    isTelevision: Boolean = false,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        val w = size.width
        val h = size.height

        if (isTelevision) {
            // TV 端：左侧偏上微光漫射，形成影院环境幕布感
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E2333).copy(alpha = 0.55f),
                        Color(0xFF0F1219).copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.28f, h * 0.40f),
                    radius = w * 0.45f,
                ),
                center = Offset(w * 0.28f, h * 0.40f),
                radius = w * 0.45f,
            )
            // 右侧微透暖银底纹，烘托悬浮控制台
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF181C26).copy(alpha = 0.30f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.78f, h * 0.50f),
                    radius = w * 0.35f,
                ),
                center = Offset(w * 0.78f, h * 0.50f),
                radius = w * 0.35f,
            )
        } else {
            // 手机端：顶部中央微光延伸
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E2333).copy(alpha = 0.50f),
                        Color(0xFF0E1118).copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, h * 0.22f),
                    radius = w * 0.75f,
                ),
                center = Offset(w * 0.5f, h * 0.22f),
                radius = w * 0.75f,
            )
        }
    }
}

@Composable
internal fun ColumnScope.EntryFormViewport(
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    arrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .testTag("entry:form-scroll")
                .padding(top = topPadding, bottom = bottomPadding),
            verticalArrangement = arrangement,
        ) {
            Column(
                Modifier.widthIn(max = width).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

@Composable
internal fun EntryFooter(step: String, bottomPadding: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BOUNDLESS CINEMA",
            color = Palette.Muted.copy(alpha = .7f),
            fontSize = 9.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            step,
            color = Palette.Muted,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 品牌页头微组：官方纯粹微笑光弧 Logo + JOYFIN 字标
 */
@Composable
internal fun EntryBrand(fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JoyfinLogo(Modifier.size(28.dp), glow = true)
        Text(
            "JOYFIN",
            color = Palette.Text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Palette.Line)
        )
        Text(
            "无界影院",
            color = Palette.Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * 现代胶囊步骤微标 (Step Pill)
 */
@Composable
internal fun EntryStep(stepNumber: String, stepTitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Line.copy(alpha = 0.5f))
            .border(0.5.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Palette.Text)
        )
        Text(
            "$stepNumber · $stepTitle",
            color = Palette.Text,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 保持旧版兼容入口 */
@Composable
internal fun EntryStep(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Line.copy(alpha = 0.4f))
            .border(0.5.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Palette.Text.copy(alpha = 0.8f))
        )
        Text(
            text,
            color = Palette.Text,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
