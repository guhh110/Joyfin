package dev.joyfin.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class JsonSerializationTest {
    @Test
    fun nullableServerFieldsAndNewFieldsKeepDefaults() {
        val item =
            AppJson.decodeFromString<ItemDto>(
                """{"Id":"episode","Name":null,"Overview":null,"UserData":null,"MediaSources":null,"People":null,"ProductionYear":null,"UnknownFutureField":{"nested":true}}"""
            )
        val media = Media.from(item)
        assertEquals("未命名", media.name)
        assertEquals("", media.overview)
        assertEquals(0L, media.resumeTicks)
        assertEquals(0, media.year)
        assertTrue(media.sources.isEmpty())
        assertTrue(media.people.isEmpty())
    }

    @Test
    fun missingAuthenticationFieldsFailInsteadOfCreatingAnInvalidSession() {
        for (json in listOf("{}", """{"AccessToken":"token","User":{}}""")) {
            assertThrows(SerializationException::class.java) {
                AppJson.decodeFromString<LoginResponse>(json)
            }
        }
    }

    @Test
    fun wirePayloadKeepsPascalCaseAndOmitsUnselectedTracks() {
        val request =
            PlaybackRequest(
                "user",
                DeviceProfileDto(),
                123L,
                4_000_000,
                6,
                true,
                true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                subtitleStreamIndex = -1,
                autoOpenLiveStream = true,
            )
        val wire = AppJson.encodeToString(request)
        assertTrue(wire.contains("\"StartTimeTicks\":123"))
        assertTrue(wire.contains("\"EnableTranscoding\":true"))
        assertTrue(wire.contains("\"MaxAudioChannels\":6"))
        assertFalse(wire.contains("AudioStreamIndex"))
        assertFalse(wire.contains("MediaSourceId"))
        assertEquals(request, AppJson.decodeFromString<PlaybackRequest>(wire))
    }

    @Test
    fun encryptedSessionPayloadFormatRemainsReadable() {
        val old =
            """{"server":"https:\/\/example.test\/jellyfin","token":"test","userId":"user","userName":"Viewer"}"""
        val session = AppJson.decodeFromString<Session>(old)
        assertEquals("https://example.test/jellyfin", session.server)
        assertEquals(session, AppJson.decodeFromString<Session>(AppJson.encodeToString(session)))
    }

    @Test
    fun seriesPreferenceHashMatchesAndroidLegacyWriter() {
        assertEquals(
            "75b9950e16097c89c270794931fab2d3f043de0497efc7e03e0c6cee67551103",
            PlaybackPreferenceStore.key(
                Session("https://example.test/jellyfin/", "token", "user", "Viewer"),
                Media("episode", "Episode", "Episode", seriesId = "show1"),
            ),
        )
    }
}
