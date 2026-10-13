package dev.joyfin.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeekPreviewControllerTest {
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
