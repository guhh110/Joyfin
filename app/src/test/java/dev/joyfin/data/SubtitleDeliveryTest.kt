package dev.joyfin.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubtitleDeliveryTest {
    private val tracks =
        listOf(
            JSONObject("""{"Type":"Audio","Index":0}"""),
            JSONObject("""{"Type":"Subtitle","Index":7,"Codec":"ass"}"""),
            JSONObject("""{"Type":"Subtitle","Index":2,"Codec":"pgssub"}"""),
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
