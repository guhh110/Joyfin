package dev.joyfin

import android.content.res.Configuration
import dev.joyfin.ui.device.isTelevision
import org.junit.Assert.*
import org.junit.Test

class DeviceLayoutTest {
    @Test
    fun wideHandheldDoesNotBecomeATelevision() {
        val configuration = Configuration()
        configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL
        configuration.smallestScreenWidthDp = 720
        assertFalse(configuration.isTelevision())
    }

    @Test
    fun televisionModeIsRecognizedRegardlessOfResolution() {
        val configuration = Configuration()
        configuration.uiMode = Configuration.UI_MODE_TYPE_TELEVISION
        configuration.smallestScreenWidthDp = 320
        assertTrue(configuration.isTelevision())
    }
}
