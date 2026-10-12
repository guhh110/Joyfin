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
                            listOf(Palette.Background.copy(alpha = .62f), Color.Transparent),
                        ),
                    ),
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Palette.Background.copy(alpha = .2f),
                            Color.Transparent,
                            Palette.Background,
                        ),
                    ),
                ),
        )
        content()
    }
}
