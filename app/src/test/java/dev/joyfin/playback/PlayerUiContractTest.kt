package dev.joyfin.playback

import dev.joyfin.data.Media
import dev.joyfin.data.PlaybackPlan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerUiContractTest {
    private fun plan(vararg tracks: JSONObject) =
        PlaybackPlan(
            Media("movie", "影片", "Movie"),
            JSONObject().put("MediaStreams", JSONArray(tracks.toList())),
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
}
