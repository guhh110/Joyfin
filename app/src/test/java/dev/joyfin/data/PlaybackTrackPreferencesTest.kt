package dev.joyfin.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackTrackPreferencesTest {
    private fun track(
        index: Int,
        language: String = "eng",
        type: String = "Subtitle",
        extra: String = "",
    ): JSONObject =
        JSONObject("""{"Index":$index,"Language":"$language","Type":"$type","Codec":"ass"$extra}""")

    @Test
    fun matchesLanguageAcrossIndicesAndAliases() {
        val preference = PlaybackTrackPreferences().selected("Subtitle", 7, listOf(track(7)))
        assertEquals(19, preference.subtitleIndex(listOf(track(7, "jpn"), track(19, "en"))))
    }

    @Test
    fun missingLanguageFallsBackWithoutReplacingRememberedChoice() {
        val preference = PlaybackTrackPreferences().selected("Subtitle", 7, listOf(track(7)))
        assertNull(preference.subtitleIndex(listOf(track(7, "jpn"))))
        assertEquals(9, preference.subtitleIndex(listOf(track(9))))
    }

    @Test
    fun offPersistsAndSelectingSubtitleReenablesIt() {
        val off = PlaybackTrackPreferences().selected("Subtitle", -1, emptyList())
        assertEquals(
            -1,
            PlaybackTrackPreferences.fromJson(JSONObject(off.toJson().toString()))
                .subtitleIndex(emptyList()),
        )
        assertEquals(
            3,
            off.selected("Subtitle", 3, listOf(track(3))).subtitleIndex(listOf(track(3))),
        )
    }

    @Test
    fun forcedHearingImpairedAndCommentaryAreDistinct() {
        val streams =
            listOf(
                track(1, extra = ",\"IsForced\":true"),
                track(2, extra = ",\"IsHearingImpaired\":true"),
                track(3, extra = ",\"Title\":\"Commentary\""),
                track(4),
            )
        val preference = PlaybackTrackPreferences().selected("Subtitle", 4, streams)
        assertEquals(4, preference.subtitleIndex(streams))
        assertNull(preference.subtitleIndex(streams.take(3)))
    }

    @Test
    fun simplifiedAndTraditionalDoNotCrossMatch() {
        val preference =
            PlaybackTrackPreferences()
                .selected("Subtitle", 1, listOf(track(1, "chi", extra = ",\"Title\":\"简体\"")))
        assertNull(preference.subtitleIndex(listOf(track(2, "zho", extra = ",\"Title\":\"繁體\""))))
        assertEquals(
            3,
            preference.subtitleIndex(listOf(track(3, "zh", extra = ",\"Title\":\"简体\""))),
        )
    }

    @Test
    fun chineseScriptTagMatchesExplicitScriptTitle() {
        val preference =
            PlaybackTrackPreferences().selected("Subtitle", 1, listOf(track(1, "zh_Hans")))
        assertEquals(
            3,
            preference.subtitleIndex(listOf(track(3, "chi", extra = ",\"Title\":\"简体\""))),
        )
        assertNull(preference.subtitleIndex(listOf(track(4, "zh-Hant"))))
    }

    @Test
    fun unknownLanguageDoesNotSaveAnIndexOrKeepAnOldChoice() {
        val preference = PlaybackTrackPreferences().selected("Subtitle", 1, listOf(track(1)))
        assertNull(preference.selected("Subtitle", 2, listOf(track(2, "und"))).subtitle)
        assertNull(preference.selected("Subtitle", 2, listOf(track(2, ""))).subtitle)
    }

    @Test
    fun audioAndSubtitleChoicesAreIndependentAndRoundTrip() {
        val streams = listOf(track(4, "jpn", "Audio"), track(8, "eng"))
        val preference =
            PlaybackTrackPreferences()
                .selected("Audio", 4, streams)
                .selected("Subtitle", 8, streams)
        val restored = PlaybackTrackPreferences.fromJson(JSONObject(preference.toJson().toString()))
        assertEquals(preference, restored)
        assertEquals(4, restored.audio?.match("Audio", streams))
        assertEquals(8, restored.subtitleIndex(streams))
    }

    @Test
    fun codecIsOnlyATieBreaker() {
        val preference = PlaybackTrackPreferences().selected("Subtitle", 1, listOf(track(1)))
        val srt = track(2).put("Codec", "srt")
        assertEquals(3, preference.subtitleIndex(listOf(srt, track(3))))
        assertEquals(2, preference.subtitleIndex(listOf(srt)))
    }

    @Test
    fun scopeSeparatesServerUserAndMovieButGroupsEpisodes() {
        val session = Session("https://example.test", "token", "user", "Viewer")
        val episode = Media("ep1", "First", "Episode", seriesId = "show1")
        val key = PlaybackPreferenceStore.key(session, episode)
        assertEquals(
            key,
            PlaybackPreferenceStore.key(session.copy(token = "changed"), episode.copy(id = "ep2")),
        )
        assertNotEquals(key, PlaybackPreferenceStore.key(session.copy(userId = "other"), episode))
        assertNotEquals(
            key,
            PlaybackPreferenceStore.key(session.copy(server = "https://other.test"), episode),
        )
        assertNotEquals(key, PlaybackPreferenceStore.key(session, episode.copy(seriesId = "show2")))
        assertNotEquals(
            PlaybackPreferenceStore.key(session, episode.copy(type = "Movie")),
            PlaybackPreferenceStore.key(session, episode.copy(type = "Movie", id = "ep2")),
        )
    }
}
