package dev.joyfin.audit

import dev.joyfin.data.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock

interface AuditPersistence {
    suspend fun load(owner: Session, scope: AuditScope): AuditSaved?
    suspend fun save(owner: Session, saved: AuditSaved)
}

data class AuditUiState(
    val report: AuditReport = AuditReport(), val ignored: Set<String> = emptySet(),
    val category: AuditCategory? = null, val showIgnored: Boolean = false,
    val loading: Boolean = false, val running: Boolean = false, val phase: String = "",
    val progress: AuditReport? = null, val rechecking: String? = null,
    val error: String? = null, val cacheError: String? = null,
) {
    val visible: List<AuditFinding> get() = report.findings.filter {
        (category == null || it.category == category) && (showIgnored || it.fingerprint !in ignored)
    }
}

/** Owns cancellation and account/scope fencing; saved snapshots always retain their captured owner. */
class LibraryAuditController(
    private val scope: CoroutineScope,
    private val repository: LibraryAuditRepository,
    private val persistence: AuditPersistence,
    private val session: () -> Session?,
    private val onExpired: () -> Unit,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutable = MutableStateFlow(AuditUiState())
    val state = mutable.asStateFlow()
    @Volatile private var owner: Session? = null
    @Volatile private var generation = 0L
    private var job: Job? = null
    private var saving: Job? = null
    @Volatile private var latest: AuditReport? = null

    fun open(target: AuditScope) {
        close()
        val account = session()
        owner = account
        if (account == null) {
            mutable.value = AuditUiState(report = AuditReport(target))
            return
        }
        val token = generation
        val previousSave = saving
        mutable.value = AuditUiState(report = AuditReport(target), loading = true)
        job = scope.launch {
            try {
                previousSave?.join()
                val saved = persistence.load(account, target)
                ensureActive()
                if (!current(token, account)) return@launch
                if (saved != null && saved.report.rulesVersion == 1 && saved.report.scope.id == target.id) {
                    val report = saved.report.copy(scope = target).let {
                        if (it.status == AuditStatus.RUNNING) it.copy(status = AuditStatus.PARTIAL) else it
                    }
                    mutable.update { it.copy(report = report, ignored = saved.ignored, loading = false) }
                } else mutable.update { it.copy(loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(token, account)) mutable.update { it.copy(loading = false, cacheError = "本机体检记录无法读取，可重新扫描") }
            }
        }
    }

    private fun current(token: Long, account: Session) = token == generation && owner == account && session() == account
    fun filter(category: AuditCategory?) { mutable.update { it.copy(category = category) } }
    fun showIgnored(value: Boolean) { mutable.update { it.copy(showIgnored = value) } }

    fun start() {
        val account = owner ?: return
        val before = mutable.value
        if (before.loading || before.running || before.rechecking != null || account != session()) return
        job?.cancel()
        generation++
        val token = generation
        latest = AuditReport(before.report.scope, AuditStatus.RUNNING, clock.millis())
        mutable.update { it.copy(running = true, progress = latest, error = null, cacheError = null, phase = "读取目录") }
        job = scope.launch {
            try {
                val report = withContext(worker) { repository.scan(account, before.report.scope) { partial, phase ->
                    withContext(scope.coroutineContext.minusKey(Job)) {
                        if (current(token, account)) {
                            latest = partial
                            mutable.update { it.copy(progress = partial, phase = phase,
                                report = if (before.report.status == AuditStatus.READY) partial else it.report) }
                        }
                    }
                } }
                ensureActive()
                if (!current(token, account)) return@launch
                latest = report
                mutable.update { it.copy(report = report, progress = null, running = false, phase = "") }
                persist(account, token)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current(token, account)) return@launch
                if (expired(e)) { close(); onExpired() }
                else {
                    val report = (latest ?: before.report).copy(status = AuditStatus.PARTIAL, finishedAt = clock.millis())
                    mutable.update { it.copy(report = report, running = false, progress = null, error = auditError(e)) }
                    persist(account, token)
                }
            }
        }
    }

    fun cancel() {
        if (!mutable.value.running) return
        generation++
        job?.cancel()
        val report = (latest ?: mutable.value.report).copy(status = AuditStatus.CANCELED, finishedAt = clock.millis())
        mutable.update { it.copy(report = report, progress = null, running = false, phase = "") }
        owner?.takeIf { it == session() }?.let { persist(it, generation) }
    }

    fun ignore(finding: AuditFinding) {
        if (mutable.value.running || mutable.value.rechecking != null) return
        val account = owner?.takeIf { it == session() } ?: return
        mutable.update { it.copy(ignored = if (finding.fingerprint in it.ignored) it.ignored - finding.fingerprint else it.ignored + finding.fingerprint) }
        persist(account, generation)
    }

    fun recheck(finding: AuditFinding) {
        val account = owner?.takeIf { it == session() } ?: return
        if (mutable.value.running || mutable.value.loading || mutable.value.rechecking != null) return
        val token = generation
        mutable.update { it.copy(rechecking = finding.key, error = null) }
        job = scope.launch {
            try {
                val findings = withContext(worker) { repository.recheck(account, finding) }
                ensureActive()
                if (!current(token, account)) return@launch
                mutable.update { state ->
                    val remaining = state.report.findings.filterNot { old ->
                        when {
                            finding.code == "duplicate-entries" -> old.key == finding.key
                            finding.category == AuditCategory.VERSIONS -> old.target.id == finding.target.id &&
                                old.category == finding.category && old.code != "duplicate-entries"
                            else -> old.target.id == finding.target.id && old.category == finding.category
                        }
                    }
                    state.copy(report = state.report.copy(findings = (remaining + findings).distinctBy { it.key }), rechecking = null)
                }
                persist(account, token)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current(token, account)) return@launch
                if (expired(e)) { close(); onExpired() }
                else mutable.update { it.copy(rechecking = null, error = "复查未完成，保留原结果。${auditError(e)}") }
            }
        }
    }

    private fun persist(account: Session, token: Long) {
        val snapshot = mutable.value.let { AuditSaved(it.report, it.ignored) }
        val previous = saving
        saving = scope.launch {
            previous?.join()
            try { persistence.save(account, snapshot) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(token, account)) mutable.update { it.copy(cacheError = "本次结果未能保存到本机，当前仍可查看") }
            }
        }
    }

    fun close() {
        if (mutable.value.running) cancel()
        generation++
        job?.cancel()
        mutable.update { it.copy(loading = false, running = false, rechecking = null, progress = null) }
    }

    fun media(finding: AuditFinding) = finding.target.media().takeIf {
        owner == session() && mutable.value.report.findings.any { row -> row.key == finding.key }
    }
}
