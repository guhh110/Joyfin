package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.joyfin.data.Media
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

/** A real episode still, or an explicit placeholder. Never uses inherited artwork. */
@Composable
fun EpisodeArtwork(
    episode: Media,
    images: ArtworkSource,
    modifier: Modifier = Modifier,
    imageRatio: Float = 16f / 9f,
) {
    val url = remember(episode, images) { images.episode(episode) }
    val request = rememberArtworkRequest(url, images)
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(url == null) }
    Box(
        modifier.aspectRatio(imageRatio).clip(RoundedCornerShape(6.dp)).background(Palette.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded)
            Column(
                Modifier.testTag("episode-placeholder:${episode.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Caption("第 ${episode.episode} 集")
                if (failed) Caption("暂无剧照")
            }
        if (url != null)
            key(url) {
                AsyncImage(
                    model = request,
                    contentDescription = "第 ${episode.episode} 集剧照",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    onError = {
                        failed = true
                        loaded = false
                    },
                )
            }
    }
}
