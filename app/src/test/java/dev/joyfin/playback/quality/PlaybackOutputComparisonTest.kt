package dev.joyfin.playback.quality

import dev.joyfin.playback.quality.QualityFixtures.decision
import org.junit.Assert.*
import org.junit.Test

class PlaybackOutputComparisonTest {
    @Test fun encodedCarrierChannelsDoNotImplySurroundDownmix() {
        val output = PlaybackOutputSnapshot(pcm = false, channels = 2, audioMime = "audio/vnd.dts")
        assertTrue(actualOutputReasons(decision(), output).isEmpty())
    }

    @Test fun observedPcmAndReducedChannelsExplainBothChanges() {
        val reasons = actualOutputReasons(decision(), PlaybackOutputSnapshot(pcm = true, channels = 2))
        assertEquals(2, reasons.size)
        assertTrue(reasons.any { it.contains("未采用原编码") })
        assertTrue(reasons.any { it.contains("少于片源声道") })
    }

    @Test fun systemPcmMappingIsNotReportedAsConfirmedReceiverDownmix() {
        val input = decision().copy(audioPath = AudioPath.PLATFORM_PCM, pcmOutputChannels = 2)
        val reasons = actualOutputReasons(input, PlaybackOutputSnapshot(pcm = true, channels = 6))
        assertEquals(1, reasons.size)
        assertTrue(reasons.single().contains("由系统处理"))
        assertFalse(reasons.single().contains("少于片源"))
    }

    @Test fun dtsHdCoreAndJocFallbackRemainDistinctFromObjectAudioConfirmation() {
        val dts = actualOutputReasons(decision().copy(sourceAudioMime = "audio/vnd.dts.hd"),
            PlaybackOutputSnapshot(pcm = false, audioMime = "audio/vnd.dts"))
        assertTrue(dts.single().contains("DTS 核心"))
        val joc = actualOutputReasons(decision().copy(sourceAudioMime = "audio/eac3-joc"),
            PlaybackOutputSnapshot(pcm = false, audioMime = "audio/eac3"))
        assertTrue(joc.single().contains("对象音效未确认"))
        assertTrue(actualOutputReasons(decision().copy(sourceAudioMime = "audio/eac3-joc"),
            PlaybackOutputSnapshot(pcm = false, audioMime = "audio/eac3-joc")).isEmpty())
    }

    @Test fun unknownFactsDoNotInventAnOutputChange() {
        assertTrue(actualOutputReasons(null, PlaybackOutputSnapshot(pcm = true, channels = 2)).isEmpty())
        assertTrue(actualOutputReasons(decision(), PlaybackOutputSnapshot()).isEmpty())
    }

    @Test fun newlyObservedHdrNeedsConversionOnSdrOrUnknownDisplay() {
        assertTrue(needsVideoConversion(decision(), DisplayPlaybackCapabilities(true), VideoRange.HDR10))
        assertTrue(needsVideoConversion(decision(), DisplayPlaybackCapabilities(), VideoRange.DOLBY_VISION))
        assertFalse(needsVideoConversion(decision(), DisplayPlaybackCapabilities(), VideoRange.UNKNOWN))
        assertFalse(needsVideoConversion(decision(), DisplayPlaybackCapabilities(true), VideoRange.SDR))
    }

    @Test fun hdr10PlusDisplayCanAcceptHdr10WithoutStartingConversion() {
        val display = DisplayPlaybackCapabilities(true, setOf(VideoRange.HDR10_PLUS))
        assertFalse(needsVideoConversion(decision(), display, VideoRange.HDR10))
        assertTrue(needsVideoConversion(decision(), display, VideoRange.HLG))
        assertTrue(needsVideoConversion(decision(), display, VideoRange.DOLBY_VISION))
    }

    @Test fun anAlreadyChosenBaseOrConversionPathDoesNotLoopOnSourceMetadata() {
        val display = DisplayPlaybackCapabilities(true)
        assertFalse(needsVideoConversion(decision().copy(videoPath = VideoPath.BASE_LAYER), display, VideoRange.DOLBY_VISION))
        assertFalse(needsVideoConversion(decision().copy(videoTranscode = true), display, VideoRange.HDR10))
    }
}
