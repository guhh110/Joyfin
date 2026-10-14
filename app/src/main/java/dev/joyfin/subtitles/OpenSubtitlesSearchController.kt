package dev.joyfin.subtitles

import dev.joyfin.data.subtitles.opensubtitles.*
import dev.joyfin.data.subtitles.subtitleFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OpenSubtitlesSearchState(
    val results: List<OpenSubtitlesEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val available: Boolean = false,
    val nextPage: Int? = null,
    val error: String? = null,
    val selected: OpenSubtitlesEntry? = null,
    val busyFile: Int? = null,
    val transferringFile: Int? = null,
    val remaining: Int? = null,
)
class OpenSubtitlesSearchController(
    private val scope: CoroutineScope,
    private val accounts: OpenSubtitlesAccountStore,
    private val api: OpenSubtitlesApi,
    private val download: LocalSubtitleDownload,
) {
    private val mutable = MutableStateFlow(OpenSubtitlesSearchState())
    val state = mutable.asStateFlow()
    val requestUrls = api.requestUrls
    private var job: Job? = null
    private var downloadJob: Job? = null
    private var target: SubtitleSearchContext? = null
    private var submitted = ""
    private var language = "zh"
    private var successfulParams = emptyMap<String, String>()
    fun search(value: SubtitleSearchContext, query: String, lang: String) {
        val sameTarget = target == value
        cancel()
        target = value
        submitted = query.trim()
        language = lang
        mutable.update { if (sameTarget) it.copy(loading = true, error = null, nextPage = null) else OpenSubtitlesSearchState(loading = true) }
        request(1)
    }
    fun more() { mutable.value.nextPage?.takeIf { !mutable.value.loading }?.let(::request) }
    fun retry() { target?.let { search(it, submitted, language) } }
    private fun request(page: Int) {
        val current = target ?: return
        mutable.update { it.copy(loading = true, loadingMore = page > 1, error = null) }
        job = scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { accounts.load() }
                val ready = account.enabled && account.apiKey.isNotBlank()
                mutable.update { it.copy(available = ready) }
                if (!ready) { mutable.update { it.copy(loading = false, loadingMore = false, results = emptyList(), nextPage = null) }; return@launch }
                val params = if (page > 1) successfulParams.toMutableMap().apply { put("page", page.toString()) }
                else linkedMapOf<String, String>().apply {
                    if (current.work != null) put("type", if (current.work.type == "Series") "episode" else "movie")
                    current.season?.let { put("season_number", it.toString()) }
                    if (current.season == null && (current.work?.year ?: 0) > 0) put("year", (current.work?.year ?: 0).toString())
                    if (language.isNotBlank()) put("languages", if (language == "zh") "zh-cn,zh-tw" else language)
                }
                if (page == 1) params["query"] = submitted
                val response = api.search(account, params)
                ensureActive()
                successfulParams = params.filterKeys { it != "page" }
                mutable.update { it.copy(loading = false, loadingMore = false,
                    results = ((if (page == 1) emptyList() else it.results) + response.data).distinctBy { item -> item.id },
                    nextPage = (page + 1).takeIf { page < response.totalPages }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, loadingMore = false, error = subtitleFailure(e)) } }
        }
    }
    fun choose(item: OpenSubtitlesEntry) {
        if (mutable.value.busyFile != null || download.state.value.busy || download.state.value.needsEncoding) return
        if (item.attributes.files.isEmpty()) {
            mutable.update { it.copy(error = "该结果没有可下载文件") }
            return
        }
        mutable.update { it.copy(selected = item) }
        item.attributes.files.singleOrNull()?.let(::download)
    }
    fun dismissFiles() { downloadJob?.cancel(); mutable.update { it.copy(selected = null, busyFile = null, transferringFile = null) } }
    fun download(file: OpenSubtitlesFile) {
        val current = target ?: return
        val selected = mutable.value.selected ?: return
        if (mutable.value.busyFile != null || download.state.value.busy || download.state.value.needsEncoding) return
        mutable.update { it.copy(busyFile = file.id, error = null) }
        downloadJob = scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { accounts.load() }
                val resolved = api.download(account, file.id)
                ensureActive()
                download.open(current, resolved.name, resolved.link, "OpenSubtitles · ${selected.attributes.release}", selected.attributes.language, api::recordRequest)
                mutable.update { it.copy(busyFile = null, transferringFile = file.id, remaining = resolved.remaining) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busyFile = null, error = subtitleFailure(e)) } }
        }
    }
    fun cancel() { job?.cancel(); downloadJob?.cancel(); mutable.update { it.copy(loading = false, loadingMore = false, busyFile = null, selected = null) } }
    fun clear() { cancel(); target = null; mutable.value = OpenSubtitlesSearchState() }
}
