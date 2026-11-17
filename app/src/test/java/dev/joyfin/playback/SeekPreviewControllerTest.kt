package dev.joyfin.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeekPreviewControllerTest {
    @Test
    fun remoteTapKeepsTenSecondStepAndCommitsOneSecondAfterRelease() = runTest {
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { SeekEnvironment(50_000, 500_000, true) }, {}, seeks::add)
        controller.actions.remoteDown(1)
        advanceTimeBy(100)
        controller.actions.remoteUp(1)
        assertEquals(60_000, controller.state!!.targetMs)
        assertNull(controller.state!!.remoteStepMs)
        assertFalse(controller.state!!.requiresConfirmation)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(seeks.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(60_000L), seeks)
    }

    @Test
    fun remoteHoldAcceleratesWithoutSeekingAndReleaseRequiresConfirmation() = runTest {
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { SeekEnvironment(50_000, 5_000_000, true) }, {}, seeks::add)
        controller.remoteDown(1)
        assertEquals(60_000, controller.state!!.targetMs)
        advanceTimeBy(1499)
        runCurrent()
        assertEquals(100_000, controller.state!!.targetMs)
        assertEquals(10_000L, controller.state!!.remoteStepMs)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(130_000, controller.state!!.targetMs)
        assertEquals(30_000L, controller.state!!.remoteStepMs)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(340_000, controller.state!!.targetMs)
        assertEquals(60_000L, controller.state!!.remoteStepMs)
        assertTrue(seeks.isEmpty())
        controller.remoteUp(1)
        val released = controller.state!!
        assertTrue(released.requiresConfirmation)
        assertNull(released.remoteStepMs)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(released, controller.state)
        assertTrue(seeks.isEmpty())
        controller.commit()
        controller.commit()
        assertEquals(listOf(340_000L), seeks)
    }

    @Test
    fun duplicateNativeDownsDoNotMultiplyRepeatsOrResetAcceleration() = runTest {
        val controller = SeekPreviewController(this, { SeekEnvironment(50_000, 5_000_000, true) }, {}, {})
        controller.remoteDown(1)
        repeat(15) {
            advanceTimeBy(100)
            controller.remoteDown(1)
        }
        runCurrent()
        assertEquals(130_000, controller.state!!.targetMs)
        assertEquals(30_000L, controller.state!!.remoteStepMs)
        controller.cancel()
    }

    @Test
    fun reversedHoldResetsSpeedAndIgnoresLateReleaseOfOldDirection() = runTest {
        val controller = SeekPreviewController(this, { SeekEnvironment(500_000, 5_000_000, true) }, {}, {})
        controller.remoteDown(1)
        advanceTimeBy(3000)
        runCurrent()
        val forwardTarget = controller.state!!.targetMs
        controller.remoteDown(-1)
        assertEquals(forwardTarget - 10_000, controller.state!!.targetMs)
        assertEquals(10_000L, controller.state!!.remoteStepMs)
        controller.remoteUp(1)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(forwardTarget - 20_000, controller.state!!.targetMs)
        controller.remoteUp(-1)
        advanceUntilIdle()
        assertTrue(controller.state!!.active)
        assertTrue(controller.state!!.requiresConfirmation)
    }

    @Test
    fun tappingAfterHoldFineTunesWithoutDroppingConfirmation() = runTest {
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { SeekEnvironment(500_000, 5_000_000, true) }, {}, seeks::add)
        controller.remoteDown(1)
        advanceTimeBy(1500)
        runCurrent()
        controller.remoteUp(1)
        val target = controller.state!!.targetMs
        controller.remoteDown(-1)
        controller.remoteUp(-1)
        assertEquals(target - 10_000, controller.state!!.targetMs)
        advanceUntilIdle()
        assertTrue(seeks.isEmpty())
        assertTrue(controller.state!!.requiresConfirmation)
        controller.cancel()
    }

    @Test
    fun heldSeekClampsBothEndsAndCancelStopsRepeating() = runTest {
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { SeekEnvironment(5000, 25_000, true) }, {}, seeks::add)
        controller.remoteDown(-1)
        advanceTimeBy(4000)
        runCurrent()
        assertEquals(0, controller.state!!.targetMs)
        controller.remoteDown(1)
        advanceTimeBy(4000)
        runCurrent()
        assertEquals(25_000, controller.state!!.targetMs)
        controller.cancel()
        controller.remoteUp(1)
        advanceUntilIdle()
        assertNull(controller.state)
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun loadingCancelsHoldAndUnknownDurationNeverStartsOne() = runTest {
        var env = SeekEnvironment(50_000, 0, true)
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { env }, {}, seeks::add)
        controller.remoteDown(1)
        assertNull(controller.state)
        env = env.copy(durationMs = 500_000)
        controller.remoteDown(1)
        env = env.copy(allowed = false)
        advanceUntilIdle()
        assertNull(controller.state)
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun switchingToTouchCancelsRemoteRepeatAndUsesTouchCommitRules() = runTest {
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { SeekEnvironment(50_000, 500_000, true) }, {}, seeks::add)
        controller.remoteDown(1)
        advanceTimeBy(1500)
        runCurrent()
        controller.absolute(200_000)
        controller.remoteUp(1)
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(SeekInput.TIMELINE, controller.state!!.input)
        assertFalse(controller.state!!.requiresConfirmation)
        assertTrue(seeks.isEmpty())
        controller.commit()
        assertEquals(listOf(200_000L), seeks)
    }

    @Test
    fun remoteAccumulatesAndResetsOneSecondDeadline() = runTest {
        val seeks = mutableListOf<Long>()
        val controller =
            SeekPreviewController(this, { SeekEnvironment(50_000, 500_000, true) }, {}, seeks::add)
        controller.adjust(10_000)
        advanceTimeBy(700)
        controller.adjust(10_000)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(seeks.isEmpty())
        assertEquals(70_000, controller.state!!.targetMs)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(70_000L), seeks)
        assertFalse(controller.state!!.active)
        advanceTimeBy(600)
        runCurrent()
        assertNull(controller.state)
    }

    @Test
    fun swipeUsesFrozenBaseAndOnlyReleaseCommits() = runTest {
        var actual = 50_000L
        val seeks = mutableListOf<Long>()
        val controller =
            SeekPreviewController(this, { SeekEnvironment(actual, 500_000, true) }, {}, seeks::add)
        controller.actions.beginSwipe()
        actual += 4000
        controller.swipeOffset(30_000)
        advanceTimeBy(5000)
        assertEquals(50_000, controller.state!!.baseMs)
        assertTrue(seeks.isEmpty())
        controller.commit()
        controller.commit()
        assertEquals(listOf(80_000L), seeks)
    }

    @Test
    fun reversalAndClampingReportActualDelta() = runTest {
        val controller =
            SeekPreviewController(this, { SeekEnvironment(5000, 25_000, true) }, {}, {})
        controller.adjust(-10_000)
        assertEquals(-5000, controller.state!!.deltaMs)
        controller.adjust(10_000)
        assertEquals(10_000, controller.state!!.targetMs)
        controller.adjust(100_000)
        assertEquals(20_000, controller.state!!.deltaMs)
        controller.cancel()
    }

    @Test
    fun cancelNeverSeeksIncludingPendingRemoteTimer() = runTest {
        val seeks = mutableListOf<Long>()
        val controller =
            SeekPreviewController(this, { SeekEnvironment(5000, 500_000, true) }, {}, seeks::add)
        controller.adjust(10_000)
        controller.cancel()
        advanceUntilIdle()
        assertNull(controller.state)
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun loadingOrUnknownDurationDisallowsPreview() = runTest {
        var env = SeekEnvironment(5000, 0, true)
        val seeks = mutableListOf<Long>()
        val controller = SeekPreviewController(this, { env }, {}, seeks::add)
        controller.adjust(10_000)
        assertNull(controller.state)
        env = SeekEnvironment(5000, 500_000, false)
        controller.actions.beginSwipe()
        assertNull(controller.state)
        env = env.copy(allowed = true)
        controller.adjust(10_000)
        env = env.copy(allowed = false)
        advanceUntilIdle()
        assertTrue(seeks.isEmpty())
        assertNull(controller.state)
    }

    @Test
    fun timelinePreviewAndTapShareCommitAndCancelRules() = runTest {
        val seeks = mutableListOf<Long>()
        val controller =
            SeekPreviewController(this, { SeekEnvironment(50_000, 500_000, true) }, {}, seeks::add)
        controller.absolute(300_000)
        controller.absolute(250_000)
        advanceTimeBy(2000)
        assertTrue(seeks.isEmpty())
        controller.commit()
        assertEquals(listOf(250_000L), seeks)
        controller.absolute(10_000)
        controller.cancel()
        advanceUntilIdle()
        assertEquals(1, seeks.size)
    }

    @Test
    fun explicitCommitRunsOnceAndNextOperationHasNewBase() = runTest {
        var actual = 50_000L
        val seeks = mutableListOf<Long>()
        val controller =
            SeekPreviewController(
                this,
                { SeekEnvironment(actual, 500_000, true) },
                {},
                {
                    seeks += it
                    actual = it
                },
            )
        controller.adjust(10_000)
        controller.commit()
        advanceTimeBy(200)
        controller.adjust(-10_000)
        assertEquals(60_000, controller.state!!.baseMs)
        advanceUntilIdle()
        assertEquals(listOf(60_000L, 50_000L), seeks)
    }
}
