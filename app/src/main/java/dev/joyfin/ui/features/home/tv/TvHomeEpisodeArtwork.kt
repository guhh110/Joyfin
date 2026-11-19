package dev.joyfin.ui.features.home.tv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.size.Size
import coil.transform.Transformation
import dev.joyfin.data.Media
import dev.joyfin.ui.components.rememberArtworkRequest
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
internal fun TvHomeEpisodeArtwork(item: Media, images: ArtworkSource, unit: Dp,
    modifier: Modifier = Modifier) {
    val stillUrl = remember(item, images) { images.episode(item)?.takeIf(String::isNotBlank) }
    val stillRequest = rememberArtworkRequest(stillUrl, images)
    var stillFailed by remember(stillRequest) { mutableStateOf(stillUrl == null) }
    Box(modifier.background(Brush.linearGradient(listOf(
        Color(0xFF29343E), Palette.Surface, Color(0xFF141C24)))), contentAlignment = Alignment.Center) {
        if (stillFailed) {
            val posterUrl = remember(item, images) {
                item.seriesId.takeIf(String::isNotBlank)?.let { seriesId ->
                    images.poster(Media(seriesId, item.seriesName, "Series",
                        imageId = seriesId, imageTag = item.seriesImageTag)).takeIf(String::isNotBlank)
                }
            }
            val posterBase = rememberArtworkRequest(posterUrl, images)
            val posterRequest = remember(posterBase) {
                posterBase.newBuilder().size(128, 192)
                    .placeholderMemoryCacheKey(null as String?)
                    .transformations(TvHomeEpisodePosterSoftening).build()
            }
            var posterLoaded by remember(posterRequest) { mutableStateOf(false) }
            if (posterUrl != null) AsyncImage(posterRequest, contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag("home:episode-poster:${item.id}"),
                contentScale = ContentScale.Crop,
                alpha = .5f,
                onSuccess = { posterLoaded = true },
                onError = { posterLoaded = false })
            if (!posterLoaded) Box(Modifier.fillMaxSize().testTag("home:episode-empty:${item.id}"))
            Text("暂无剧照", Modifier.testTag("home:episode-placeholder:${item.id}"),
                color = Palette.Text.copy(alpha = .38f), fontFamily = FontFamily.SansSerif,
                fontSize = (unit.value * 20).sp, maxLines = 1)
        } else {
            key(stillRequest) {
                AsyncImage(stillRequest, contentDescription = "第 ${item.episode} 集剧照",
                    modifier = Modifier.fillMaxSize().testTag("home:episode-still:${item.id}"),
                    contentScale = ContentScale.Crop,
                    onError = { stillFailed = true })
            }
        }
    }
}

private object TvHomeEpisodePosterSoftening : Transformation {
    override val cacheKey = "tv-home-episode-poster-softening-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scale = minOf(1f, 24f / maxOf(input.width, input.height))
        val width = (input.width * scale).roundToInt().coerceAtLeast(1)
        val height = (input.height * scale).roundToInt().coerceAtLeast(1)
        val sampled = Bitmap.createScaledBitmap(input, width, height, true)
        return Bitmap.createScaledBitmap(sampled, input.width, input.height, true)
    }
}
