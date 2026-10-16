package dev.joyfin.playback.quality

import dev.joyfin.data.MediaStream
import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackPolicyTest {
    private val hdr10 = DisplayPlaybackCapabilities(true, setOf(VideoRange.HDR10))
    private val sdr = DisplayPlaybackCapabilities(true)
    private fun video(range: String) = MediaStream(type = "Video", codec = "hevc", bitDepth = 10, videoRangeType = range)
    private fun assess(stream: MediaStream?, display: DisplayPlaybackCapabilities = hdr10,
        native: Boolean = true, base: Boolean = true, codecs: Set<String> = setOf("hevc")) =
        VideoPlaybackPolicy.assess(stream, display, codecs, { native }, { base })

    @Test fun hdrNeedsBothDisplayAndDecoderSupport() {
        assertEquals(VideoPath.NATIVE, assess(video("HDR10")).path)
        assertEquals(VideoPath.TRANSCODE, assess(video("HDR10"), native = false).path)
        val result = assess(video("HDR10"), sdr)
        assertEquals(VideoPath.TRANSCODE, result.path)
        assertEquals(VideoRange.SDR, result.targetRange)
        assertFalse("HDR10" in result.acceptedRanges)
    }

    @Test fun hlgDoesNotPretendToBeHdr10() {
        assertEquals(VideoPath.TRANSCODE, assess(video("HLG")).path)
        val result = assess(video("HLG"), DisplayPlaybackCapabilities(true, setOf(VideoRange.HLG)))
        assertEquals(VideoPath.NATIVE, result.path)
        assertEquals(VideoRange.HLG, result.targetRange)
    }

    @Test fun hdr10PlusCanUseKnownHdr10Base() {
        val result = assess(video("HDR10Plus"))
        assertEquals(VideoPath.BASE_LAYER, result.path)
        assertEquals(VideoRange.HDR10_PLUS, result.sourceRange)
        assertEquals(VideoRange.HDR10, result.targetRange)
        assertTrue(result.reasons.any { it.contains("基础层") })
    }

    @Test fun baseLayerStillRequiresDecoderSupport() {
        assertEquals(VideoPath.TRANSCODE, assess(video("HDR10Plus"), base = false).path)
    }

    @Test fun nativeDolbyVisionP5RequiresTheDolbyVisionDisplayAndDecoder() {
        val p5 = video("DOVI").copy(dvProfile = 5)
        val dvDisplay = DisplayPlaybackCapabilities(true, setOf(VideoRange.DOLBY_VISION))
        assertEquals(VideoPath.NATIVE, assess(p5, dvDisplay).path)
        assertEquals(VideoPath.TRANSCODE, assess(p5, dvDisplay, native = false).path)
    }

    @Test fun p5NeverBlindlyUsesTheHevcDecoderAsAnHdr10Base() {
        var baseProbed = false
        val result = VideoPlaybackPolicy.assess(video("DOVI").copy(dvProfile = 5), hdr10, setOf("hevc"),
            { false }, { baseProbed = true; true })
        assertEquals(VideoPath.TRANSCODE, result.path)
        assertEquals(VideoRange.SDR, result.targetRange)
        assertFalse(baseProbed)
    }

    @Test fun p7UsesItsKnownHdr10BaseWithoutClaimingFelProcessing() {
        val result = assess(video("DOVIWithEL").copy(dvProfile = 7, elPresentFlag = 1))
        assertEquals(VideoPath.BASE_LAYER, result.path)
        assertEquals(VideoRange.HDR10, result.targetRange)
        val native = assess(video("DOVIWithEL").copy(dvProfile = 7),
            DisplayPlaybackCapabilities(true, setOf(VideoRange.DOLBY_VISION)))
        assertEquals(VideoPath.NATIVE, native.path)
        assertTrue(native.reasons.any { it.contains("增强层处理未经验证") })
    }

    @Test fun p8WithoutBaseCompatibilityMetadataDoesNotGuessHdr10() {
        assertEquals(VideoPath.TRANSCODE, assess(video("DOVI").copy(dvProfile = 8)).path)
    }

    @Test fun p8KnownHdr10BaseHasDolbyConfigurationRemovedBeforeProbing() {
        val original = video("DOVIWithHDR10").copy(dvProfile = 8, dvLevel = 6, dvBlSignalCompatibilityId = 1)
        var seen: MediaStream? = null
        val result = VideoPlaybackPolicy.assess(original, hdr10, setOf("hevc"), { false }, { seen = it; true })
        assertEquals(VideoPath.BASE_LAYER, result.path)
        assertNull(seen!!.dvProfile)
        assertNull(seen!!.dvLevel)
        assertEquals("hevc", seen!!.codec)
        assertEquals(10, seen!!.bitDepth)
        assertEquals(VideoRange.HDR10, VideoPlaybackPolicy.rangeOf(seen!!))
        assertEquals(8, original.dvProfile)
    }

    @Test fun p8HlgAndSdrCompatibilityAreHandledIndependently() {
        val p8 = video("DOVI").copy(dvProfile = 8)
        val hlg = assess(p8.copy(dvBlSignalCompatibilityId = 4), DisplayPlaybackCapabilities(true, setOf(VideoRange.HLG)))
        assertEquals(VideoPath.BASE_LAYER, hlg.path)
        assertEquals(VideoRange.HLG, hlg.targetRange)
        assertEquals(VideoPath.TRANSCODE, assess(p8.copy(dvBlSignalCompatibilityId = 4)).path)
        val baseSdr = assess(p8.copy(dvBlSignalCompatibilityId = 2), sdr)
        assertEquals(VideoPath.BASE_LAYER, baseSdr.path)
        assertEquals(VideoRange.SDR, baseSdr.targetRange)
    }

    @Test fun knownHdr10PlusDolbyBaseRetainsDynamicMetadataCapability() {
        val result = assess(video("DOVIWithHDR10Plus").copy(dvProfile = 8, dvBlSignalCompatibilityId = 1),
            DisplayPlaybackCapabilities(true, setOf(VideoRange.HDR10_PLUS)))
        assertEquals(VideoPath.BASE_LAYER, result.path)
        assertEquals(VideoRange.HDR10_PLUS, result.targetRange)
    }

    @Test fun invalidDolbyConfigurationDoesNotProbeOrAdvertiseNativePlayback() {
        val result = VideoPlaybackPolicy.assess(video("DOVIInvalid"),
            DisplayPlaybackCapabilities(true, setOf(VideoRange.DOLBY_VISION)), setOf("hevc"),
            { error("Invalid metadata must not reach the decoder") }, { error("No valid base layer") })
        assertEquals(VideoPath.TRANSCODE, result.path)
        assertFalse(result.acceptedRanges.any { it.startsWith("DOVI") })
    }

    @Test fun unknownDisplayAndUnknownHdrRequestSafeServerConversion() {
        val unknownDisplay = assess(video("HDR10"), DisplayPlaybackCapabilities())
        assertEquals(VideoPath.TRANSCODE, unknownDisplay.path)
        assertTrue(unknownDisplay.reasons.any { it.contains("显示能力未知") })
        val unknownHdr = assess(video("").copy(videoRange = "HDR"))
        assertEquals(VideoPath.TRANSCODE, unknownHdr.path)
        assertEquals(VideoRange.UNKNOWN, unknownHdr.sourceRange)
    }

    @Test fun missingColorMetadataStaysUnknownWhileSupportedSdrCanPlay() {
        val unknown = assess(video(""), sdr)
        assertEquals(VideoRange.UNKNOWN, unknown.sourceRange)
        assertEquals(VideoPath.NATIVE, unknown.path)
        assertEquals(VideoPath.NATIVE, assess(video("SDR"), sdr).path)
        assertEquals(VideoPath.TRANSCODE, assess(video("SDR"), sdr, codecs = emptySet()).path)
        assertEquals(VideoPath.UNKNOWN, assess(null).path)
    }

    @Test fun transferMetadataIsUsedWithoutInventingDolbyVision() {
        assertEquals(VideoRange.HDR10, VideoPlaybackPolicy.rangeOf(video("").copy(colorTransfer = "smpte2084")))
        assertEquals(VideoRange.HLG, VideoPlaybackPolicy.rangeOf(video("").copy(colorTransfer = "arib-std-b67")))
        assertEquals(VideoRange.HDR10_PLUS, VideoPlaybackPolicy.rangeOf(video("").copy(hdr10PlusPresentFlag = true)))
        assertEquals(VideoRange.DOLBY_VISION, VideoPlaybackPolicy.rangeOf(video("HDR10Plus").copy(dvProfile = 8)))
    }
}
