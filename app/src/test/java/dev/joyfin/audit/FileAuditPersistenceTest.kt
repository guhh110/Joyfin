package dev.joyfin.audit

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAuditPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reportsAreIsolatedByServerAccountAndScopeAndSurviveReopening() = runTest {
        val directory = temporary.newFolder()
        val storage = FileAuditPersistence(directory)
        val finding = LibraryAuditRules.item(auditMovie()) { _, _ -> false }.first()
        val saved = AuditSaved(AuditReport(AuditScope("library", "媒体库"), AuditStatus.CANCELED,
            findings = listOf(finding)), setOf(finding.fingerprint))
        storage.save(auditAccount, saved)
        assertEquals(saved, FileAuditPersistence(directory).load(auditAccount.copy(server = auditAccount.server + "/"), saved.report.scope))
        assertNull(storage.load(auditAccount.copy(userId = "other"), saved.report.scope))
        assertNull(storage.load(auditAccount.copy(server = "https://other.invalid"), saved.report.scope))
        assertNull(storage.load(auditAccount, AuditScope()))
        val updated = saved.copy(report = saved.report.copy(status = AuditStatus.COMPLETE))
        storage.save(auditAccount, updated)
        assertEquals(updated, storage.load(auditAccount, saved.report.scope))
        assertTrue(directory.listFiles()!!.all { it.name.matches(Regex("[a-f0-9]{64}-[a-f0-9]{64}\\.json")) })
    }
}
