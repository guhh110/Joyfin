package dev.joyfin.subtitles

import dev.joyfin.data.subtitles.assrt.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AssrtSearchState(
    val results: List<AssrtSubtitle> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val available: Boolean = false,
    val nextPosition: Int? = null,
    val error: String? = null,
    val selected: AssrtSubtitle? = null,
    val files: List<AssrtFile> = emptyList(),
    val busyId: Long? = null,
    val transferringId: Long? = null,
)
class AssrtSearchController(
    private val scope: CoroutineScope,
    private val accounts: AssrtAccountStore,
    private val api: AssrtApi,
    private val download: LocalSubtitleDownload,
) {
    private val mutable = MutableStateFlow(AssrtSearchState())
    val state = mutable.asStateFlow()
    val requestUrls = api.requestUrls
    private var job: Job? = null
    private var detailJob: Job? = null
    private var target: SubtitleSearchContext? = null
    private var submitted = ""
    private var language = "zh"
    fun search(value: SubtitleSearchContext, query: String, lang: String) {
        val sameTarget = target == value
        cancel()
        target = value
        submitted = query.trim()
        language = lang
        mutable.update { if (sameTarget) it.copy(loading = true, error = null, nextPosition = null) else AssrtSearchState(loading = true) }
        request(0)
    }
    fun more() { mutable.value.nextPosition?.takeIf { !mutable.value.loading }?.let(::request) }
    fun retry() { target?.let { search(it, submitted, language) } }
    private fun request(position: Int) {
        if (target == null) return
        mutable.update { it.copy(loading = true, loadingMore = position > 0, error = null) }
        job = scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { accounts.load() }
                val ready = account.enabled && account.token.isNotBlank()
                mutable.update { it.copy(available = ready) }
                if (!ready) { mutable.update { it.copy(loading = false, loadingMore = false, results = emptyList(), nextPosition = null) }; return@launch }
                val results = api.search(account, submitted, position, false)
                ensureActive()
                val filtered = results.filter { item -> when (language) {
                    "zh" -> item.lang.desc.any { it in "中简繁双雙" }
                    "en" -> item.lang.desc.contains("英")
                    else -> true
                } }
                mutable.update { it.copy(loading = false, loadingMore = false,
                    results = ((if (position == 0) emptyList() else it.results) + filtered).distinctBy { item -> item.id },
                    nextPosition = (position + 15).takeIf { results.size == 15 }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, loadingMore = false, error = assrtFailure("搜索", e)) } }
        }
    }
    fun choose(item: AssrtSubtitle) {
        if (mutable.value.busyId != null || download.state.value.busy || download.state.value.needsEncoding) return
        mutable.update { it.copy(busyId = item.id, error = null) }
        detailJob = scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { accounts.load() }
                val detail = api.detail(account, item.id)
                ensureActive()
                val archive = if (detail.url.isNotBlank() && detail.filename.isNotBlank())
                    listOf(AssrtFile(detail.filename, detail.url)) else emptyList()
                val files = (archive + detail.filelist).distinctBy { it.url }
                check(files.isNotEmpty())
                mutable.update { it.copy(busyId = null, selected = detail, files = files) }
                if (files.size == 1) download(files.first())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busyId = null, error = assrtFailure("详情", e)) } }
        }
    }
    fun download(file: AssrtFile) {
        val current = target ?: return
        val item = mutable.value.selected ?: return
        if (download.state.value.busy || download.state.value.needsEncoding) return
        mutable.update { it.copy(transferringId = item.id) }
        download.open(current, file.f, file.url, "ASSRT · ${item.nativeName}", null, api::recordRequest)
    }
    fun downloadAllFiles() {
        val current = target ?: return
        val item = mutable.value.selected ?: return
        if (download.state.value.busy || download.state.value.needsEncoding || item.filelist.isEmpty()) return
        mutable.update { it.copy(transferringId = item.id) }
        download.openFiles(current, item.filelist.map { it.f to it.url },
            "ASSRT · ${item.nativeName}", null, api::recordRequest)
    }
    fun dismissFiles() { mutable.update { it.copy(selected = null, files = emptyList()) } }
    fun cancel() { job?.cancel(); detailJob?.cancel(); mutable.update { it.copy(loading = false, loadingMore = false, busyId = null, selected = null, files = emptyList()) } }
    fun clear() { cancel(); target = null; mutable.value = AssrtSearchState() }
}
