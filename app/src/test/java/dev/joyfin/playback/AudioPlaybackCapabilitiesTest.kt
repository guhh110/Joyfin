package dev.joyfin.playback

import dev.joyfin.data.DeviceProfileDto
import dev.joyfin.data.MediaSource
import dev.joyfin.data.MediaStream
import org.junit.Assert.*
import org.junit.Test

class AudioPlaybackCapabilitiesTest {
    @Test
    fun selectedAudioSampleRateWinsOverFirstTrackAndAliasesAgree() {
        val source =
            MediaSource(
                streams =
                    listOf(
                        MediaStream(type = "Audio", index = 1, codec = "dts", sampleRate = 48_000),
                        MediaStream(type = "Audio", index = 4, codec = "dts", sampleRate = 96_000),
                    )
            )
        val rates = mutableSetOf<Int>()
        val capabilities =
            AudioPlaybackCapabilities.detectWithProbe(source, 4) { mime, rate, channels ->
                if (mime == "audio/vnd.dts") rates += rate
                mime == "audio/vnd.dts" && rate == 96_000 && channels <= 6
            }
        assertEquals(setOf(96_000), rates)
        assertEquals(6, capabilities.channelsByCodec["dts"])
        assertEquals(6, capabilities.channelsByCodec["dca"])
    }

    @Test
    fun supportedDtsAndHdAliasesAreAdvertisedWithTheirChannelLimits() {
        val audio =
            AudioPlaybackCapabilities.detectWithProbe(null) { mime, _, channels ->
                mime in listOf("audio/vnd.dts", "audio/vnd.dts.hd") && channels <= 6
            }
        listOf("dts", "dca", "dtshd", "dts_hd").forEach {
            assertEquals(6, audio.channelsByCodec[it])
        }
        assertFalse(audio.channelsByCodec.containsKey("truehd"))
    }

    @Test
    fun absentDecoderAndPassthroughDoNotAdvertiseDts() {
        val audio = AudioPlaybackCapabilities.detectWithProbe(null) { _, _, _ -> false }
        assertEquals(setOf("pcm", "lpcm"), audio.channelsByCodec.keys)
    }

    @Test
    fun capabilityProbeUsesSourceSampleRate() {
        val source =
            MediaSource(
                streams =
                    listOf(
                        MediaStream(
                            type = "Audio",
                            codec = "dts",
                            sampleRate = 96_000,
                            channels = 6,
                        )
                    )
            )
        val audio =
            AudioPlaybackCapabilities.detectWithProbe(source) { mime, rate, channels ->
                mime == "audio/vnd.dts" && rate == 96_000 && channels <= 6
            }
        assertEquals(6, audio.channelsByCodec["dts"])
    }

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
