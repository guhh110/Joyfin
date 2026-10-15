package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.joyfin.data.Media
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

/** The phone artwork has its own height; long text never stretches or recrops it. */
@Composable
fun HeroBackdrop(
    item: Media?, images: ArtworkSource, minHeight: Dp, modifier: Modifier = Modifier,
    artworkHeight: Dp? = null, content: @Composable BoxScope.() -> Unit,
) {
    val tv = LocalDeviceLayout.current.television
    Box(modifier.fillMaxWidth().heightIn(min = minHeight)) {
        if (item != null) {
            Box(if (artworkHeight == null) Modifier.matchParentSize()
                else Modifier.fillMaxWidth().height(artworkHeight)) {
                Artwork(images.backdrop(item), images, Modifier.matchParentSize(),
                    alignment = if (tv) Alignment.CenterEnd else Alignment.TopCenter)
                if (tv) Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                    0f to Palette.Background.copy(alpha = .97f),
                    .30f to Palette.Background.copy(alpha = .85f),
                    .58f to Palette.Background.copy(alpha = .42f),
                    1f to Color.Transparent,
                )))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Palette.Background.copy(alpha = if (tv) .25f else .58f),
                    .24f to if (tv) Color.Transparent else Palette.Background.copy(alpha = .22f),
                    .48f to Palette.Background.copy(alpha = if (tv) .05f else .20f),
                    .76f to Palette.Background.copy(alpha = .6f),
                    1f to Palette.Background,
                )))
            }
        }
        content()
    }
}
