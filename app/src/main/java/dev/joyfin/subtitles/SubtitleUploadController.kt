package dev.joyfin.subtitles

import android.util.Base64
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SubtitleServerTrack(val receipt: SubtitleReceipt, val label: String, val selected: Boolean)
data class SubtitleUploadState(
    val busy: Boolean = false,
    val preparing: Boolean = false,
    val packageId: String? = null,
    val fileName: String? = null,
    val tracks: List<SubtitleServerTrack> = emptyList(),
    val retained: List<SubtitleReceipt> = emptyList(),
    val result: ManagedSubtitle? = null,
    val error: String? = null,
)

/** Upload desired files first, then remove reviewed old external tracks by fresh path identity. */
class SubtitleUploadController(
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val store: LocalSubtitleStore,
    private val organizer: SubtitleOrganizer,
    private val changed: () -> Unit,
) {
    private val mutable = MutableStateFlow(SubtitleUploadState())
    val state = mutable.asStateFlow()
    private var preparation: Job? = null

    fun prepare() {
        if (mutable.value.busy) return
        val session = api.session ?: return
        val pack = organizer.state.value.pack ?: return
        preparation?.cancel()
        mutable.value = SubtitleUploadState(preparing = true, packageId = pack.id)
        preparation = scope.launch {
            try {
                val recorded = withContext(Dispatchers.IO) { store.packages(session.server, session.userId) }
                    .flatMap { it.receipts }.distinctBy { it.itemId to it.path }
                val mappings = (pack.files.mapNotNull { it.mapping } + pack.receipts.map { it.mapping })
                    .distinctBy { it.itemId to it.versionId }
                val tracks = mutableListOf<SubtitleServerTrack>()
                val retainedReceipts = mutableListOf<SubtitleReceipt>()
                for (mapping in mappings) {
                    check(api.session == session) { "账号已切换" }
                    val item = resolve(mapping)
                    val desired = pack.files.filter { it.mapping?.let { m -> m.itemId == mapping.itemId && m.versionId == mapping.versionId } == true }
                    external(item).forEach { stream ->
                        val known = recorded.firstOrNull { it.itemId == item.id && it.path == stream.path }
                        val retained = known != null && desired.any { row -> matches(known, row, pack) }
                        if (retained && known != null) retainedReceipts += known
                        if (!retained) {
                            val receipt = known ?: SubtitleReceipt(item.id, stream.path, "", mapping, pack.language, pack.forced, pack.hearingImpaired)
                            tracks += SubtitleServerTrack(receipt,
                                "${mapping.itemName} · ${stream.displayTitle.ifBlank { stream.path.substringAfterLast('/') }}",
                                known != null || pack.pendingDeletes.any { it.itemId == item.id && it.path == stream.path })
                        }
                    }
                }
                mutable.update { it.copy(preparing = false, tracks = tracks.distinctBy { t -> t.receipt.itemId to t.receipt.path }, retained = retainedReceipts) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(preparing = false, error = e.message ?: "读取服务器字幕失败") } }
        }
    }
    fun toggleDelete(item: String, path: String) {
        if (mutable.value.busy || mutable.value.preparing) return
        mutable.update { it.copy(tracks = it.tracks.map { track ->
            if (track.receipt.itemId == item && track.receipt.path == path) track.copy(selected = !track.selected) else track
        }) }
    }
    fun upload() {
        val prepared = mutable.value
        if (prepared.busy || prepared.preparing || prepared.error != null || organizer.state.value.loading || organizer.state.value.saving) return
        val session = api.session ?: return
        val pack = organizer.state.value.pack ?: return
        if (prepared.packageId != pack.id) return
        organizer.uploadBusy(true)
        mutable.value = prepared.copy(busy = true, result = null, error = null)
        scope.launch {
            var saved: ManagedSubtitle? = null
            try {
                var current = organizer.saveForUpload().copy(
                    pendingDeletes = prepared.tracks.filter { it.selected }.map { it.receipt }, cleanupError = null)
                persist(current)
                saved = current
                for (i in current.files.indices) {
                    ensureActive()
                    val row = current.files[i]
                    val mapping = row.mapping ?: continue
                    if (row.upload == "done") continue
                    mutable.update { it.copy(fileName = row.name) }
                    var stop = false
                    try {
                        check(api.session == session) { "账号已切换，回传已停止" }
                        val item = resolve(mapping)
                        val retained = prepared.retained.firstOrNull { matches(it, row, current) && it.itemId == item.id && external(item).any { track -> track.path == it.path } }
                        if (retained != null) {
                            current = current.copy(receipts = (current.receipts + retained).distinctBy { it.itemId to it.path })
                            current = replace(current, i, row.copy(upload = "done", uploadError = null))
                            persist(current); saved = current
                            continue
                        }
                        var before = row.uploadBefore
                        if (row.upload != "identifying") {
                            check(row.upload !in setOf("sending", "uncertain")) { "上次请求未确认，请在服务器核对后重新选择文件" }
                            before = external(item).map { it.path }
                            current = replace(current, i, row.copy(upload = "sending", uploadBefore = before, uploadError = null))
                            persist(current); saved = current
                            val body = withContext(Dispatchers.IO) {
                                UploadSubtitleRequest(current.language, row.name.substringAfterLast('.').lowercase(), current.forced,
                                    current.hearingImpaired, Base64.encodeToString(store.content(row.fileId), Base64.NO_WRAP))
                            }
                            api.uploadSubtitle(session, item.id, body)
                            current = replace(current, i, current.files[i].copy(upload = "identifying"))
                            persist(current); saved = current
                        }
                        val receipt = identify(session, item.id, mapping, row, current, requireNotNull(before))
                        current = current.copy(receipts = (current.receipts + receipt).distinctBy { it.itemId to it.path })
                        current = replace(current, i, current.files[i].copy(upload = "done", uploadError = null))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        stop = e is ApiException && e.status in setOf(401, 403)
                        val status = when {
                            current.files[i].upload == "identifying" -> "identifying"
                            current.files[i].upload == "sending" && e !is ApiException -> "uncertain"
                            else -> "failed"
                        }
                        current = replace(current, i, current.files[i].copy(upload = status,
                            uploadError = when {
                                status == "identifying" -> "已上传，尚未确认服务器字幕身份；重试只刷新识别，不重复上传"
                                status == "uncertain" -> "未收到回传确认，请先核对服务器，不自动重复上传"
                                e is ApiException && e.status == 403 -> "账号没有字幕管理权限"
                                else -> e.message ?: "回传失败"
                            }))
                    }
                    persist(current); saved = current; changed()
                    if (stop || api.session != session) break
                }
                // Never delete the old set while any replacement is not confirmed.
                if (current.files.filter { it.mapping != null }.all { it.upload == "done" }) {
                    for (receipt in current.pendingDeletes.toList()) {
                        try {
                            check(api.session == session) { "账号已切换" }
                            val item = api.item(receipt.itemId)
                            val candidates = external(item).filter { it.path == receipt.path }
                            require(candidates.size <= 1) { "旧字幕身份不唯一，停止删除" }
                            candidates.singleOrNull()?.let { track ->
                                if (receipt.fileId.isNotBlank()) {
                                    val expected = withContext(Dispatchers.IO) { normalize(store.content(receipt.fileId)) }
                                    val actual = api.subtitleContent(session, receipt.itemId, track.index, receipt.path.substringAfterLast('.').lowercase())
                                    require(normalize(actual) == expected) { "旧字幕内容已变化，未执行删除" }
                                }
                                api.deleteSubtitle(session, receipt.itemId, track.index)
                            }
                            // Remove the exact path, never a previously cached stream index.
                            withContext(Dispatchers.IO) { store.forgetReceipt(session.server, session.userId, receipt.itemId, receipt.path) }
                            current = current.copy(pendingDeletes = current.pendingDeletes.filterNot { it.itemId == receipt.itemId && it.path == receipt.path },
                                receipts = current.receipts.filterNot { it.itemId == receipt.itemId && it.path == receipt.path })
                            persist(current); saved = current
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            current = current.copy(cleanupError = if (e is ApiException && e.status == 403)
                                "新字幕已上传，但账号没有删除字幕权限，旧字幕仍保留" else "旧字幕删除失败：${e.message.orEmpty()}")
                            persist(current); saved = current
                            break
                        }
                    }
                } else if (current.pendingDeletes.isNotEmpty()) {
                    current = current.copy(cleanupError = "新字幕尚未全部确认，旧字幕暂不删除")
                    persist(current); saved = current
                }
                mutable.update { it.copy(busy = false, fileName = null, result = saved) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, error = e.message ?: "回传失败", result = saved) } }
            finally { saved?.let(organizer::uploaded); organizer.uploadBusy(false); changed() }
        }
    }
    private suspend fun resolve(mapping: SubtitleMapping): Media {
        val item = api.item(mapping.itemId)
        val version = item.sources.firstOrNull { it.id == mapping.versionId } ?: error("媒体版本已变化，请重新整理")
        require(version.path.isNotBlank() && version.path == mapping.versionPath) { "媒体路径已变化，请重新整理" }
        val uploadItem = if (sameId(version.id, item.id)) item else api.item(version.id)
        require(uploadItem.type in setOf("Movie", "Episode") && uploadItem.path == version.path) { "无法确认此版本的回传条目" }
        return uploadItem
    }
    private suspend fun identify(session: Session, item: String, mapping: SubtitleMapping, row: ManagedSubtitleFile, pack: ManagedSubtitle, before: List<String>): SubtitleReceipt {
        val expected = withContext(Dispatchers.IO) { normalize(store.content(row.fileId)) }
        repeat(8) { attempt ->
            check(api.session == session) { "账号已切换" }
            val new = external(api.item(item)).filter { it.path !in before && it.path.substringAfterLast('.').equals(row.name.substringAfterLast('.'), true) }
            if (new.size == 1) {
                val track = new.single()
                val actual = api.subtitleContent(session, item, track.index, row.name.substringAfterLast('.').lowercase())
                if (normalize(actual) == expected) return SubtitleReceipt(item, track.path, row.fileId, mapping, pack.language, pack.forced, pack.hearingImpaired)
            }
            if (attempt < 7) delay(500)
        }
        error("等待服务器刷新字幕信息")
    }
    private fun external(item: Media): List<MediaStream> = item.sources
        .filter { sameId(it.id, item.id) }.flatMap { it.streams }
        .filter { it.type == "Subtitle" && it.external && it.path.isNotBlank() && it.index >= 0 }
    private fun normalize(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").replace("\r\n", "\n")
    private fun matches(receipt: SubtitleReceipt, row: ManagedSubtitleFile, pack: ManagedSubtitle): Boolean =
        receipt.fileId == row.fileId && receipt.mapping == row.mapping && receipt.language == pack.language &&
            receipt.forced == pack.forced && receipt.hearingImpaired == pack.hearingImpaired
    private suspend fun persist(pack: ManagedSubtitle) = withContext(Dispatchers.IO) { store.putPackage(pack) }
    private fun replace(pack: ManagedSubtitle, i: Int, row: ManagedSubtitleFile) = pack.copy(files = pack.files.mapIndexed { index, file -> if (index == i) row else file })
    fun dismissResult() { if (!mutable.value.busy) mutable.value = SubtitleUploadState() }
    private fun sameId(a: String, b: String) = a.replace("-", "").equals(b.replace("-", ""), true)
}
