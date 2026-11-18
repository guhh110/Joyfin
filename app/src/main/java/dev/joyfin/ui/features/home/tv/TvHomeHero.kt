package dev.joyfin.ui.features.home.tv

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.theme.rememberMotionSpec

@Composable
internal fun TvHomeBackdrop(item: Media?, images: ArtworkSource, collection: Boolean = false,
    content: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (content != null) content()
        else Crossfade(item, animationSpec = rememberMotionSpec(180), label = "homeSectionBackdrop") {
            if (it != null) Artwork(images.backdrop(it), images, Modifier.fillMaxSize(),
                alignment = Alignment.CenterEnd)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Palette.Background.copy(alpha = if (collection) .96f else .90f),
            .43f to Palette.Background.copy(alpha = if (collection) .84f else .44f),
            1f to Palette.Background.copy(alpha = if (collection) .71f else .02f),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Palette.Background.copy(alpha = .43f),
            .28f to Color.Transparent,
            .55f to Color.Transparent,
            1f to Palette.Background,
        )))
    }
}
