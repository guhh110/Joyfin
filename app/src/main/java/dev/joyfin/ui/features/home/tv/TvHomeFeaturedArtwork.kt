package dev.joyfin.ui.features.home.tv

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

internal data class TvHomeFeaturedArtwork(
    val key: String,
    val request: ImageRequest?,
    val landscape: Boolean,
    val tint: Color = Color.Transparent,
)

internal fun tvHomeAtmosphereColor(pixels: IntArray): Color {
    var red = 0f
    var green = 0f
    var blue = 0f
    var total = 0f
    pixels.forEach { pixel ->
        val alpha = (pixel ushr 24) / 255f
        val pixelRed = (pixel ushr 16 and 255) / 255f
        val pixelGreen = (pixel ushr 8 and 255) / 255f
        val pixelBlue = (pixel and 255) / 255f
        val highest = maxOf(pixelRed, pixelGreen, pixelBlue)
        val lowest = minOf(pixelRed, pixelGreen, pixelBlue)
        if (highest in .08f..0.96f && alpha > .5f) {
            val weight = (highest - lowest + .05f) * alpha
            red += pixelRed * weight
            green += pixelGreen * weight
            blue += pixelBlue * weight
            total += weight
        }
    }
    if (total == 0f) return Color.Transparent
    red /= total
    green /= total
    blue /= total
    val neutral = (red + green + blue) / 3
    return Color(red * .65f + neutral * .35f, green * .65f + neutral * .35f,
        blue * .65f + neutral * .35f)
}

@Composable
internal fun TvHomeFeaturedBackdrop(state: TvHomeFeaturedState, unit: Dp) {
    val background = Palette.Background
    Box(Modifier.fillMaxSize().background(background)) {
        Crossfade(state.artwork, animationSpec = rememberMotionSpec(320), label = "featured-artwork") { frame ->
            Box(Modifier.fillMaxSize()) {
                if (frame?.request != null && frame.landscape) {
                    AsyncImage(frame.request, contentDescription = null,
                        modifier = Modifier.fillMaxSize().testTag("hero:backdrop:${frame.key}"),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.CenterEnd)
                }
                Box(Modifier.matchParentSize().drawWithCache {
                    val tint = frame?.tint ?: Color.Transparent
                    val poster = frame?.request != null && !frame.landscape
                    val atmosphere = Brush.radialGradient(
                        listOf(tint.copy(alpha = tint.alpha * if (poster) .58f else .16f), Color.Transparent),
                        center = Offset(size.width * .77f, size.height * .43f),
                        radius = size.maxDimension * if (poster) .60f else .85f)
                    onDrawBehind { drawRect(atmosphere) }
                })
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                    0f to background.copy(alpha = .98f), .24f to background.copy(alpha = .90f),
                    .5f to background.copy(alpha = .46f), .74f to background.copy(alpha = .025f),
                    1f to Color.Transparent)))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to background.copy(alpha = .48f), .18f to Color.Transparent,
                    .60f to Color.Transparent, .86f to background.copy(alpha = .82f), 1f to background)))
                if (frame?.request != null) {
                    if (!frame.landscape) {
                        val shape = RoundedCornerShape(unit * 18)
                        AsyncImage(frame.request, contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .padding(end = unit * 208, bottom = unit * 104)
                                .size(unit * 448, unit * 660)
                                .shadow(unit * 28, shape)
                                .clip(shape).border(unit, Palette.Text.copy(alpha = .10f), shape)
                                .testTag("hero:backdrop:${frame.key}"),
                            contentScale = ContentScale.Fit)
                    }
                    Box(Modifier.matchParentSize().testTag(
                        if (frame.landscape) "hero:backdrop:landscape" else "hero:backdrop:poster"))
                }
            }
        }
    }
}
