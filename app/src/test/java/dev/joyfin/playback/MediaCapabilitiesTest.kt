package dev.joyfin.playback

import dev.joyfin.data.*
import org.junit.Assert.*
import org.junit.Test

class MediaCapabilitiesTest {
    @Test
    fun audioRendererOrderDoesNotSelectAacWhenServerSelectedDtsHd() {
        val streams =
            listOf(
                MediaStream(
                    type = "Audio",
                    index = 1,
                    codec = "dts",
                    profile = "DTS-HD MA",
                    channels = 6,
                ),
                MediaStream(type = "Audio", index = 3, codec = "aac", channels = 2),
            )
        val tracks =
            listOf(
                AudioTrackMatcher.Track("audio/mp4a-latm", null, null, 2),
                AudioTrackMatcher.Track("audio/vnd.dts.hd", null, null, 6),
            )
        assertEquals(1, AudioTrackMatcher.find(1, streams, tracks))
        assertEquals(
            1,
            AudioTrackMatcher.find(
                1,
                streams,
                tracks.map {
                    if (it.mime == "audio/vnd.dts.hd") it.copy(mime = "audio/vnd.dts") else it
                },
            ),
        )
        assertEquals(0, AudioTrackMatcher.find(3, streams, tracks))
    }

    @Test
    fun subtitleRendererOrderDoesNotSelectAssWhenServerSelectedPgs() {
        val streams =
            listOf(
                MediaStream(type = "Subtitle", index = 2, codec = "ass"),
                MediaStream(type = "Subtitle", index = 5, codec = "pgssub"),
            )
        val tracks =
            listOf(
                EmbeddedSubtitleMatcher.Track("application/pgs", null, null),
                EmbeddedSubtitleMatcher.Track("text/x-ssa", null, null),
            )
        assertEquals(0, EmbeddedSubtitleMatcher.find(5, streams, tracks))
    }

    @Test
    fun originalQualityDoesNotUseTheOldEightyMbpsCeiling() {
        assertEquals(Int.MAX_VALUE, DeviceProfileDto().maxStreamingBitrate)
        assertEquals(PlaybackQuality.ORIGINAL, PlayerUiState().bitrate)
    }

    @Test
    fun bitrateLimitDoesNotInventAResolutionLimit() {
        val profile =
            DeviceProfile.create(
                setOf("h264", "hevc"),
                AudioPlaybackCapabilities(mapOf("aac" to 6)),
                4_000_000,
            )
        assertEquals(4_000_000, profile.maxStreamingBitrate)
        assertFalse(profile.codecProfiles.flatMap { it.conditions }.any { it.property == "Height" })
        assertEquals("h264,hevc", profile.transcodingProfiles.first().videoCodec)
        assertFalse(profile.directPlayProfiles.any { it.container.contains("avi") })
        assertTrue(
            profile.subtitleProfiles.any {
                it.format == "pgssub" && it.method == "Embed" && it.container == "mkv,matroska"
            }
        )
    }

    @Test
    fun extractorCodecCombinationsDoNotInheritEveryDeviceDecoder() {
        val profiles =
            ContainerPlaybackCapabilities.profiles(
                setOf("hevc", "vp9", "mpeg2video"),
                setOf("aac", "flac", "dts"),
            )
        val ts = profiles.first { it.container == "ts,mpegts,m2ts" }
        assertFalse(ts.videoCodec.contains("vp9"))
        assertFalse(ts.audioCodec.contains("flac"))
        assertTrue(ts.audioCodec.contains("dts"))
        assertFalse(profiles.any { it.container == "flv" })
        assertTrue(ContainerPlaybackCapabilities.profiles(emptySet(), setOf("aac")).isEmpty())
        assertTrue(ContainerPlaybackCapabilities.profiles(setOf("h264"), emptySet()).isEmpty())
    }

    @Test
    fun supportedAv1HasAnFmp4CopyPathRatherThanBeingLimitedToTsH264() {
        val profile =
            DeviceProfile.create(
                setOf("h264", "av1"),
                AudioPlaybackCapabilities(mapOf("aac" to 2, "flac" to 8)),
                80_000_000,
            )
        val fmp4 = profile.transcodingProfiles.first { it.container == "mp4" }
        assertTrue(fmp4.videoCodec.split(',').contains("av1"))
        assertTrue(fmp4.audioCodec.split(',').contains("flac"))
    }

    @Test
    fun highTenH264AndHevcKeepTheirProfileAndLevel() {
        assertEquals(
            "avc1.6e0033",
            VideoPlaybackCapabilities.codecString(
                MediaStream(codec = "h264", bitDepth = 10, profile = "High 10", level = 51.0)
            ),
        )
        assertEquals(
            "hvc1.2.6.L153.B0",
            VideoPlaybackCapabilities.codecString(
                MediaStream(codec = "hevc", bitDepth = 10, level = 153.0)
            ),
        )
        assertEquals(
            "avc1.64000a",
            VideoPlaybackCapabilities.codecString(
                MediaStream(codec = "h264", profile = "High", level = -99.0)
            ),
        )
    }

    @Test
    fun dolbyVisionUsesItsOwnProfileRatherThanPretendingToBePlainHevc() {
        assertEquals(
            "dvhe.05.06",
            VideoPlaybackCapabilities.codecString(
                MediaStream(codec = "hevc", dvProfile = 5, dvLevel = 6)
            ),
        )
    }

    @Test
    fun embeddedTrackOrdinalIsNotTheGlobalServerIndex() {
        val streams =
            listOf(
                MediaStream(type = "Audio", index = 1),
                MediaStream(type = "Subtitle", index = 5, codec = "pgssub"),
                MediaStream(type = "Subtitle", index = 9, codec = "subrip", external = true),
                MediaStream(type = "Subtitle", index = 12, codec = "pgssub"),
            )
        val tracks =
            listOf(
                EmbeddedSubtitleMatcher.Track("application/pgs", null, null),
                EmbeddedSubtitleMatcher.Track("application/pgs", null, null),
            )
        assertEquals(1, EmbeddedSubtitleMatcher.find(12, streams, tracks))
        assertNull(EmbeddedSubtitleMatcher.find(9, streams, tracks))
        assertNull(EmbeddedSubtitleMatcher.find(12, streams, tracks.take(1)))
    }
}
