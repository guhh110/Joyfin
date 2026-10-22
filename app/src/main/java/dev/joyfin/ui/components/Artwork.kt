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
import dev.joyfin.artwork.ArtworkCacheIdentity
import dev.joyfin.artwork.ArtworkCacheDiagnostics
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
    val request = rememberArtworkRequest(url, images)
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

/** Shared authentication and request identity; Coil owns fetching, decoding and caches. */
@Composable
internal fun rememberArtworkRequest(url: String?, images: ArtworkSource): ImageRequest {
    val context = LocalContext.current
    return remember(context, url, images.authorization, images.cacheNamespace) {
        val cacheKey = url?.takeIf { it.isNotBlank() }?.let {
            ArtworkCacheIdentity.key(images.cacheNamespace.ifBlank { images.authorization }, it)
        }
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("Authorization", images.authorization)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .placeholderMemoryCacheKey(cacheKey)
            .crossfade(false)
            .listener(
                onSuccess = { _, result -> ArtworkCacheDiagnostics.success(result.dataSource,
                    url?.startsWith("https://", true) == true || url?.startsWith("http://", true) == true) },
                onError = { _, _ -> if (!url.isNullOrBlank()) ArtworkCacheDiagnostics.failure() },
            )
            .build()
    }
}
