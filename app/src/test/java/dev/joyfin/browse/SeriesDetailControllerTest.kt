package dev.joyfin.browse

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailControllerTest {
    private fun episode(id: String, season: Int = 1) =
        Media(
            id,
            id,
            "Episode",
            seriesId = "show",
            seasonId = "s$season",
            season = season,
            episode = if (id.endsWith("2")) 2 else 1,
        )

    private val seasons = listOf(Media("s1", "第 1 季", "Season"), Media("s2", "第 2 季", "Season"))

    @Test
    fun slowRecommendationDoesNotBlockSeasonsOrPlaybackTargets() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, season ->
                    if (season == null) delay(5000)
                    listOf(episode("e1"))
                },
                { episode(it) },
                { state },
                { state = it },
                {},
                { seasons },
            )
        controller.initialize("show")
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(seasons, state!!.seasons.data)
        assertEquals("e1", state!!.episodes.data!!.single().id)
        assertTrue(state!!.recommendation.loading)
        assertEquals("e1", state!!.inspected!!.id)
        advanceUntilIdle()
        assertEquals("e1", state!!.recommendation.data!!.id)
    }

    @Test
    fun remembersBrowsingWithoutChangingRecommendation() = runTest {
        val recommended = episode("e1")
        var state: SeriesDetailState? =
            SeriesDetailState("show", recommendation = SeriesResource.Ready(recommended))
        val controller =
            SeriesDetailController(
                this,
                { _, season ->
                    if (season == "s1") listOf(episode("e1"), episode("e2"))
                    else listOf(episode("x1", 2))
                },
                { episode(it, if (it.startsWith("x")) 2 else 1).copy(overview = "details") },
                { state },
                { state = it },
                {},
            )
        controller.season("s1")
        advanceUntilIdle()
        controller.episode(episode("e2"))
        advanceUntilIdle()
        controller.season("s2")
        advanceUntilIdle()
        controller.season("s1")
        advanceUntilIdle()
        assertEquals("e2", state!!.inspected!!.id)
        assertEquals("details", state!!.inspected!!.overview)
        assertEquals(recommended, state!!.recommendation.data)
    }

    @Test
    fun rapidAtoBtoARejectsLateSeasonResponse() = runTest {
        var state: SeriesDetailState? = SeriesDetailState("show")
        var first = true
        val controller =
            SeriesDetailController(
                this,
                { _, season ->
                    val old = season == "s1" && first
                    if (old) {
                        first = false
                        withContext(NonCancellable) { delay(500) }
                    }
                    listOf(episode(if (old) "old1" else "fresh1", if (season == "s1") 1 else 2))
                },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.season("s1")
        runCurrent()
        controller.season("s2")
        runCurrent()
        controller.season("s1")
        advanceUntilIdle()
        assertEquals("s1", state!!.seasonId)
        assertEquals("fresh1", state!!.episodes.data!!.single().id)
    }

    @Test
    fun passingRowsOnlyFetchesSettledEpisode() = runTest {
        var state: SeriesDetailState? =
            SeriesDetailState(
                "show",
                seasonId = "s1",
                episodes = SeriesResource.Ready(listOf(episode("e1"), episode("e2"))),
            )
        val fetched = mutableListOf<String>()
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                {
                    fetched += it
                    episode(it)
                },
                { state },
                { state = it },
                {},
            )
        controller.episode(episode("e1"))
        advanceTimeBy(50)
        controller.episode(episode("e2"))
        advanceUntilIdle()
        assertEquals(listOf("e2"), fetched)
    }

    @Test
    fun lateEpisodeCannotOverwriteNewInspection() = runTest {
        var state: SeriesDetailState? =
            SeriesDetailState(
                "show",
                seasonId = "s1",
                episodes = SeriesResource.Ready(listOf(episode("e1"), episode("e2"))),
            )
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                { id ->
                    if (id == "e1") withContext(NonCancellable) { delay(500) }
                    episode(id).copy(overview = id)
                },
                { state },
                { state = it },
                {},
            )
        controller.episode(episode("e1"))
        advanceTimeBy(200)
        runCurrent()
        controller.episode(episode("e2"))
        advanceUntilIdle()
        assertEquals("e2", state!!.inspected!!.overview)
    }

    @Test
    fun cacheRefreshFailurePreservesDataAndLatestUserInspection() = runTest {
        val cached = listOf(episode("e1"), episode("e2"))
        var state: SeriesDetailState? =
            SeriesDetailState("show", seasonCache = mapOf("s1" to cached))
        val controller =
            SeriesDetailController(
                this,
                { _, _ ->
                    delay(5000)
                    error("offline")
                },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.season("s1")
        assertEquals(cached, state!!.episodes.data)
        assertTrue(state!!.episodes.loading)
        controller.episode(episode("e2"))
        advanceUntilIdle()
        assertEquals("offline", state!!.episodes.error)
        assertEquals(cached, state!!.episodes.data)
        assertEquals("e2", state!!.inspected!!.id)
    }

    @Test
    fun refreshedSeasonDoesNotResetBrowsingToOriginalRememberedItem() = runTest {
        val cached = listOf(episode("e1"), episode("e2"))
        var state: SeriesDetailState? =
            SeriesDetailState(
                "show",
                remembered = mapOf("s1" to "e1"),
                seasonCache = mapOf("s1" to cached),
            )
        val controller =
            SeriesDetailController(
                this,
                { _, _ ->
                    delay(500)
                    cached
                },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.season("s1")
        controller.episode(episode("e2"))
        advanceUntilIdle()
        assertEquals("e2", state!!.inspectedEpisodeId)
    }

    @Test
    fun uncachedSeasonNeverReusesOtherSeasonItems() = runTest {
        var state: SeriesDetailState? =
            SeriesDetailState(
                "show",
                seasonId = "s1",
                episodes = SeriesResource.Ready(listOf(episode("e1"))),
            )
        val controller =
            SeriesDetailController(
                this,
                { _, _ ->
                    delay(500)
                    emptyList()
                },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.season("s2")
        assertNull(state!!.episodes.data)
        assertNull(state!!.inspected)
        advanceUntilIdle()
        assertTrue(state!!.episodes is SeriesResource.Ready)
        assertTrue(state!!.episodes.data!!.isEmpty())
    }

    @Test
    fun missingExternalEpisodeDoesNotSelectAnotherEpisode() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> listOf(episode("e1")) },
                { episode(it) },
                { state },
                { state = it },
                {},
                { seasons },
            )
        controller.initialize("show", "s1", "missing")
        advanceUntilIdle()
        assertNull(state!!.inspected)
        assertTrue(state!!.entryError!!.contains("不存在"))
        assertEquals(listOf(episode("e1")), state!!.episodes.data)
    }

    @Test
    fun missingExternalSeasonDoesNotGuessFirstSeason() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                { episode(it) },
                { state },
                { state = it },
                {},
                { seasons },
            )
        controller.initialize("show", "missing")
        advanceUntilIdle()
        assertNull(state!!.seasonId)
        assertTrue(state!!.entryError!!.contains("不存在"))
    }

    @Test
    fun failedEpisodeSupplementStillExposesListPlaybackTarget() = runTest {
        var state: SeriesDetailState? = SeriesDetailState("show")
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> listOf(episode("e1").copy(overview = "known")) },
                { error("offline") },
                { state },
                { state = it },
                {},
            )
        controller.season("s1")
        advanceUntilIdle()
        assertEquals("offline", state!!.episodeDetails.error)
        assertEquals("e1", state!!.inspected!!.id)
        assertEquals("known", state!!.inspected!!.overview)
    }

    @Test
    fun recommendationFailureDoesNotFailLoadedSeason() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, season ->
                    if (season == null) error("scan failed") else listOf(episode("e1"))
                },
                { episode(it) },
                { state },
                { state = it },
                {},
                { seasons },
            )
        controller.initialize("show")
        advanceUntilIdle()
        assertEquals("scan failed", state!!.recommendation.error)
        assertEquals("e1", state!!.episodes.data!!.single().id)
        assertNull(state!!.episodes.error)
    }

    @Test
    fun userSeasonSelectionWinsOverLateSeasonInitialization() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, season -> listOf(episode("e1", if (season == "s2") 2 else 1)) },
                { episode(it) },
                { state },
                { state = it },
                {},
                {
                    delay(500)
                    seasons
                },
            )
        controller.initialize("show")
        controller.season("s2")
        advanceUntilIdle()
        assertEquals("s2", state!!.seasonId)
    }

    @Test
    fun refreshingProgressPreservesSeasonRefreshAndDoesNotChangeInspection() = runTest {
        val e1 = episode("e1")
        var state: SeriesDetailState? =
            SeriesDetailState(
                "show",
                seasonId = "s1",
                episodes = SeriesResource.Ready(listOf(e1), SeriesResource.Refresh.Running),
                inspectedEpisodeId = "e1",
            )
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> listOf(e1.copy(resumeTicks = 100)) },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.recommend()
        advanceUntilIdle()
        assertEquals("e1", state!!.inspectedEpisodeId)
        assertEquals(100L, state!!.inspected!!.resumeTicks)
        assertTrue(state!!.episodes.loading)
    }

    @Test
    fun authFailureExpiresSession() = runTest {
        var state: SeriesDetailState? = SeriesDetailState("show")
        var expired = false
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> throw ApiException(401, "expired") },
                { episode(it) },
                { state },
                { state = it },
                { expired = true },
            )
        controller.season("s1")
        advanceUntilIdle()
        assertTrue(expired)
    }

    @Test
    fun newVisitRejectsOldDataEvenForSameSeries() = runTest {
        var state: SeriesDetailState? = null
        var calls = 0
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                { episode(it) },
                { state },
                { state = it },
                {},
                {
                    val call = ++calls
                    if (call == 1) withContext(NonCancellable) { delay(500) }
                    listOf(Media("s$call", "Season", "Season"))
                },
            )
        controller.initialize("show")
        runCurrent()
        controller.initialize("show")
        advanceUntilIdle()
        assertEquals("s2", state!!.seasonId)
    }

    @Test
    fun restoreFromParentSeasonWhenSeriesIdMissing() = runTest {
        val missing = episode("e1").copy(seriesId = "")
        val result =
            resolveSeriesEntry(missing) {
                if (it == "e1") missing else Media("s1", "Season", "Season", seriesId = "show")
            }
        assertEquals("show", result.seriesId)
        assertEquals("s1", result.seasonId)
    }

    @Test
    fun missingParentFailsRatherThanGuessingSeries() = runTest {
        val missing = episode("e1").copy(seriesId = "", seasonId = "")
        try {
            resolveSeriesEntry(missing) { missing }
            fail("expected missing parent")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("所属剧集"))
        }
    }

    @Test
    fun cachedMissingExternalTargetIsNotReplacedAndRestoreKeepsTheRequest() = runTest {
        val cached = listOf(episode("e1"))
        var state: SeriesDetailState? =
            SeriesDetailState("show", seasonCache = mapOf("s1" to cached))
        val controller =
            SeriesDetailController(
                this,
                { _, _ ->
                    delay(500)
                    cached
                },
                { episode(it) },
                { state },
                { state = it },
                {},
            )
        controller.season("s1", "missing")
        assertNull(state!!.inspected)
        controller.cancel()
        controller.restore()
        advanceUntilIdle()
        assertNull(state!!.inspected)
        assertTrue(state!!.entryError!!.contains("不存在"))
        controller.episode(episode("e1"))
        advanceUntilIdle()
        assertNull(state!!.entryError)
    }

    @Test
    fun openingLoadedDetailsDoesNotRefetchButExplicitRetryDoes() = runTest {
        var state: SeriesDetailState? =
            SeriesDetailState("show", episodes = SeriesResource.Ready(listOf(episode("e1"))))
        var calls = 0
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                {
                    calls++
                    episode(it)
                },
                { state },
                { state = it },
                {},
            )
        controller.episode(episode("e1"))
        advanceUntilIdle()
        controller.episode(episode("e1"))
        advanceUntilIdle()
        assertEquals(1, calls)
        controller.reloadEpisode()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun entryResolutionFailureDoesNotInvalidateAlreadyLoadedSeasonList() = runTest {
        var state: SeriesDetailState? = null
        val controller =
            SeriesDetailController(
                this,
                { _, _ -> emptyList() },
                { error("entry offline") },
                { state },
                { state = it },
                {},
                { seasons },
            )
        controller.initialize("show", entryEpisodeId = "missing")
        advanceUntilIdle()
        assertEquals(seasons, state!!.seasons.data)
        assertNull(state!!.seasons.error)
        assertEquals("entry offline", state!!.entryError)
        assertNull(state!!.seasonId)
    }
}
