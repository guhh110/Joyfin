@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import dev.joyfin.data.subtitles.LocalSubtitleFile
import dev.joyfin.data.subtitles.LocalSubtitleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local subtitle preparation only. No subtitle site or server API dependency. */
data class PreparedLocalSubtitle(
    val file: LocalSubtitleFile,
    val offsetMs: Long,
    val configuration: MediaItem.SubtitleConfiguration,
)
class LocalSubtitlePlayback(private val store: LocalSubtitleStore) {
    suspend fun selected(key: String): PreparedLocalSubtitle? = withContext(Dispatchers.IO) {
        store.choice(key)?.let { (file, offset) -> prepare(file, offset) }
    }
    suspend fun shifted(selected: PreparedLocalSubtitle, offset: Long): PreparedLocalSubtitle = withContext(Dispatchers.IO) {
        require(offset in -60_000..60_000)
        prepare(selected.file, offset)
    }
    private fun prepare(file: LocalSubtitleFile, offset: Long): PreparedLocalSubtitle {
        val local = store.playbackFile(file, offset)
        val mime = when (local.extension) {
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            else -> error("不支持的字幕格式")
        }
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(local))
            .setId(TRACK_ID).setMimeType(mime).setLanguage(file.language).setLabel(file.name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        return PreparedLocalSubtitle(file, offset, config)
    }
    companion object {
        const val TRACK_ID = "joyfin-local-subtitle"
        fun matches(id: String?): Boolean = id?.substringAfterLast(':') == TRACK_ID
    }
}
