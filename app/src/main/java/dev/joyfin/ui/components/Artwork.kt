package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

@Composable
fun Artwork(
    url: String?,
    images: ArtworkSource,
    modifier: Modifier = Modifier,
    fallback: String = "",
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val request =
        remember(context, url, images.authorization) {
            ImageRequest.Builder(context)
                .data(url)
                .addHeader("Authorization", images.authorization)
                .build()
        }
    Box(modifier.background(Palette.Surface), contentAlignment = Alignment.Center) {
        if (fallback.isNotBlank()) Caption(fallback.take(16))
        if (!url.isNullOrBlank())
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
            )
    }
}
