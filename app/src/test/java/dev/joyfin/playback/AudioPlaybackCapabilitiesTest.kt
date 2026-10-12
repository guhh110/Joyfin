package dev.joyfin.playback

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AudioPlaybackCapabilitiesTest {
    @Test
    fun multichannelDecoderIsNotLimitedByStereoSpeakers() {
        val audio = AudioPlaybackCapabilities(mapOf("aac" to 2, "eac3" to 6))
        val profile = JSONObject().put("CodecProfiles", audio.codecProfiles())
        assertEquals(6, AudioPlaybackCapabilities.requestChannelLimit(profile))
        assertEquals("aac,eac3", audio.codecs)
        assertFalse(audio.codecs.contains("truehd"))
        assertEquals(
            "2",
            audio
                .codecProfiles()
                .getJSONObject(0)
                .getJSONArray("Conditions")
                .getJSONObject(0)
                .getString("Value"),
        )
    }

    @Test
    fun stereoOnlyProfileRemainsStereo() {
        val audio = AudioPlaybackCapabilities(mapOf("aac" to 2))
        assertEquals(
            2,
            AudioPlaybackCapabilities.requestChannelLimit(
                JSONObject().put("CodecProfiles", audio.codecProfiles())
            ),
        )
    }
}
