package dev.joyfin.data

import dev.joyfin.playback.quality.VideoPlaybackPolicy
import dev.joyfin.playback.quality.VideoRange
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class MediaQualityMetadataTest {
    @Test fun olderServerPayloadLeavesNewFieldsUnknown() {
        val source = AppJson.decodeFromString<MediaSource>("""{"Id":"old","MediaStreams":[{"Type":"Video","Codec":"hevc"}]}""")
        val video = source.streams.single()
        assertNull(video.dvBlSignalCompatibilityId)
        assertNull(video.colorTransfer)
        assertNull(video.rpuPresentFlag)
        assertNull(video.elPresentFlag)
        assertNull(video.blPresentFlag)
        assertNull(video.hdr10PlusPresentFlag)
        assertNull(video.audioSpatialFormat)
        assertNull(source.videoType)
        assertNull(source.isoType)
        assertEquals(VideoRange.UNKNOWN, VideoPlaybackPolicy.rangeOf(video))
    }

    @Test fun newServerQualityMetadataRetainsWireTypesAndNullability() {
        val source = AppJson.decodeFromString<MediaSource>("""{
            "Id":"main","VideoType":"Iso","IsoType":"BluRay","TranscodingContainer":"mp4","TranscodingSubProtocol":"hls",
            "MediaStreams":[
                {"Type":"Video","Codec":"hevc","DvProfile":8,"DvLevel":6,"DvBlSignalCompatibilityId":1,
                 "RpuPresentFlag":1,"ElPresentFlag":0,"BlPresentFlag":1,"Hdr10PlusPresentFlag":true,
                 "ColorSpace":"bt2020nc","ColorPrimaries":"bt2020","ColorTransfer":"smpte2084","ColorRange":"tv"},
                {"Type":"Audio","Codec":"eac3","AudioSpatialFormat":"DolbyAtmos","ColorSpace":null}
            ]} """)
        val video = source.streams.first()
        assertEquals(8, video.dvProfile)
        assertEquals(1, video.dvBlSignalCompatibilityId)
        assertEquals(1, video.rpuPresentFlag)
        assertEquals(0, video.elPresentFlag)
        assertEquals(1, video.blPresentFlag)
        assertEquals(true, video.hdr10PlusPresentFlag)
        assertEquals("bt2020", video.colorPrimaries)
        assertEquals("smpte2084", video.colorTransfer)
        assertEquals("DolbyAtmos", source.streams[1].audioSpatialFormat)
        assertNull(source.streams[1].colorSpace)
        assertEquals("BluRay", source.isoType)
        assertEquals("mp4", source.transcodingContainer)
        assertEquals("hls", source.transcodingSubProtocol)
    }

    @Test fun audioSelectionUsesExplicitIndexThenSourceDefaultThenFirstAudio() {
        val first = MediaStream(type = "Audio", index = 1, codec = "dts")
        val preferred = MediaStream(type = "Audio", index = 4, codec = "aac")
        val source = MediaSource(defaultAudioIndex = 4, streams = listOf(MediaStream(type = "Video", index = 0), first, preferred))
        assertEquals(preferred, source.audioStream())
        assertEquals(first, source.audioStream(1))
        assertEquals(first, source.audioStream(999))
        assertNull(source.copy(streams = emptyList()).audioStream())
    }

    @Test fun transcodeMethodAloneDoesNotIncorrectlyForceHlsParser() {
        fun plan(url: String, source: MediaSource = MediaSource()) = PlaybackPlan(
            Media("movie", "Movie", "Movie"), source, "session", url, "Transcode", 0, null, -1, Int.MAX_VALUE, emptyList())
        assertEquals("application/x-mpegURL", plan("https://example.test/main.m3u8?token=test").mimeType)
        assertEquals("application/x-mpegURL", plan("https://example.test/stream", MediaSource(transcodingSubProtocol = "hls")).mimeType)
        assertEquals("video/mp4", plan("https://example.test/stream", MediaSource(transcodingContainer = "mp4")).mimeType)
        assertNull(plan("https://example.test/stream").mimeType)
    }
}
