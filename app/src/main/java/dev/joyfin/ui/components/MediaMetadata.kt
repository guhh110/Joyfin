package dev.joyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.data.runtimeLabel
import dev.joyfin.ui.theme.Palette
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaMetadata(item: Media) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item.rating?.let {
            RatingPill(it)
        }
        if (item.year > 0) {
            MediaPill(item.year.toString())
        }
        if (item.genres.isNotEmpty()) {
            MediaPill(item.genres.take(2).joinToString(" / "))
        }
        if (item.officialRating.isNotBlank()) {
            MediaPill(item.officialRating, outlined = true)
        }
        val streams = item.sources.firstOrNull()?.streams.orEmpty()
        val video = streams.firstOrNull { it.type == "Video" }
        if (video != null) {
            when {
                video.width >= 3840 || video.height >= 2160 -> QualityBadge("4K UHD")
                video.width >= 1920 || video.height >= 1080 -> QualityBadge("1080P")
            }
            when {
                video.videoRangeType.contains("DOVI", ignoreCase = true) ||
                    video.videoRangeType.contains("DV", ignoreCase = true) -> QualityBadge("Dolby Vision")
                video.videoRange.contains("HDR", ignoreCase = true) ||
                    video.videoRangeType.contains("HDR", ignoreCase = true) -> QualityBadge("HDR")
                video.videoRange.isNotBlank() && video.videoRange != "SDR" -> QualityBadge(video.videoRange)
            }
        }
        val audio = streams.firstOrNull { it.type == "Audio" }
        if (audio != null) {
            if (audio.displayTitle.contains("atmos", ignoreCase = true) ||
                audio.codec.contains("atmos", ignoreCase = true)) {
                QualityBadge("Atmos")
            } else when (audio.channels) {
                8 -> QualityBadge("7.1")
                6 -> QualityBadge("5.1")
            }
        }
        runtimeLabel(item.ticks)?.let {
            MediaPill(it, icon = Glyph.Clock)
        }
    }
}

fun has4kQuality(item: Media): Boolean {
    val video = item.sources.firstOrNull()?.streams.orEmpty().firstOrNull { it.type == "Video" }
    return video != null && (video.width >= 3840 || video.height >= 2160)
}

fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600)
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
