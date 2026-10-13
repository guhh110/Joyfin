package dev.joyfin.playback

import dev.joyfin.data.DeviceProfileDto
import org.junit.Assert.*
import org.junit.Test

class AudioPlaybackCapabilitiesTest {
    @Test
    fun multichannelDecoderIsNotLimitedByStereoSpeakers() {
        val audio = AudioPlaybackCapabilities(mapOf("aac" to 2, "eac3" to 6))
        val profile = DeviceProfileDto(codecProfiles = audio.codecProfiles())
        assertEquals(6, AudioPlaybackCapabilities.requestChannelLimit(profile))
        assertEquals("aac,eac3", audio.codecs)
        assertFalse(audio.codecs.contains("truehd"))
        assertEquals("2", audio.codecProfiles().first().conditions.first().value)
    }

    @Test
    fun stereoOnlyProfileRemainsStereo() {
        val audio = AudioPlaybackCapabilities(mapOf("aac" to 2))
        assertEquals(
            2,
            AudioPlaybackCapabilities.requestChannelLimit(
                DeviceProfileDto(codecProfiles = audio.codecProfiles())
            ),
        )
    }
}
