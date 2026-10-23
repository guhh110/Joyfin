package dev.joyfin.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackSpeedControllerTest {
    @Test fun releasingAHoldRestoresTheUsersSelectedSpeed() {
        val output = mutableListOf<Pair<Float, Boolean>>()
        val speed = PlaybackSpeedController(1.5f) { value, held -> output += value to held }
        val token = requireNotNull(speed.begin())
        assertEquals(1.5f, speed.selectedSpeed)
        assertTrue(speed.boosting)
        speed.end(token)
        assertEquals(listOf(2f to true, 1.5f to false), output)
        assertFalse(speed.boosting)
    }

    @Test fun aLateReleaseFromACanceledMediaCannotEndANewHold() {
        val output = mutableListOf<Float>()
        val speed = PlaybackSpeedController(1f) { value, _ -> output += value }
        val old = requireNotNull(speed.begin())
        speed.cancel()
        val current = requireNotNull(speed.begin())
        speed.end(old)
        assertTrue(speed.boosting)
        assertEquals(listOf(2f, 1f, 2f), output)
        speed.end(current)
        assertEquals(listOf(2f, 1f, 2f, 1f), output)
    }

    @Test fun anExplicitSpeedSelectionWinsOverTheOldHoldsRelease() {
        val output = mutableListOf<Pair<Float, Boolean>>()
        val speed = PlaybackSpeedController(.75f) { value, held -> output += value to held }
        val old = requireNotNull(speed.begin())
        speed.select(1.25f)
        speed.end(old)
        assertEquals(listOf(2f to true, 1.25f to false), output)
        assertEquals(1.25f, speed.selectedSpeed)
        assertFalse(speed.boosting)
    }

    @Test fun repeatedStartReleaseAndLifecycleCancellationDoNotReapplySpeed() {
        val output = mutableListOf<Float>()
        val speed = PlaybackSpeedController(1f) { value, _ -> output += value }
        speed.cancel()
        val token = requireNotNull(speed.begin())
        assertNull(speed.begin())
        speed.cancel()
        speed.cancel()
        speed.end(token)
        assertEquals(listOf(2f, 1f), output)
    }

    @Test fun holdingNeverSlowsAnAlreadyFasterSelection() {
        val output = mutableListOf<Float>()
        val speed = PlaybackSpeedController(3f) { value, _ -> output += value }
        val token = requireNotNull(speed.begin())
        speed.end(token)
        assertEquals(listOf(3f, 3f), output)
    }

    @Test fun invalidSelectionsDoNotDestroyAnActiveHoldsRestorePoint() {
        val output = mutableListOf<Float>()
        val speed = PlaybackSpeedController(1.25f) { value, _ -> output += value }
        val token = requireNotNull(speed.begin())
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach(speed::select)
        speed.end(token)
        assertEquals(listOf(2f, 1.25f), output)
    }
}
