package dev.joyfin.playback

import dev.joyfin.data.*
import dev.joyfin.data.Media
import dev.joyfin.data.PlaybackPlan
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerUiContractTest {
    private fun plan(vararg tracks: JSONObject) =
        PlaybackPlan(
            Media("movie", "影片", "Movie"),
            MediaSource(
                streams = tracks.map { AppJson.decodeFromString<MediaStream>(it.toString()) }
            ),
            "session",
            "http://localhost/stream",
            "DirectPlay",
            0,
            null,
            -1,
            80_000_000,
            emptyList(),
        )

    @Test
    fun keepsServerTrackIndicesAndSeparatesAudioFromSubtitles() {
        val plan =
            plan(
                JSONObject()
                    .put("Type", "Audio")
                    .put("Index", 5)
                    .put("DisplayTitle", "国语")
                    .put("Codec", "aac")
                    .put("Channels", 2),
                JSONObject().put("Type", "Subtitle").put("Index", 9).put("DisplayTitle", "简体中文"),
            )
        assertEquals(listOf(5), plan.trackChoices("Audio").map { it.index })
        assertEquals("国语", plan.trackChoices("Audio").single().title)
        assertEquals("AAC · 2 声道", plan.trackChoices("Audio").single().detail)
        assertEquals(listOf(9), plan.trackChoices("Subtitle").map { it.index })
    }

    @Test
    fun usesLanguageOrNumberForMissingTrackTitles() {
        val plan =
            plan(
                JSONObject().put("Type", "Audio").put("Index", 2).put("Language", "jpn"),
                JSONObject().put("Type", "Subtitle").put("Index", 7),
            )
        assertEquals("jpn", plan.trackChoices("Audio").single().title)
        assertEquals("字幕 7", plan.trackChoices("Subtitle").single().title)
    }

    @Test
    fun noStreamsProducesAnEmptyChoiceList() {
        assertTrue(plan().trackChoices("Audio").isEmpty())
        assertTrue(plan().trackChoices("Subtitle").isEmpty())
    }

    @Test
    fun subtitleCustomizationStateAndSizesAreConfigured() {
        val state = PlayerUiState()
        assertEquals(SubtitleSize.STANDARD, state.subtitleSize)
        assertFalse(state.subtitleBackground)

        assertEquals(1.0f, SubtitleSize.STANDARD.scale, 0.001f)
        assertEquals(1.2f, SubtitleSize.LARGE.scale, 0.001f)
        assertEquals(1.4f, SubtitleSize.EXTRA_LARGE.scale, 0.001f)
        assertEquals(0.8f, SubtitleSize.COMPACT.scale, 0.001f)

        val largeState = state.copy(subtitleSize = SubtitleSize.LARGE, subtitleBackground = true)
        assertEquals(SubtitleSize.LARGE, largeState.subtitleSize)
        assertTrue(largeState.subtitleBackground)
    }
}

