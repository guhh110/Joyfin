package dev.joyfin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.theme.Palette

enum class Glyph {
    Home,
    Library,
    Heart,
    Play,
    Back,
    Search,
    Account,
    Plus,
    Check,
    Down,
    Next,
    Close,
    List,
    Settings,
    Audio,
    Brightness,
    Subtitle,
    Info,
    Refresh,
    Star,
    Clock,
    CheckCircle,
    Film,
    Tv,
    Lock,
    Unlock,
}

@Composable
fun AppIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Canvas(modifier.size(20.dp)) {
        scale(
            size.width / 24f,
            size.height / 24f,
            pivot = androidx.compose.ui.geometry.Offset.Zero,
        ) {
            val p = Path()
            when (glyph) {
                Glyph.Home -> {
                    p.moveTo(3f, 10f); p.lineTo(12f, 3f); p.lineTo(21f, 10f)
                    p.moveTo(5f, 9f); p.lineTo(5f, 21f); p.lineTo(10f, 21f)
                    p.lineTo(10f, 14f); p.lineTo(14f, 14f); p.lineTo(14f, 21f)
                    p.lineTo(19f, 21f); p.lineTo(19f, 9f)
                }
                Glyph.Library -> {
                    p.moveTo(3f, 3f); p.lineTo(7f, 3f); p.lineTo(7f, 21f); p.lineTo(3f, 21f); p.close()
                    p.moveTo(10f, 3f); p.lineTo(13f, 3f); p.lineTo(13f, 21f); p.lineTo(10f, 21f); p.close()
                    p.moveTo(16f, 4f); p.lineTo(20f, 3f); p.lineTo(23f, 20f); p.lineTo(19f, 21f); p.close()
                }
                Glyph.Heart -> {
                    p.moveTo(12f, 21f)
                    p.cubicTo(10f, 18f, 2f, 13f, 2f, 7f)
                    p.cubicTo(2f, 1f, 10f, 1f, 12f, 7f)
                    p.cubicTo(14f, 1f, 22f, 1f, 22f, 7f)
                    p.cubicTo(22f, 13f, 14f, 18f, 12f, 21f)
                    p.close()
                }
                Glyph.Play -> {
                    p.moveTo(8f, 5f)
                    p.lineTo(20f, 12f)
                    p.lineTo(8f, 19f)
                    p.close()
                }
                Glyph.Back -> {
                    p.moveTo(15f, 5f)
                    p.lineTo(8f, 12f)
                    p.lineTo(15f, 19f)
                }
                Glyph.Next -> {
                    p.moveTo(9f, 5f)
                    p.lineTo(16f, 12f)
                    p.lineTo(9f, 19f)
                }
                Glyph.Down -> {
                    p.moveTo(5f, 9f)
                    p.lineTo(12f, 16f)
                    p.lineTo(19f, 9f)
                }
                Glyph.Close -> {
                    p.moveTo(6f, 6f)
                    p.lineTo(18f, 18f)
                    p.moveTo(18f, 6f)
                    p.lineTo(6f, 18f)
                }
                Glyph.Plus -> {
                    p.moveTo(12f, 5f)
                    p.lineTo(12f, 19f)
                    p.moveTo(5f, 12f)
                    p.lineTo(19f, 12f)
                }
                Glyph.Check -> {
                    p.moveTo(5f, 12f)
                    p.lineTo(10f, 17f)
                    p.lineTo(20f, 6f)
                }
                Glyph.Search -> {
                    drawCircle(
                        color,
                        7f,
                        androidx.compose.ui.geometry.Offset(10f, 10f),
                        style = Stroke(1.6f),
                    )
                    p.moveTo(15f, 15f)
                    p.lineTo(21f, 21f)
                }
                Glyph.Account -> {
                    drawCircle(
                        color,
                        4f,
                        androidx.compose.ui.geometry.Offset(12f, 8f),
                        style = Stroke(1.6f),
                    )
                    p.moveTo(4f, 21f)
                    p.cubicTo(4f, 12f, 20f, 12f, 20f, 21f)
                }
                Glyph.List -> {
                    for (y in listOf(6f, 12f, 18f)) {
                        p.moveTo(8f, y)
                        p.lineTo(21f, y)
                        drawCircle(color, 1f, androidx.compose.ui.geometry.Offset(3f, y))
                    }
                }
                Glyph.Settings -> {
                    // Eight teeth make this distinguishable from account and radio controls.
                    for (tooth in 0 until 8) {
                        for ((offset, radius) in listOf(-.5f to 7.5f, -.24f to 10f, .24f to 10f, .5f to 7.5f)) {
                            val angle = (tooth + offset) * kotlin.math.PI / 4.0
                            val x = 12f + kotlin.math.cos(angle).toFloat() * radius
                            val y = 12f + kotlin.math.sin(angle).toFloat() * radius
                            if (tooth == 0 && offset == -.5f) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                    }
                    p.close()
                    drawCircle(
                        color,
                        3f,
                        androidx.compose.ui.geometry.Offset(12f, 12f),
                        style = Stroke(1.6f),
                    )
                }
                Glyph.Audio -> {
                    p.moveTo(4f, 9f)
                    p.lineTo(8f, 9f)
                    p.lineTo(13f, 5f)
                    p.lineTo(13f, 19f)
                    p.lineTo(8f, 15f)
                    p.lineTo(4f, 15f)
                    p.close()
                    p.moveTo(17f, 8f)
                    p.cubicTo(21f, 10f, 21f, 14f, 17f, 16f)
                }
                Glyph.Brightness -> {
                    drawCircle(color, 4f, androidx.compose.ui.geometry.Offset(12f, 12f), style = Stroke(1.6f))
                    for (ray in 0 until 8) {
                        val angle = ray * kotlin.math.PI / 4.0
                        p.moveTo(12f + kotlin.math.cos(angle).toFloat() * 7f, 12f + kotlin.math.sin(angle).toFloat() * 7f)
                        p.lineTo(12f + kotlin.math.cos(angle).toFloat() * 10f, 12f + kotlin.math.sin(angle).toFloat() * 10f)
                    }
                }
                Glyph.Subtitle -> {
                    p.moveTo(3f, 5f)
                    p.lineTo(21f, 5f)
                    p.lineTo(21f, 19f)
                    p.lineTo(3f, 19f)
                    p.close()
                    p.moveTo(6f, 11f)
                    p.lineTo(10f, 11f)
                    p.moveTo(14f, 11f)
                    p.lineTo(18f, 11f)
                    p.moveTo(6f, 15f)
                    p.lineTo(18f, 15f)
                }
                Glyph.Info -> {
                    drawCircle(
                        color,
                        9f,
                        androidx.compose.ui.geometry.Offset(12f, 12f),
                        style = Stroke(1.6f),
                    )
                    p.moveTo(12f, 11f)
                    p.lineTo(12f, 17f)
                    drawCircle(color, 1f, androidx.compose.ui.geometry.Offset(12f, 7f))
                }
                Glyph.Refresh -> {
                    p.moveTo(20f, 6f)
                    p.lineTo(20f, 12f)
                    p.lineTo(14f, 12f)
                    p.moveTo(19f, 8f)
                    p.cubicTo(13f, -1f, 0f, 8f, 6f, 17f)
                    p.cubicTo(9f, 22f, 17f, 21f, 20f, 17f)
                }
                Glyph.Star -> {
                    for (i in 0 until 10) {
                        val angle = -kotlin.math.PI / 2.0 + i * kotlin.math.PI / 5.0
                        val r = if (i % 2 == 0) 8.5f else 3.8f
                        val x = 12f + kotlin.math.cos(angle).toFloat() * r
                        val y = 12f + kotlin.math.sin(angle).toFloat() * r
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    p.close()
                }
                Glyph.Clock -> {
                    drawCircle(
                        color,
                        9f,
                        androidx.compose.ui.geometry.Offset(12f, 12f),
                        style = Stroke(1.6f),
                    )
                    p.moveTo(12f, 7f)
                    p.lineTo(12f, 12f)
                    p.lineTo(15.5f, 12f)
                }
                Glyph.CheckCircle -> {
                    drawCircle(
                        color,
                        9f,
                        androidx.compose.ui.geometry.Offset(12f, 12f),
                        style = Stroke(1.6f),
                    )
                    p.moveTo(7.5f, 12f)
                    p.lineTo(10.5f, 15f)
                    p.lineTo(16.5f, 8.5f)
                }
                Glyph.Film -> {
                    p.moveTo(4f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 20f); p.lineTo(4f, 20f); p.close()
                    p.moveTo(4f, 8f); p.lineTo(20f, 8f)
                    p.moveTo(4f, 16f); p.lineTo(20f, 16f)
                    p.moveTo(8f, 4f); p.lineTo(8f, 8f)
                    p.moveTo(16f, 4f); p.lineTo(16f, 8f)
                    p.moveTo(8f, 16f); p.lineTo(8f, 20f)
                    p.moveTo(16f, 16f); p.lineTo(16f, 20f)
                }
                Glyph.Tv -> {
                    p.moveTo(8f, 4f); p.lineTo(12f, 7f); p.lineTo(16f, 4f)
                    p.moveTo(3f, 7f); p.lineTo(21f, 7f); p.lineTo(21f, 19f); p.lineTo(3f, 19f); p.close()
                    p.moveTo(8f, 21f); p.lineTo(16f, 21f)
                }
                Glyph.Lock -> {
                    p.moveTo(6f, 11f); p.lineTo(18f, 11f); p.lineTo(18f, 21f); p.lineTo(6f, 21f); p.close()
                    p.moveTo(8.5f, 11f); p.lineTo(8.5f, 7.5f)
                    p.cubicTo(8.5f, 4.5f, 15.5f, 4.5f, 15.5f, 7.5f); p.lineTo(15.5f, 11f)
                    p.moveTo(12f, 14.5f); p.lineTo(12f, 17.5f)
                }
                Glyph.Unlock -> {
                    p.moveTo(6f, 11f); p.lineTo(18f, 11f); p.lineTo(18f, 21f); p.lineTo(6f, 21f); p.close()
                    p.moveTo(8.5f, 11f); p.lineTo(8.5f, 7.5f)
                    p.cubicTo(8.5f, 4.5f, 15.5f, 4.5f, 15.5f, 7.5f); p.lineTo(15.5f, 6.5f)
                    p.moveTo(12f, 14.5f); p.lineTo(12f, 17.5f)
                }
            }
            drawPath(
                p,
                color,
                style =
                    if (glyph in setOf(Glyph.Play, Glyph.Star)) androidx.compose.ui.graphics.drawscope.Fill
                    else Stroke(1.6f, cap = StrokeCap.Round),
            )
        }
    }
}
