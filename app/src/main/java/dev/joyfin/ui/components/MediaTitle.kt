package dev.joyfin.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import dev.joyfin.data.Media
import dev.joyfin.ui.model.ArtworkSource

/** One detail-page title: original transparent artwork, with readable text until it is available. */
@Composable
fun MediaTitle(
    media: Media,
    images: ArtworkSource,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    maxLines: Int = 2,
    logoWidth: Dp = 320.dp,
    logoHeight: Dp = 80.dp,
) {
    val url = images.logo(media)?.takeIf { it.isNotBlank() }
    if (url == null) {
        FilmTitle(media.name, modifier, large = large, maxLines = maxLines)
        return
    }
    SubcomposeAsyncImage(
        model = rememberArtworkRequest(url, images),
        contentDescription = media.name,
        modifier = modifier,
        alignment = Alignment.CenterStart,
        contentScale = ContentScale.Fit,
        loading = { FilmTitle(media.name, large = large, maxLines = maxLines) },
        error = { FilmTitle(media.name, large = large, maxLines = maxLines) },
        success = {
            SubcomposeAsyncImageContent(
                modifier = Modifier.widthIn(max = logoWidth).fillMaxWidth().height(logoHeight)
                    .semantics { heading() },
            )
        },
    )
}
