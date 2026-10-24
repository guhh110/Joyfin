package dev.joyfin.playback

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNavigationTest {
    private fun segment(type: String, start: Long, end: Long) = MediaSegment("e", type, start * 10_000, end * 10_000)
    private val episode = Media("e", "Episode", "Episode", seriesId = "series")

    @Test fun serverIntroOverridesManualIntroWhileMissingOutroUsesTheSavedDuration() {
        val ranges = PlaybackNavigationPolicy.ranges(listOf(segment("Intro", 30000, 90000),
            segment("Recap", 0, 20000)), 600000, SkipPreferences(120, 60), true)
        assertEquals(listOf(SkipRange(30000, 90000, "片头"), SkipRange(540000, 600000, "片尾")), ranges)
    }

    @Test fun ambiguousOverlappingAndInvalidRangesNeverBecomeSkipTargets() {
        val ranges = PlaybackNavigationPolicy.ranges(listOf(segment("Intro", 0, 90000),
            segment("Outro", 80000, 100000), segment("Intro", -1, 100), segment("Intro", 200000, 900000)),
            600000, SkipPreferences(), true)
        assertTrue(ranges.isEmpty())
        assertTrue(PlaybackNavigationPolicy.ranges(emptyList(), 10000, SkipPreferences(60, 60), true).isEmpty())
        assertTrue(PlaybackNavigationPolicy.ranges(emptyList(), 600000, SkipPreferences(90, 90), false).isEmpty())
    }

    @Test fun namedChaptersProvideFallbackWhenServerHasNoSegments() {
        val chapters = listOf(
            MediaChapter(0, "片头"),
            MediaChapter(300_000_000, "正片"),
            MediaChapter(600_000_000, "片尾"),
        )
        assertEquals(
            listOf(SkipRange(0, 30000, "片头"), SkipRange(60000, 90000, "片尾")),
            PlaybackNavigationPolicy.ranges(emptyList(), 90000, SkipPreferences(), false, chapters),
        )
    }

    @Test fun chaptersAreSortedUniqueAndBoundedByTheActualVideoDuration() {
        val media = episode.copy(chapters = listOf(MediaChapter(500_000_000, "Middle"),
            MediaChapter(0), MediaChapter(-1, "Invalid"), MediaChapter(500_000_000, "Duplicate"),
            MediaChapter(1_000_000_000, "Past end")))
        assertEquals(listOf(ChapterChoice(0, "第 1 章", 0), ChapterChoice(50000, "Middle", 1)),
            PlaybackNavigationPolicy.chapters(media, 100000))
    }

    @Test fun manualAutoSkipWaitsForServerMetadataAndUndoPreventsASecondAutomaticSkip() = runTest {
        var state = PlaybackNavigationState()
        val controller = PlaybackNavigation(this, { SkipPreferences(120, 0, true) }, { _, _ -> }, {
            delay(1000)
            listOf(segment("Intro", 30000, 90000))
        }, { state = it }, { fail("Unexpected expiry") })
        controller.open(episode, "version")
        assertNull(controller.tick(0, 600000, true, 0))
        assertTrue(state.loading)
        advanceUntilIdle()
        assertNull(controller.tick(0, 600000, true, 1000))
        assertNull(controller.tick(35000, 600000, false, 1100))
        assertEquals(90000L, controller.tick(35000, 600000, true, 1200))
        assertEquals(35000L, controller.undo(1300))
        assertNull(controller.tick(35000, 600000, true, 1400))
    }

    @Test fun switchingItemsRejectsLateSegmentsAndClearsUndo() = runTest {
        var state = PlaybackNavigationState()
        val controller = PlaybackNavigation(this, { SkipPreferences() }, { _, _ -> }, { id ->
            if (id == "e") withContext(NonCancellable) { delay(1000) }
            if (id == "e") listOf(segment("Intro", 0, 90000)) else emptyList()
        }, { state = it }, {})
        controller.open(episode, "version")
        runCurrent()
        controller.open(episode.copy(id = "other"), "version")
        advanceUntilIdle()
        controller.tick(1000, 600000, false, 1500)
        assertNull(state.offer)
        assertNull(controller.undo(1500))
        assertFalse(state.loading)
    }

    @Test fun alternateVersionDoesNotUsePrimaryVersionsServerTimeline() = runTest {
        var state = PlaybackNavigationState()
        val controller = PlaybackNavigation(this, { SkipPreferences() }, { _, _ -> },
            { error("Must not query item-level segments for an alternate version") }, { state = it }, {})
        controller.open(episode.copy(sources = listOf(MediaSource(id = "primary"), MediaSource(id = "alternate")),
            chapters = listOf(MediaChapter(0, "Primary chapter"))), "alternate")
        controller.tick(0, 600000, false, 0)
        advanceUntilIdle()
        assertFalse(state.timelineAvailable)
        assertTrue(state.chapters.isEmpty())
    }

    @Test fun latePrimaryVersionResponseCannotEnableSkippingOnAnAlternateVersion() = runTest {
        var state = PlaybackNavigationState()
        val controller = PlaybackNavigation(this, { SkipPreferences() }, { _, _ -> }, {
            withContext(NonCancellable) { delay(1000) }
            listOf(segment("Intro", 0, 90000))
        }, { state = it }, {})
        val multi = episode.copy(sources = listOf(MediaSource(id = "primary"), MediaSource(id = "alternate")))
        controller.open(multi, "primary")
        runCurrent()
        controller.open(multi, "alternate")
        advanceUntilIdle()
        controller.tick(1000, 600000, true, 2000)
        assertNull(state.offer)
        assertFalse(state.timelineAvailable)
        assertFalse(state.loading)
    }

    @Test fun failedOptionalMetadataKeepsManualSkipUsableAndCanRetry() = runTest {
        var fail = true
        var state = PlaybackNavigationState()
        val controller = PlaybackNavigation(this, { SkipPreferences(60) }, { _, _ -> },
            { if (fail) error("offline") else emptyList() }, { state = it }, {})
        controller.open(episode, "version")
        advanceUntilIdle()
        assertNotNull(state.error)
        controller.tick(10000, 600000, false, 0)
        assertEquals(60000L, controller.skip(10000, 0))
        fail = false
        controller.retry()
        advanceUntilIdle()
        assertNull(state.error)
        controller.clear()
        assertNull(controller.undo(100))
    }
}
