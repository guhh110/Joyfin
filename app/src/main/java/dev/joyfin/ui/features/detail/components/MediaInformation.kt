package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.joyfin.data.Media
import dev.joyfin.data.objects
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberScreenFocus

@Composable
fun MediaInformation(item: Media, onDismiss: () -> Unit, onPlay: (String?) -> Unit) {
    RememberScreenFocus("sheet:close") {
        Sheet("媒体信息与版本", onDismiss) { MediaInformationContent(item, onPlay = onPlay) }
    }
}

@Composable
fun MediaInformationContent(
    item: Media,
    playLabel: (String?) -> String = { "播放此版本" },
    canPlay: (String?) -> Boolean = { true },
    isPreparing: (String?) -> Boolean = { false },
    onPlay: (String?) -> Unit,
) {
    Caption(
        listOf(item.locations.joinToString(" / "), item.language, item.premiereDate)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    )
    if (item.sources.isEmpty()) Caption("服务器暂无媒体源信息")
    item.sources.forEach { source ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val video =
                source.optJSONArray("MediaStreams").objects().firstOrNull {
                    it.optString("Type") == "Video"
                }
            SectionTitle(
                source.optString("Name").ifBlank { source.optString("Container").uppercase() }
            )
            Caption(
                listOfNotNull(
                        video?.optString("Codec"),
                        video
                            ?.optInt("Width")
                            ?.takeIf { it > 0 }
                            ?.let { "${it}×${video.optInt("Height")}" },
                        source.optLong("Bitrate").takeIf { it > 0 }?.let { "${it/1_000_000} Mbps" },
                    )
                    .joinToString(" · ")
            )
            source
                .optLong("Size")
                .takeIf { it > 0 }
                ?.let { bytes ->
                    Caption(
                        java.lang.String.format(
                            java.util.Locale.ROOT,
                            "文件大小 · %.1f GiB",
                            bytes / 1_073_741_824.0,
                        )
                    )
                }
            source
                .optJSONArray("MediaStreams")
                .objects()
                .filter { it.optString("Type") in setOf("Audio", "Subtitle") }
                .forEach { stream ->
                    val kind = if (stream.optString("Type") == "Audio") "音轨" else "字幕"
                    val name =
                        stream.optString("DisplayTitle").ifBlank {
                            listOf(
                                    stream.optString("Language"),
                                    stream.optString("Codec").uppercase(),
                                )
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        }
                    if (name.isNotBlank()) Caption("$kind · $name")
                }
            if (item.type in listOf("Movie", "Episode", "Video")) {
                val sourceId = source.optString("Id").takeIf { it.isNotBlank() }
                ActionButton(
                    playLabel(sourceId),
                    { onPlay(sourceId) },
                    enabled = canPlay(sourceId),
                    icon = Glyph.Play,
                    loading = isPreparing(sourceId),
                    enabledWhileLoading = true,
                    loadingLabel = "正在准备，点击取消",
                )
            }
        }
    }
}
