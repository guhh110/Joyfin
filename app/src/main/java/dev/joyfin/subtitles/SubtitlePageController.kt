package dev.joyfin.subtitles

import android.content.Context
import dev.joyfin.browse.DetailUiState
import dev.joyfin.browse.data
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import dev.joyfin.data.subtitles.assrt.*
import dev.joyfin.data.subtitles.opensubtitles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SubtitleScreen { MANAGEMENT, SEARCH, ORGANIZE }
data class SubtitlePageState(
    val screen: SubtitleScreen = SubtitleScreen.MANAGEMENT,
    val packages: List<ManagedSubtitle> = emptyList(),
    val context: SubtitleSearchContext? = null,
    val filterWork: Boolean = false,
    val seasons: List<Media> = emptyList(),
    val query: String = "",
    val language: String = "zh",
    val loading: Boolean = false,
    val error: String? = null,
    val works: List<Media> = emptyList(),
    val workQuery: String = "",
    val workLoading: Boolean = false,
    val workNext: Int? = null,
    val workError: String? = null,
)

/** One independent module; search state survives visits to management and organization. */
class SubtitlePageController(
    context: Context,
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val expired: () -> Unit,
) {
    private val mutable = MutableStateFlow(SubtitlePageState())
    val state = mutable.asStateFlow()
    val serverAddress: String get() = api.session?.server.orEmpty()
    private val store = LocalSubtitleStore.get(context)
    val assrtAccounts = AssrtAccountStore(context)
    val openSubtitlesAccounts = OpenSubtitlesAccountStore(context)
    val assrtApi = AssrtApi()
    val openSubtitlesApi = OpenSubtitlesApi()
    val download = LocalSubtitleDownload(scope, store, ::reloadPackages)
    val organizer = SubtitleOrganizer(scope, api, store, ::reloadPackages)
    val uploader = SubtitleUploadController(scope, api, store, organizer, ::reloadPackages)
    val assrt = AssrtSearchController(scope, assrtAccounts, assrtApi, download)
    val openSubtitles = OpenSubtitlesSearchController(scope, openSubtitlesAccounts, openSubtitlesApi, download)
    private var metadataJob: Job? = null
    private var localJob: Job? = null
    private var worksJob: Job? = null
    private var searched = false

    fun open(detail: DetailUiState? = null) {
        close()
        searched = false
        val session = api.session ?: return
        assrtApi.clearRequestUrls()
        openSubtitlesApi.clearRequestUrls()
        val owner = LocalSubtitleStore.owner(session.server, session.userId)
        mutable.value = SubtitlePageState(context = SubtitleSearchContext(owner), loading = detail?.item != null)
        reloadPackages()
        if (detail?.item == null) return
        metadataJob = scope.launch {
            try {
                val seed = requireNotNull(detail.item)
                val series = detail.series
                val work = if (series != null && seed.id != series.seriesId) api.item(series.seriesId) else seed
                val seasons = if (work.type == "Series") series?.seasons?.data ?: api.seasons(work.id) else emptyList()
                val season = seasons.firstOrNull { it.id == series?.seasonId } ?: seasons.firstOrNull()
                ensureActive()
                mutable.update { it.copy(loading = false, seasons = seasons, filterWork = true,
                    context = SubtitleSearchContext(owner, work, season), query = work.originalTitle.ifBlank { work.name }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
        }
    }
    fun reloadPackages() {
        val session = api.session ?: return
        val owner = mutable.value.context?.owner ?: return
        if (LocalSubtitleStore.owner(session.server, session.userId) != owner) return
        localJob?.cancel()
        localJob = scope.launch {
            try {
                val packages = withContext(Dispatchers.IO) { store.packages(session.server, session.userId) }
                ensureActive()
                if (mutable.value.context?.owner == owner) mutable.update { it.copy(packages = packages) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
        }
    }
    fun allWorks() { mutable.update { it.copy(filterWork = false, seasons = emptyList(), context = it.context?.let { value -> SubtitleSearchContext(value.owner) }) } }
    fun management() { mutable.update { it.copy(screen = SubtitleScreen.MANAGEMENT) }; reloadPackages() }
    fun showSearch() {
        mutable.update { it.copy(screen = SubtitleScreen.SEARCH) }
        if (mutable.value.query.isNotBlank() && !searched) search()
    }
    fun organize(id: String) {
        if (uploader.state.value.busy || organizer.state.value.saving) return
        val pack = mutable.value.packages.firstOrNull { it.id == id } ?: return
        organizer.open(pack)
        mutable.update { it.copy(screen = SubtitleScreen.ORGANIZE) }
    }
    fun query(value: String) { mutable.update { it.copy(query = value) } }
    fun language(value: String) { mutable.update { it.copy(language = value) }; search() }
    fun search() {
        val ui = mutable.value
        val context = ui.context ?: return
        if (ui.query.isBlank()) { mutable.update { it.copy(error = "请输入搜索关键词") }; return }
        searched = true
        mutable.update { it.copy(error = null) }
        // Keywords are sent as typed; each site applies its own supported context filters.
        assrt.search(context, ui.query, ui.language)
        openSubtitles.search(context, ui.query, ui.language)
    }
    fun searchSeason(id: String) {
        val season = mutable.value.seasons.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(context = it.context?.copy(seasonMedia = season)) }
        search()
    }
    fun findWorks(query: String, more: Boolean = false) {
        if (more && mutable.value.workLoading) return
        worksJob?.cancel()
        val start = if (more) mutable.value.workNext ?: return else 0
        mutable.update { it.copy(workQuery = query, workLoading = true, workError = null,
            works = if (more) it.works else emptyList()) }
        worksJob = scope.launch {
            try {
                val page = api.subtitleWorks(query, start)
                ensureActive()
                mutable.update { it.copy(workLoading = false, works = (if (more) it.works else emptyList()) + page.items,
                    workNext = (start + page.items.size).takeIf { offset -> page.items.isNotEmpty() && offset < page.total }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(workLoading = false, workError = e.message ?: "查找作品失败") } }
        }
    }
    fun back(): Boolean {
        if (mutable.value.screen == SubtitleScreen.MANAGEMENT) return false
        management()
        return true
    }
    private fun error(e: Exception) {
        if (e is ApiException && e.status == 401) expired()
        else mutable.update { it.copy(loading = false, error = e.message ?: "读取字幕管理失败") }
    }
    fun close() {
        metadataJob?.cancel(); localJob?.cancel(); worksJob?.cancel()
        assrt.clear(); openSubtitles.clear(); download.cancel(); organizer.close()
        // Explicit uploads continue and persist their results while navigating away.
    }
}
