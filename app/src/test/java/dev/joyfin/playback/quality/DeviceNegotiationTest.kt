package dev.joyfin.playback.quality

import dev.joyfin.data.AudioOutputMode
import dev.joyfin.data.PlaybackQuality
import dev.joyfin.playback.DeviceProfile
import dev.joyfin.playback.quality.QualityFixtures.allPaths
import dev.joyfin.playback.quality.QualityFixtures.capabilities
import dev.joyfin.playback.quality.QualityFixtures.decision
import dev.joyfin.playback.quality.QualityFixtures.hdrVideo
import dev.joyfin.playback.quality.QualityFixtures.requirements
import dev.joyfin.playback.quality.QualityFixtures.source
import org.junit.Assert.*
import org.junit.Test

class DeviceNegotiationTest {
    @Test fun copiedHdrNegotiationDeclaresOnlyKnownRangesAndRetainsBitrateCap() {
        val source = source()
        val caps = capabilities()
        val result = DeviceProfile.negotiate(caps, decision(source, caps), source, null, 12_000_000)
        val ranges = result.profile.codecProfiles.single { it.type == "Video" }.conditions.single()
        assertEquals("EqualsAny", ranges.condition)
        assertEquals("VideoRangeType", ranges.property)
        assertEquals(setOf("Unknown", "SDR", "HDR10"), ranges.value.split('|').toSet())
        assertEquals(12_000_000, result.profile.maxStreamingBitrate)
        assertFalse(result.requestPolicy.videoTranscode)
        assertFalse(result.requestPolicy.audioTranscode)
        assertNull(result.requestPolicy.maxAudioChannels)
    }

    @Test fun videoConversionDeclaresSdrRatherThanAllHdrAndDolbyRanges() {
        val source = source()
        val caps = capabilities()
        val selected = decision(source, caps, requirements(forceVideo = true))
        val result = DeviceProfile.negotiate(caps, selected, source, null, PlaybackQuality.ORIGINAL)
        assertTrue(result.requestPolicy.videoTranscode)
        assertFalse(result.requestPolicy.audioTranscode)
        assertEquals("SDR", result.profile.codecProfiles.single { it.type == "Video" }.conditions.single().value)
    }

    @Test fun pcmConversionTargetsAreLimitedByOutputAndExcludeEncodedOnlyCodecs() {
        val source = source()
        val caps = capabilities(mapOf("dts" to allPaths.copy(platformChannels = 0, softwareChannels = 0),
            "aac" to allPaths.copy(passthroughChannels = 0)), pcmChannels = 2)
        val result = DeviceProfile.negotiate(caps,
            decision(source, caps, requirements(mode = AudioOutputMode.PCM)), source, null, PlaybackQuality.ORIGINAL)
        assertEquals(2, result.requestPolicy.maxAudioChannels)
        assertTrue(result.profile.transcodingProfiles.all { it.maxAudioChannels == "2" })
        assertTrue(result.profile.directPlayProfiles.none { "dts" in it.audioCodec.split(',') })
        assertTrue(result.profile.codecProfiles.none { it.codec == "dts" })
        assertFalse(result.requestPolicy.videoTranscode)
    }

    @Test fun encodedMultichannelTargetIsRetainedForAudioOnlyServerFallback() {
        val source = source()
        val caps = capabilities(mapOf("dts" to allPaths, "eac3" to allPaths, "aac" to allPaths.copy(passthroughChannels = 0)),
            pcmChannels = 2)
        val result = DeviceProfile.negotiate(caps, decision(source, caps, requirements(forceAudio = true)), source, null, PlaybackQuality.ORIGINAL)
        assertEquals(8, result.requestPolicy.maxAudioChannels)
        assertEquals("eac3", result.profile.transcodingProfiles.first().audioCodec.split(',').first())
        assertTrue(result.requestPolicy.audioTranscode)
        assertFalse(result.requestPolicy.videoTranscode)
        assertTrue(result.profile.directPlayProfiles.none { "dts" in it.audioCodec.split(',') })
    }

    @Test fun audioProfileIncludesProbedRateAndChannelConstraintsOnWire() {
        val source = source(sampleRate = 96_000)
        val caps = capabilities(mapOf("dts" to allPaths.copy(sampleRate = 96_000, passthroughChannels = 6,
            platformChannels = 2, softwareChannels = 0)))
        val profile = DeviceProfile.negotiate(caps, decision(source, caps), source, null, PlaybackQuality.ORIGINAL).profile
        val constraints = profile.codecProfiles.single { it.codec == "dts" }.conditions
        assertEquals("6", constraints.single { it.property == "AudioChannels" }.value)
        assertEquals("96000", constraints.single { it.property == "AudioSampleRate" }.value)
        assertEquals("Equals", constraints.single { it.property == "AudioSampleRate" }.condition)
    }

    @Test fun isoBdmvAndUnsupportedContainerAudioCombinationsAreNeverAdvertised() {
        val source = source().copy(container = "iso")
        val caps = capabilities(mapOf("truehd" to allPaths, "dts" to allPaths, "aac" to allPaths))
        val result = DeviceProfile.negotiate(caps, decision(source, caps), source, null, PlaybackQuality.ORIGINAL)
        assertTrue(result.requestPolicy.discSource)
        val profiles = result.profile.directPlayProfiles
        assertFalse(profiles.any { it.container.split(',').any { container -> container in setOf("iso", "bdmv", "bluray") } })
        assertFalse(profiles.single { "ts" in it.container.split(',') }.audioCodec.split(',').contains("truehd"))
        assertTrue(profiles.single { "mkv" in it.container.split(',') }.audioCodec.split(',').contains("truehd"))
    }

    @Test fun missingRangeEvidenceDoesNotAdvertiseUnboundedHdr() {
        val source = source()
        val caps = capabilities(video = hdrVideo.copy(acceptedRanges = emptySet()))
        val profile = DeviceProfile.negotiate(caps, decision(source, caps), source, null, PlaybackQuality.ORIGINAL).profile
        assertEquals(setOf("Unknown", "SDR"), profile.codecProfiles.single { it.type == "Video" }
            .conditions.single().value.split('|').toSet())
    }
}
