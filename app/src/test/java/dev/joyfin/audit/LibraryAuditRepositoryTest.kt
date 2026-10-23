package dev.joyfin.audit

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal val auditClock: Clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC)
internal val auditAccount = Session("https://example.invalid/jellyfin", "test-token", "user", "Viewer")
internal fun auditMovie(id: String = "movie") = AuditItemDto(id, id, "Movie", "简介", 2026, 100,
    images = mapOf("Primary" to "tag"), sources = listOf(AuditSourceDto("v1", "v1", "mkv", 1000, 100,
        listOf(AuditStreamDto("Video", "hevc", width = 1920, height = 1080)))))

internal open class FakeAuditSource : LibraryAuditSource {
    val starts = mutableListOf<Int>()
    var works: suspend (Int) -> AuditPageDto = { AuditPageDto(emptyList(), 0) }
    var detail: suspend (String) -> AuditItemDto = { auditMovie(it) }
    var episodes: suspend (String, Boolean, Int) -> AuditPageDto = { _, _, _ -> AuditPageDto(emptyList(), 0) }
    override suspend fun works(owner: Session, scope: AuditScope, start: Int): AuditPageDto { starts += start; return works(start) }
    override suspend fun item(owner: Session, id: String) = detail(id)
    override suspend fun episodes(owner: Session, series: String, missing: Boolean, start: Int) = episodes(series, missing, start)
}

class LibraryAuditRepositoryTest {
    private fun repository(source: FakeAuditSource, local: suspend (Session) -> ((String, String) -> Boolean?) = { { _, _ -> false } }) =
        LibraryAuditRepository(source, local, auditClock)
    private suspend fun scan(source: FakeAuditSource) = repository(source).scan(auditAccount, AuditScope()) { _, _ -> }

    @Test fun paginationUsesReturnedRowCountEvenWhenServerCapsPagesBelowRequestedSize() = runTest {
        val source = FakeAuditSource().apply { works = { start -> AuditPageDto(listOf(auditMovie("m$start")), 3) } }
        val report = scan(source)
        assertEquals(listOf(0, 1, 2), source.starts)
        assertEquals(AuditStatus.COMPLETE, report.status)
        assertEquals(3, report.checked)
    }

    @Test fun unknownTotalsRequireAnExplicitEmptyPageRatherThanAssumingAShortPageIsLast() = runTest {
        val source = FakeAuditSource().apply { works = { start -> if (start < 2) AuditPageDto(listOf(auditMovie("m$start"))) else AuditPageDto(emptyList()) } }
        assertEquals(2, scan(source).checked)
        assertEquals(listOf(0, 1, 2), source.starts)
    }

    @Test fun anAbsentItemsFieldIsAnIncompleteResponseButAnExplicitEmptyLibraryIsComplete() = runTest {
        val invalid = FakeAuditSource().apply { works = { AuditPageDto() } }
        assertEquals(AuditStatus.PARTIAL, scan(invalid).status)
        assertEquals(AuditStatus.COMPLETE, scan(FakeAuditSource()).status)
    }

    @Test fun repeatedPrematureOrChangingPagesNeverProduceACompleteReport() = runTest {
        val repeat = FakeAuditSource().apply { works = { AuditPageDto(listOf(auditMovie()), 2) } }
        val short = FakeAuditSource().apply { works = { start -> AuditPageDto(if (start == 0) listOf(auditMovie()) else emptyList(), 2) } }
        val changed = FakeAuditSource().apply { works = { start -> AuditPageDto(listOf(auditMovie("m$start")), if (start == 0) 2 else 3) } }
        for (source in listOf(repeat, short, changed)) {
            val report = scan(source)
            assertEquals(AuditStatus.PARTIAL, report.status)
            assertEquals(1, report.checked)
            assertTrue(report.findings.isNotEmpty())
        }
    }

    @Test fun detailRequestsAreOnlyMadeWhenTechnicalDataIsUnknownAndUseItsActualVersion() = runTest {
        var details = 0
        val source = FakeAuditSource().apply {
            works = { AuditPageDto(listOf(auditMovie("known"), auditMovie("unknown").copy(sources = null)), 2) }
            detail = { id -> details++; auditMovie(id).copy(sources = listOf(auditMovie().sources!!.single().copy(id = "actual-version"))) }
        }
        val report = scan(source)
        assertEquals(1, details)
        assertTrue(report.findings.any { it.target.id == "unknown" && it.sourceId == "actual-version" })
    }

    @Test fun aFailedLocalSubtitleIndexMakesSubtitleStateUnknownWithoutLeakingTheError() = runTest {
        val source = FakeAuditSource().apply { works = { AuditPageDto(listOf(auditMovie()), 1) } }
        val report = repository(source) { throw IllegalStateException("https://secret.invalid/token") }
            .scan(auditAccount, AuditScope()) { _, _ -> }
        assertEquals(AuditStatus.PARTIAL, report.status)
        assertEquals(AuditConfidence.UNKNOWN, report.findings.single { it.category == AuditCategory.SUBTITLES }.confidence)
        assertFalse(report.toString().contains("secret.invalid"))
    }

    @Test fun failingAnEpisodePageDoesNotInferGapsFromAPartiallyReadSeason() = runTest {
        val source = FakeAuditSource().apply {
            works = { AuditPageDto(listOf(AuditItemDto("series", "剧", "Series")), 1) }
            episodes = { _, _, start -> if (start == 0) AuditPageDto(listOf(AuditItemDto("e1", "一", "Episode", season = 1, episode = 1)), 2)
                else throw java.io.IOException("offline") }
        }
        val report = scan(source)
        assertEquals(AuditStatus.PARTIAL, report.status)
        assertTrue(report.findings.none { it.category == AuditCategory.EPISODES })
    }

    @Test fun aFailedServerMissingQueryKeepsObservedGapsAndReportsIncompleteCoverage() = runTest {
        val source = FakeAuditSource().apply {
            works = { AuditPageDto(listOf(AuditItemDto("series", "剧", "Series")), 1) }
            episodes = { _, missing, _ -> if (missing) throw ApiException(404, "private response")
                else AuditPageDto(listOf(1, 3).map { AuditItemDto("e$it", "集", "Episode", season = 1, episode = it, sources = emptyList()) }, 2) }
        }
        val report = scan(source)
        assertEquals(AuditStatus.PARTIAL, report.status)
        assertEquals("gap:1:2:2", report.findings.single { it.category == AuditCategory.EPISODES }.code)
        assertFalse(report.warnings.any { it.contains("private response") })
    }

    @Test fun authenticationErrorsEscapeToTheControllerRatherThanBecomingAHealthyEmptyResult() = runTest {
        val source = FakeAuditSource().apply { works = { throw ApiException(401, "expired") } }
        try { scan(source); fail("Expected authentication failure") }
        catch (e: ApiException) { assertEquals(401, e.status) }
    }

    @Test fun cancellationStopsBeforeAnyFurtherWorkIsFetched() = runTest {
        val source = FakeAuditSource().apply { works = { start -> AuditPageDto(listOf(auditMovie("m$start")), 5) } }
        try {
            repository(source).scan(auditAccount, AuditScope()) { _, _ -> throw CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(0), source.starts)
    }

    @Test fun virtualEpisodesCannotBecomePhysicalSubtitleOrDuplicateFindings() = runTest {
        val source = FakeAuditSource().apply {
            works = { AuditPageDto(listOf(AuditItemDto("series", "剧", "Series")), 1) }
            episodes = { _, missing, _ ->
                val rows = if (missing) emptyList() else listOf("one", "two").map {
                    auditMovie(it).copy(type = "Episode", season = 1, episode = 1, location = "Virtual")
                }
                AuditPageDto(rows, rows.size)
            }
        }
        val report = scan(source)
        assertEquals(1, report.checked)
        assertTrue(report.findings.none { it.category in setOf(AuditCategory.SUBTITLES, AuditCategory.VERSIONS) })
    }

    @Test fun episodeRecheckDoesNotDependOnTheLocalSubtitleIndex() = runTest {
        val series = AuditItemDto("series", "剧", "Series")
        val source = FakeAuditSource().apply { detail = { series } }
        val finding = AuditFinding("gap:1:2:2", AuditCategory.EPISODES, LibraryAuditRules.target(series),
            "缺集", emptyList(), AuditConfidence.REVIEW)
        val result = repository(source) { error("Unreadable subtitle index") }.recheck(auditAccount, finding)
        assertTrue(result.isEmpty())
    }
}
