package dev.joyfin.audit

import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

private class MemoryAuditPersistence : AuditPersistence {
    val rows = mutableMapOf<String, AuditSaved>()
    val writes = mutableListOf<AuditSaved>()
    private fun key(owner: Session, scope: AuditScope) = owner.server + ":" + owner.userId + ":" + scope.id
    override suspend fun load(owner: Session, scope: AuditScope) = rows[key(owner, scope)]
    override suspend fun save(owner: Session, saved: AuditSaved) {
        rows[key(owner, saved.report.scope)] = saved
        writes += saved
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAuditControllerTest {
    @Test fun lateCanceledRequestsCannotPublishIntoANewScopeOrExpireANewAccount() = runTest {
        var owner = auditAccount
        var expired = 0
        val source = FakeAuditSource().apply { works = {
            withContext(NonCancellable) { delay(1000) }
            throw ApiException(401, "old session")
        } }
        val storage = MemoryAuditPersistence()
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { owner }, { expired++ }, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope("old", "旧库")); runCurrent()
        controller.start(); runCurrent()
        owner = owner.copy(userId = "new-user")
        controller.open(AuditScope("new", "新库")); advanceUntilIdle()
        assertEquals("new", controller.state.value.report.scope.id)
        assertEquals(AuditStatus.READY, controller.state.value.report.status)
        assertFalse(controller.state.value.running)
        assertEquals(0, expired)
        assertTrue(storage.writes.none { it.report.status == AuditStatus.COMPLETE })
    }

    @Test fun repeatedStartDoesNotDuplicateTheScanAndCancelKeepsAPartialSnapshot() = runTest {
        val source = FakeAuditSource().apply { works = { delay(1000); AuditPageDto(listOf(auditMovie()), 1) } }
        val storage = MemoryAuditPersistence()
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { auditAccount }, {}, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent()
        controller.start(); controller.start(); runCurrent()
        assertEquals(listOf(0), source.starts)
        controller.cancel(); advanceUntilIdle()
        assertEquals(AuditStatus.CANCELED, controller.state.value.report.status)
        assertEquals(AuditStatus.CANCELED, storage.writes.last().report.status)
        assertFalse(controller.state.value.running)
    }

    @Test fun refreshKeepsThePreviousReportUntilTheNewResultArrives() = runTest {
        val storage = MemoryAuditPersistence()
        val oldFinding = LibraryAuditRules.item(auditMovie()) { _, _ -> false }.first()
        storage.save(auditAccount, AuditSaved(AuditReport(status = AuditStatus.COMPLETE, checked = 1, findings = listOf(oldFinding))))
        val source = FakeAuditSource().apply { works = { delay(1000); AuditPageDto(emptyList(), 0) } }
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { auditAccount }, {}, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent()
        controller.start(); runCurrent()
        assertEquals(listOf(oldFinding), controller.state.value.report.findings)
        assertTrue(controller.state.value.running)
        advanceUntilIdle()
        assertTrue(controller.state.value.report.findings.isEmpty())
        assertEquals(AuditStatus.COMPLETE, controller.state.value.report.status)
    }

    @Test fun ignoresPersistButChangedEvidenceBecomesVisibleAndRecheckCanResolveOnlyItsCategory() = runTest {
        val source = FakeAuditSource().apply { works = { AuditPageDto(listOf(auditMovie()), 1) } }
        val storage = MemoryAuditPersistence()
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { auditAccount }, {}, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent(); controller.start(); advanceUntilIdle()
        val subtitle = controller.state.value.report.findings.single { it.category == AuditCategory.SUBTITLES }
        controller.ignore(subtitle); advanceUntilIdle()
        assertFalse(controller.state.value.visible.any { it.key == subtitle.key })
        controller.showIgnored(true)
        assertTrue(controller.state.value.visible.any { it.key == subtitle.key })
        controller.showIgnored(false)
        source.detail = { auditMovie().copy(sources = listOf(auditMovie().sources!!.single().copy(streams = null))) }
        controller.recheck(subtitle); advanceUntilIdle()
        val unknown = controller.state.value.visible.single { it.category == AuditCategory.SUBTITLES }
        assertEquals(AuditConfidence.UNKNOWN, unknown.confidence)
        source.detail = {
            val version = auditMovie().sources!!.single().copy(streams = listOf(AuditStreamDto("Subtitle")))
            auditMovie().copy(sources = listOf(version))
        }
        controller.recheck(unknown); advanceUntilIdle()
        assertTrue(controller.state.value.report.findings.none { it.category == AuditCategory.SUBTITLES })
    }

    @Test fun aFailedRecheckPreservesEvidenceAndAWriteFailureDoesNotDiscardVisibleResults() = runTest {
        val source = FakeAuditSource().apply {
            works = { AuditPageDto(listOf(auditMovie()), 1) }
            detail = { throw java.io.IOException("private path") }
        }
        val storage = object : AuditPersistence {
            override suspend fun load(owner: Session, scope: AuditScope): AuditSaved? = null
            override suspend fun save(owner: Session, saved: AuditSaved) { throw java.io.IOException("disk full") }
        }
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { auditAccount }, {}, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent(); controller.start(); advanceUntilIdle()
        assertNotNull(controller.state.value.cacheError)
        val finding = controller.state.value.report.findings.first()
        controller.recheck(finding); advanceUntilIdle()
        assertTrue(controller.state.value.report.findings.contains(finding))
        assertNotNull(controller.state.value.error)
        assertNull(controller.state.value.rechecking)
        assertFalse(controller.state.value.error!!.contains("private path"))
    }

    @Test fun authenticationFailureOfTheCurrentRunExpiresExactlyOnce() = runTest {
        var expired = 0
        val source = FakeAuditSource().apply { works = { throw ApiException(401, "expired") } }
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            MemoryAuditPersistence(), { auditAccount }, { expired++ }, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent(); controller.start(); advanceUntilIdle()
        assertEquals(1, expired)
        assertFalse(controller.state.value.running)
    }

    @Test fun reopeningWaitsForTheCanceledSnapshotRatherThanReadingAnOlderCache() = runTest {
        var cached: AuditSaved? = null
        val storage = object : AuditPersistence {
            override suspend fun load(owner: Session, scope: AuditScope) = cached
            override suspend fun save(owner: Session, saved: AuditSaved) { delay(500); cached = saved }
        }
        val source = FakeAuditSource().apply { works = { delay(1000); AuditPageDto(emptyList(), 0) } }
        val controller = LibraryAuditController(this, LibraryAuditRepository(source, { { _, _ -> false } }, auditClock),
            storage, { auditAccount }, {}, auditClock, StandardTestDispatcher(testScheduler))
        controller.open(AuditScope()); runCurrent(); controller.start(); runCurrent()
        controller.close(); controller.open(AuditScope()); runCurrent()
        assertTrue(controller.state.value.loading)
        advanceUntilIdle()
        assertEquals(AuditStatus.CANCELED, controller.state.value.report.status)
        assertFalse(controller.state.value.loading)
    }
}
