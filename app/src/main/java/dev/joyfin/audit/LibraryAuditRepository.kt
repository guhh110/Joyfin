package dev.joyfin.audit

import dev.joyfin.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

interface LibraryAuditSource {
    suspend fun works(owner: Session, scope: AuditScope, start: Int): AuditPageDto
    suspend fun episodes(owner: Session, series: String, missing: Boolean, start: Int): AuditPageDto
    suspend fun item(owner: Session, id: String): AuditItemDto
}

/** Read-only scan orchestration; no playback, file probing, metadata updates or subtitle writes. */
class LibraryAuditRepository(
    private val source: LibraryAuditSource,
    private val localSnapshot: suspend (Session) -> ((String, String) -> Boolean?),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun scan(owner: Session, scope: AuditScope, progress: suspend (AuditReport, String) -> Unit): AuditReport {
        var report = AuditReport(scope, AuditStatus.RUNNING, clock.millis())
        val findings = mutableListOf<AuditFinding>()
        val warnings = mutableListOf<String>()
        val all = mutableListOf<AuditItemDto>()
        val future = mutableSetOf<String>()
        suspend fun publish(phase: String) {
            report = report.copy(findings = findings.distinctBy { it.key }, warnings = warnings.toList(), futureEpisodes = future.size)
            progress(report, phase)
        }
        val local = try { localSnapshot(owner) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            warnings += "本机字幕索引读取失败，相关字幕状态标记为未知"
            { _: String, _: String -> null }
        }
        val works = mutableListOf<AuditItemDto>()
        try {
            pages({ start -> source.works(owner, scope, start) }) { page, total ->
                if (page.any { it.type !in setOf("Movie", "Series") }) throw IOException("Unexpected work type")
                works += page
                report = report.copy(totalWorks = total)
                publish("读取作品目录 · 已发现 ${works.size} 部")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (expired(e)) throw e; warnings += "作品目录未完整读取：${auditError(e)}" }
        for (seed in works.distinctBy { it.id }) {
            currentCoroutineContext().ensureActive()
            var work = seed
            if (seed.type == "Movie" && needsDetail(seed)) {
                try { work = checkedItem(owner, seed.id) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (expired(e)) throw e; warnings += "${seed.name.orEmpty()}：详情未完整读取（${auditError(e)}）" }
            }
            all += work
            findings += LibraryAuditRules.item(work, local)
            report = report.copy(checked = report.checked + 1)
            if (work.type == "Series") {
                try {
                    val actual = episodes(owner, work, false).filter { it.location != "Virtual" && it.placeholder != true }
                    all += actual
                    for (episode in actual) {
                        currentCoroutineContext().ensureActive()
                        var detail = episode
                        if (needsDetail(episode)) {
                            try { detail = checkedItem(owner, episode.id).let { fresh ->
                                if (fresh.type != "Episode" || !fresh.seriesId.isNullOrBlank() && fresh.seriesId != work.id)
                                    throw IOException("Unexpected episode identity")
                                fresh.copy(seriesId = work.id, seriesName = fresh.seriesName ?: work.name)
                            } }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (expired(e)) throw e; warnings += "${LibraryAuditRules.target(episode).name}：详情未完整读取（${auditError(e)}）" }
                        }
                        if (LibraryAuditRules.future(detail, LocalDate.now(clock))) future += detail.id
                        findings += LibraryAuditRules.item(detail, local)
                        report = report.copy(checked = report.checked + 1)
                        if (report.checked % 20 == 0) publish("检查 ${work.name.orEmpty()} · 剧集信息")
                    }
                    var missing = emptyList<AuditItemDto>()
                    try { missing = episodes(owner, work, true) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (expired(e)) throw e; warnings += "${work.name.orEmpty()}：服务器缺失集查询未完成（${auditError(e)}）" }
                    missing.filter { LibraryAuditRules.future(it, LocalDate.now(clock)) }.forEach { future += it.id }
                    findings += LibraryAuditRules.episodes(work, actual, missing, LocalDate.now(clock))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (expired(e)) throw e; warnings += "${work.name.orEmpty()}：剧集读取未完成（${auditError(e)}）" }
            }
            report = report.copy(processedWorks = report.processedWorks + 1)
            publish("检查 ${work.name.orEmpty()}")
        }
        findings += LibraryAuditRules.duplicates(all)
        publish("完成归组")
        return report.copy(status = if (warnings.isEmpty()) AuditStatus.COMPLETE else AuditStatus.PARTIAL,
            finishedAt = clock.millis())
    }

    private fun needsDetail(item: AuditItemDto) = item.type in setOf("Movie", "Episode") &&
        item.location != "Virtual" && item.placeholder != true &&
        (item.sources == null || item.sources.any { it.streams == null } && (item.sources.size != 1 || item.streams == null))

    private suspend fun checkedItem(owner: Session, id: String): AuditItemDto = source.item(owner, id).also {
        if (it.id != id) throw IOException("Unexpected item identity")
    }

    private suspend fun episodes(owner: Session, series: AuditItemDto, missing: Boolean): List<AuditItemDto> {
        val items = mutableListOf<AuditItemDto>()
        pages({ start -> source.episodes(owner, series.id, missing, start) }) { page, _ ->
            page.forEach { episode ->
                if (episode.type != "Episode" || !episode.seriesId.isNullOrBlank() && episode.seriesId != series.id)
                    throw IOException("Unexpected episode identity")
                items += episode.copy(seriesId = series.id, seriesName = episode.seriesName ?: series.name)
            }
        }
        return items
    }

    /** Offsets advance by wire rows, not deduplicated rows; repeated/short pages cannot look complete. */
    private suspend fun pages(fetch: suspend (Int) -> AuditPageDto, consume: suspend (List<AuditItemDto>, Int?) -> Unit) {
        var offset = 0
        var total: Int? = null
        val seen = mutableSetOf<String>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = fetch(offset)
            val rows = page.items ?: throw IOException("Missing Items field")
            if (page.total != null && page.total < 0) throw IOException("Invalid total")
            if (total != null && page.total != null && total != page.total) throw IOException("Library changed during scan")
            total = page.total ?: total
            if (rows.isEmpty()) {
                if (total != null && offset < total) throw IOException("Incomplete page")
                break
            }
            if (total != null && offset.toLong() + rows.size > total) throw IOException("Inconsistent total")
            if (rows.any { it.id.isBlank() || !seen.add(it.id) }) throw IOException("Repeated or invalid page")
            consume(rows, total)
            offset += rows.size
            if (total != null && offset >= total) break
            // A server may cap page sizes below our requested limit; read until an empty page.
        }
    }

    suspend fun recheck(owner: Session, finding: AuditFinding): List<AuditFinding> {
        return when (finding.category) {
            AuditCategory.EPISODES -> {
                val series = checkedItem(owner, finding.target.id)
                LibraryAuditRules.episodes(series, episodes(owner, series, false), episodes(owner, series, true), LocalDate.now(clock))
            }
            AuditCategory.VERSIONS -> if (finding.code == "duplicate-entries") {
                val items = finding.relatedIds.distinct().map { checkedItem(owner, it) }.toMutableList()
                for (seriesId in items.mapNotNull { it.seriesId }.distinct()) {
                    if (items.none { it.id == seriesId }) items += checkedItem(owner, seriesId)
                }
                LibraryAuditRules.duplicates(items.filter { it.location != "Virtual" && it.placeholder != true })
                    .filter { it.relatedIds.any { id -> id in finding.relatedIds } }
            } else itemFindings(owner, finding)
            else -> itemFindings(owner, finding)
        }
    }

    private suspend fun itemFindings(owner: Session, finding: AuditFinding): List<AuditFinding> {
        val local = if (finding.category == AuditCategory.SUBTITLES) localSnapshot(owner) else { _: String, _: String -> null }
        return LibraryAuditRules.item(checkedItem(owner, finding.target.id), local).filter { it.category == finding.category }
    }
}

internal fun expired(error: Exception) = error is ApiException && error.status == 401
internal fun auditError(error: Exception): String = when (error) {
    is ApiException -> "服务器请求失败（HTTP ${error.status}）"
    else -> "读取失败，请检查连接后重试"
}
