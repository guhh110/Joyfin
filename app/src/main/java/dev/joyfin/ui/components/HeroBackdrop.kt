package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.joyfin.data.Media
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

@Composable
fun HeroBackdrop(
    item: Media?,
    images: ArtworkSource,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight)) {
        if (item != null) {
            Artwork(images.backdrop(item), images, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.00f to Palette.Background.copy(alpha = 0.95f),
                            0.25f to Palette.Background.copy(alpha = 0.88f),
                            0.45f to Palette.Background.copy(alpha = 0.65f),
                            0.65f to Palette.Background.copy(alpha = 0.30f),
                            0.85f to Palette.Background.copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        ),
                    ),
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Palette.Background.copy(alpha = 0.25f),
                        0.15f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.65f to Palette.Background.copy(alpha = 0.20f),
                        0.80f to Palette.Background.copy(alpha = 0.60f),
                        0.92f to Palette.Background.copy(alpha = 0.90f),
                        1.00f to Palette.Background,
                    ),
                ),
        )
        content()
    }
}
