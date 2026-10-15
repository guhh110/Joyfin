package dev.joyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.ui.theme.Palette
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaMetadata(item: Media) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item.rating?.let {
            Caption("★ ${String.format(Locale.ROOT,"%.1f",it)}", color = Palette.Text)
        }
        if (item.year > 0) Caption(item.year.toString())
        if (item.genres.isNotEmpty()) Caption(item.genres.take(2).joinToString(" / "))
        if (item.officialRating.isNotBlank()) Caption(item.officialRating)
        val video =
            item.sources.firstOrNull()?.streams.orEmpty().firstOrNull {
                it.type == "Video"
            }
        if (video != null) {
            if (video.width >= 3840) Caption("4K")
            video
                .videoRange
                .takeIf { it.isNotBlank() && it != "SDR" }
                ?.let { Caption(it) }
        }
        if (item.ticks > 0) Caption("${item.ticks/600_000_000} 分钟")
    }
}

fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600)
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
