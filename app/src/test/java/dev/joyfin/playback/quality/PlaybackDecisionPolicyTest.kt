package dev.joyfin.playback.quality

import dev.joyfin.data.AudioOutputMode
import dev.joyfin.data.MediaStream
import dev.joyfin.playback.AudioCodecCapabilities
import dev.joyfin.playback.quality.QualityFixtures.allPaths
import dev.joyfin.playback.quality.QualityFixtures.capabilities
import dev.joyfin.playback.quality.QualityFixtures.decision
import dev.joyfin.playback.quality.QualityFixtures.hdrVideo
import dev.joyfin.playback.quality.QualityFixtures.requirements
import dev.joyfin.playback.quality.QualityFixtures.source
import org.junit.Assert.*
import org.junit.Test

class PlaybackDecisionPolicyTest {
    @Test fun autoPreservesEncodedAudioAndHdrWhenAllPathsAreAvailable() {
        val result = decision()
        assertEquals(AudioPath.PASSTHROUGH, result.audioPath)
        assertEquals(VideoPath.NATIVE, result.videoPath)
        assertEquals(VideoRange.HDR10, result.targetRange)
        assertFalse(result.pcmOnly)
        assertFalse(result.audioTranscode)
        assertFalse(result.videoTranscode)
        assertTrue(result.reasons.isEmpty())
    }

    @Test fun pcmModeRejectsPassthroughEvenWhenReceiverSupportsIt() {
        val result = decision(requirements = requirements(mode = AudioOutputMode.PCM))
        assertEquals(AudioPath.PLATFORM_PCM, result.audioPath)
        assertTrue(result.pcmOnly)
        assertFalse(result.audioTranscode)
        assertFalse(result.videoTranscode)
        assertEquals(AudioOutputMode.PCM, result.outputMode)
    }

    @Test fun stereoOutputDoesNotForceServerConversionOfLocallyDecodableSurround() {
        val caps = capabilities(mapOf("dts" to AudioCodecCapabilities(96_000, 0, 6, 8)), pcmChannels = 2)
        val result = decision(source(sampleRate = 96_000), caps)
        assertEquals(AudioPath.PLATFORM_PCM, result.audioPath)
        assertEquals(2, result.pcmOutputChannels)
        assertEquals(96_000, result.audioSupport!!.sampleRate)
        assertFalse(result.audioTranscode)
    }

    @Test fun softwareDecoderIsUsedWhenPlatformCannotDecodeTheChannelCount() {
        val result = decision(capabilities = capabilities(mapOf("dts" to AudioCodecCapabilities(48_000, 2, 2, 8))))
        assertEquals(AudioPath.SOFTWARE_PCM, result.audioPath)
        assertTrue(result.pcmOnly)
        assertFalse(result.audioTranscode)
    }

    @Test fun unsupportedAudioConvertsOnlyAudioAndRetainsHdr() {
        val result = decision(capabilities = capabilities(emptyMap()))
        assertEquals(AudioPath.SERVER, result.audioPath)
        assertTrue(result.audioTranscode)
        assertFalse(result.videoTranscode)
        assertEquals(VideoRange.HDR10, result.targetRange)
        assertTrue(result.reasons.any { it.contains("优先保留原视频") })
    }

    @Test fun insufficientChannelsDoNotCountAsSupport() {
        val result = decision(capabilities = capabilities(mapOf("dts" to AudioCodecCapabilities(48_000, 2, 2, 2))))
        assertEquals(AudioPath.SERVER, result.audioPath)
    }

    @Test fun nonDefaultSelectedAudioDeterminesSupportRatherThanFirstTrack() {
        val source = source().let { it.copy(defaultAudioIndex = 3,
            streams = it.streams + MediaStream(type = "Audio", index = 3, codec = "aac", channels = 2)) }
        assertEquals(AudioPath.SERVER, decision(source).audioPath)
        assertEquals(AudioPath.PASSTHROUGH, decision(source, audioIndex = 1).audioPath)
        assertEquals(AudioPath.SERVER, decision(source, audioIndex = 3).audioPath)
    }

    @Test fun silentVideoDoesNotRequestAudioConversion() {
        val source = source().let { it.copy(streams = it.streams.filter { track -> track.type != "Audio" }) }
        val result = decision(source, requirements = requirements(compatible = true))
        assertEquals(AudioPath.NONE, result.audioPath)
        assertFalse(result.audioTranscode)
    }

    @Test fun explicitCompatibilityConvertsVideoToSdrAndAudioOnServer() {
        val result = decision(requirements = requirements(compatible = true))
        assertEquals(AudioPath.SERVER, result.audioPath)
        assertEquals(VideoPath.TRANSCODE, result.videoPath)
        assertEquals(VideoRange.SDR, result.targetRange)
        assertTrue(result.videoTranscode)
    }

    @Test fun speedChangesRequirePcmButNormalSpeedRestoresAutomaticChoice() {
        for (speed in listOf(0.5f, 1.5f, 2f)) {
            val result = decision(requirements = requirements(speed = speed))
            assertTrue(result.pcmOnly)
            assertEquals(AudioPath.PLATFORM_PCM, result.audioPath)
            assertFalse(result.videoTranscode)
        }
        assertEquals(AudioPath.PASSTHROUGH, decision(requirements = requirements(speed = 1f)).audioPath)
    }

    @Test fun forcedSoftwareDoesNotReturnToTheFailedPlatformDecoder() {
        val result = decision(capabilities = capabilities(mapOf("dts" to allPaths.copy(softwareChannels = 0))),
            requirements = requirements(forceSoftware = true))
        assertEquals(AudioPath.SERVER, result.audioPath)
        assertFalse(result.videoTranscode)
    }

    @Test fun audioFallbackExcludesAliasesButLeavesOtherTargetCodecs() {
        val source = source(codec = "dca")
        val caps = capabilities(mapOf("dts" to allPaths, "dca" to allPaths, "aac" to allPaths))
        val result = decision(source, caps, requirements(forceAudio = true))
        val profileAudio = PlaybackDecisionPolicy.audioForProfile(caps.audio, result, source, null)
        assertEquals(setOf("aac"), profileAudio.channelsByCodec.keys)
        assertTrue(result.audioTranscode)
        assertFalse(result.videoTranscode)
        assertEquals(AudioOutputMode.AUTO, result.outputMode)
        assertEquals(AudioPath.PASSTHROUGH, decision(source, caps).audioPath)
    }

    @Test fun pcmProfileDoesNotAdvertiseCodecsThatOnlySupportPassthrough() {
        val source = source()
        val caps = capabilities(mapOf("dts" to allPaths.copy(platformChannels = 0, softwareChannels = 0), "aac" to allPaths))
        val result = decision(source, caps, requirements(mode = AudioOutputMode.PCM))
        assertEquals(setOf("aac"), PlaybackDecisionPolicy.audioForProfile(caps.audio, result, source, null).channelsByCodec.keys)
    }

    @Test fun videoConversionDoesNotNeedlesslyConvertSupportedAudio() {
        val result = decision(capabilities = capabilities(video = hdrVideo.copy(path = VideoPath.TRANSCODE, targetRange = VideoRange.SDR)))
        assertTrue(result.videoTranscode)
        assertFalse(result.audioTranscode)
        assertEquals(AudioPath.PASSTHROUGH, result.audioPath)
    }

    @Test fun pcmObjectAudioExplainsThatAtmosIsNotConfirmedAtReceiver() {
        val source = source(codec = "eac3").let { it.copy(streams = it.streams.map { stream ->
            if (stream.type == "Audio") stream.copy(audioSpatialFormat = "DolbyAtmos") else stream
        }) }
        val result = decision(source, capabilities(mapOf("eac3" to allPaths)), requirements(mode = AudioOutputMode.PCM))
        assertEquals("audio/eac3-joc", result.sourceAudioMime)
        assertTrue(result.reasons.any { it.contains("不作对象音效保留承诺") })
    }

    @Test fun discRecognitionUsesMetadataAndDoesNotReadServerPath() {
        for (source in listOf(source().copy(container = "iso"), source().copy(videoType = "BluRay"),
            source().copy(isoType = "Dvd"), source().copy(container = "bdmv"))) {
            assertTrue(decision(source).discSource)
        }
        assertFalse(decision(source().copy(path = "/server/movies/archive.iso")).discSource)
    }
}
