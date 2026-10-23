package dev.joyfin.data

import dev.joyfin.data.*
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class SubtitleDeliveryTest {
    @Test fun mkvTextSubtitlesUseTheNativeDemuxerAndKeepExternalFallbackForOtherContainers() {
        for (codec in listOf("ass", "ssa", "srt", "subrip", "vtt", "webvtt")) {
            val track = MediaStream(type = "Subtitle", index = 7, codec = codec)
            assertTrue(SubtitleDelivery.embedded(track, "mkv"))
            assertTrue(SubtitleDelivery.embedded(track, "matroska"))
            assertFalse(SubtitleDelivery.embedded(track, "mp4"))
            assertFalse(SubtitleDelivery.embedded(track.copy(external = true), "mkv"))
            assertTrue(SubtitleDelivery.profiles().any { it.format == codec && it.method == "Embed" && it.container == "mkv,matroska" })
            assertTrue(SubtitleDelivery.profiles().any { it.format == codec && it.method == "External" })
        }
    }

    @Test fun independentSourcesAreLimitedToTheSelectedSubtitleAndRemovedForOffOrNativePlayback() {
        val track = MediaStream(type = "Subtitle", index = 7, codec = "ass")
        assertFalse(SubtitleDelivery.needsExternalSource(track, -1, true, "mkv"))
        assertFalse(SubtitleDelivery.needsExternalSource(track, 2, false, "mkv"))
        assertFalse(SubtitleDelivery.needsExternalSource(track, 7, true, "mkv"))
        assertTrue(SubtitleDelivery.needsExternalSource(track, 7, false, "mkv"))
        assertTrue(SubtitleDelivery.needsExternalSource(track.copy(external = true), 7, true, "mkv"))
        assertTrue(SubtitleDelivery.needsExternalSource(track, 7, true, "mp4"))
        assertFalse(SubtitleDelivery.needsExternalSource(track.copy(deliveryMethod = "Encode"), 7, false, "mkv"))
    }

    @Test
    fun textConversionIsNotVideoBurnInAndUsesTheServersDeliveryFormat() {
        val sami =
            MediaStream(
                type = "Subtitle",
                index = 8,
                codec = "sami",
                textSubtitle = true,
                deliveryUrl = "/Videos/source/Subtitles/8/Stream.srt?token=unused",
            )
        assertFalse(SubtitleDelivery.requiresBurnIn(8, listOf(sami), "mkv"))
        assertEquals("application/x-subrip", SubtitleDelivery.externalFormat(sami)?.mime)
        assertEquals("application/ttml+xml", SubtitleDelivery.format("ttml")?.mime)
    }

    @Test
    fun bitmapSubtitlesUseSupportedEmbeddedContainersButNeverPretendExternalSupIsSupported() {
        val pgs = MediaStream(type = "Subtitle", index = 5, codec = "pgssub")
        assertFalse(SubtitleDelivery.requiresBurnIn(5, listOf(pgs), "mkv"))
        assertFalse(SubtitleDelivery.requiresBurnIn(5, listOf(pgs), "matroska"))
        assertTrue(SubtitleDelivery.requiresBurnIn(5, listOf(pgs), "mp4"))
        assertTrue(SubtitleDelivery.requiresBurnIn(5, listOf(pgs.copy(external = true)), "mkv"))
        assertFalse(SubtitleDelivery.requiresBurnIn(5, listOf(pgs.copy(codec = "dvdsub")), "mkv"))
        assertFalse(SubtitleDelivery.requiresBurnIn(5, listOf(pgs.copy(codec = "mov_text")), "mp4"))
    }

    private val tracks =
        listOf(
            AppJson.decodeFromString<MediaStream>("""{"Type":"Audio","Index":0}"""),
            AppJson.decodeFromString<MediaStream>(
                """{"Type":"Subtitle","Index":7,"Codec":"ass"}"""
            ),
            AppJson.decodeFromString<MediaStream>(
                """{"Type":"Subtitle","Index":2,"Codec":"pgssub"}"""
            ),
        )

    @Test
    fun automaticSelectionUsesFirstServerSubtitleRatherThanLowestIndex() {
        assertEquals(7, SubtitleDelivery.select(null, tracks))
        assertEquals(-1, SubtitleDelivery.select(null, emptyList()))
    }

    @Test
    fun explicitOffAndSelectionSurviveReload() {
        assertEquals(-1, SubtitleDelivery.select(-1, tracks))
        assertEquals(2, SubtitleDelivery.select(2, tracks))
    }

    @Test
    fun textIsRenderedLocallyAndBitmapRequiresBurnIn() {
        assertFalse(SubtitleDelivery.requiresBurnIn(7, tracks))
        assertTrue(SubtitleDelivery.requiresBurnIn(2, tracks))
        assertFalse(SubtitleDelivery.requiresBurnIn(-1, tracks))
        assertEquals("text/x-ssa", SubtitleDelivery.format("ASS")!!.mime)
        assertEquals("ssa", SubtitleDelivery.format("ssa")!!.extension)
        assertNull(SubtitleDelivery.format("unknown"))
    }
}
