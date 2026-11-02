package dev.joyfin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaChapter(@SerialName("StartPositionTicks") val ticks: Long = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("ImageTag") val imageTag: String = "")

@Serializable
data class MediaSegment(@SerialName("ItemId") val itemId: String = "",
    @SerialName("Type") val type: String = "Unknown",
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0)

@Serializable
internal data class MediaSegmentsResponse(@SerialName("Items") val items: List<MediaSegment> = emptyList())

@Serializable
data class SkipPreferences(
    val introSeconds: Int = 0,
    val outroSeconds: Int = 0,
    val automatic: Boolean = false,
    // Explicit player marks take precedence over server markers. Legacy durations remain fallbacks.
    val introEndMs: Long? = null,
    val outroDurationMs: Long? = null,
) {
    val introDurationMs: Long get() = introEndMs ?: introSeconds * 1000L
    val effectiveOutroDurationMs: Long get() = outroDurationMs ?: outroSeconds * 1000L

    fun validated() = copy(
        introSeconds = introSeconds.coerceIn(0, 600),
        outroSeconds = outroSeconds.coerceIn(0, 600),
        introEndMs = introEndMs?.takeIf { it > 0 },
        outroDurationMs = outroDurationMs?.takeIf { it > 0 },
    )
}
