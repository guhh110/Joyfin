package dev.joyfin.ui.features.player.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBatteryTest {
    @Test
    fun batteryStateProperties() {
        val normal = BatteryState(85, false)
        assertEquals(85, normal.level)
        assertFalse(normal.charging)

        val charging = BatteryState(20, true)
        assertEquals(20, charging.level)
        assertTrue(charging.charging)
    }
}
