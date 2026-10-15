package dev.joyfin.subtitles

import android.content.Context
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import dev.joyfin.data.subtitles.assrt.*
import dev.joyfin.data.subtitles.opensubtitles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of the actual playing version, independent of the season being browsed in the UI. */
data class PlayerSubtitleTarget(
    val session: Session,
    val media: Media,
    val source: MediaSource,
    val generation: Long,
)

enum class PlayerSubtitleSource { ASSRT, OPEN_SUBTITLES }

data class PlayerSubtitleSearchState(
    val open: Boolean = false,
    val closing: Boolean = false,
    val applied: Boolean = false,
    val initializing: Boolean = false,
    val query: String = "",
    val contextLabel: String = "",
    val source: PlayerSubtitleSource = PlayerSubtitleSource.ASSRT,
    val pack: ManagedSubtitle? = null,
    val showAll: Boolean = false,
    val selectedFileId: String? = null,
    val applying: Boolean = false,
    val error: String? = null,
    val focusKey: String = "quick:query",
    val focusRevision: Int = 0,
    val episodeNumbers: Set<Int> = emptySet(),
    val downloadFocusKey: String = "",
    val selectingFile: Boolean = false,
)

/** Playback-local search workflow; site clients and their DTOs remain independent. */
class PlayerSubtitleSearchController(
    context: Context,
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val store: LocalSubtitleStore,
    private val apply: suspend (PlayerSubtitleTarget, ManagedSubtitle, String) -> Unit,
    private val closed: (applied: Boolean) -> Unit,
) {
    private val mutable = MutableStateFlow(PlayerSubtitleSearchState())
    val state = mutable.asStateFlow()
    private val assrtAccounts = AssrtAccountStore(context)
    private val openSubtitlesAccounts = OpenSubtitlesAccountStore(context)
    val download = LocalSubtitleDownload(scope, store, {}, ::downloaded)
    val assrt = AssrtSearchController(scope, assrtAccounts, AssrtApi(), download)
    val openSubtitles = OpenSubtitlesSearchController(scope, openSubtitlesAccounts, OpenSubtitlesApi(), download)
    private var target: PlayerSubtitleTarget? = null
    private var searchContext: SubtitleSearchContext? = null
    private var generation = 0
    private var setupJob: Job? = null
    private var episodesJob: Job? = null
    private var applyJob: Job? = null
    private var resultFocus = "quick:query"
    private var assrtQuery: String? = null
    private var openSubtitlesQuery: String? = null
    private val active get() = mutable.value.open && !mutable.value.closing
    val busy: Boolean get() = mutable.value.initializing || mutable.value.applying ||
        download.state.value.busy || download.state.value.needsEncoding ||
        assrt.state.value.busyId != null || openSubtitles.state.value.busyFile != null

    fun open(value: PlayerSubtitleTarget) {
        reset()
        target = value
        val media = value.media
        val episodic = media.type == "Episode"
        val work = if (episodic) media.seriesId.takeIf { it.isNotBlank() }?.let {
            Media(it, media.seriesName, "Series")
        } else media
        val season = media.seasonId.takeIf { episodic && it.isNotBlank() }?.let {
            Media(it, "第 ${media.season} 季", "Season", episode = media.season)
        }
        searchContext = SubtitleSearchContext(LocalSubtitleStore.owner(value.session.server, value.session.userId),
            work, season, media.episode.takeIf { episodic && it > 0 }, media.season.takeIf { episodic })
        mutable.value = PlayerSubtitleSearchState(open = true, initializing = true,
            episodeNumbers = setOf(media.episode),
            query = (if (episodic) media.seriesName else media.name).ifBlank { media.name },
            contextLabel = if (episodic) "${media.seriesName} · 第 ${media.season} 季 · 第 ${media.episode} 集" else media.name)
        val token = generation
        setupJob = scope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    val a = assrtAccounts.load()
                    val o = openSubtitlesAccounts.load()
                    if (a.enabled && a.token.isNotBlank() || !o.enabled || o.apiKey.isBlank())
                        PlayerSubtitleSource.ASSRT else PlayerSubtitleSource.OPEN_SUBTITLES
                }
                if (!active || token != generation) return@launch
                mutable.update { it.copy(initializing = false, source = source) }
                search()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (active && token == generation)
                    mutable.update { it.copy(initializing = false, error = "读取字幕来源配置失败，请关闭后重试") }
            }
        }
        if (episodic && media.seriesId.isNotBlank() && media.seasonId.isNotBlank()) {
            episodesJob = scope.launch {
                try {
                    val episodes = api.episodes(media.seriesId, media.seasonId)
                    if (active && token == generation)
                        mutable.update { it.copy(episodeNumbers = (episodes.map { it.episode } + media.episode).toSet()) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Explicit episode labels still work without season metadata. */ }
            }
        }
    }

    fun query(value: String) { if (active && !busy) mutable.update { it.copy(query = value) } }
    fun source(value: PlayerSubtitleSource) {
        if (!active || busy || mutable.value.pack != null) return
        dismissFiles()
        download.cancel()
        mutable.update { it.copy(source = value, error = null, showAll = false) }
        val submitted = if (value == PlayerSubtitleSource.ASSRT) assrtQuery else openSubtitlesQuery
        if (submitted != mutable.value.query.trim()) search()
    }
    fun search() {
        if (!active || busy || mutable.value.pack != null) return
        val context = searchContext ?: return
        val query = mutable.value.query.trim()
        if (query.isBlank()) { mutable.update { it.copy(error = "请输入搜索关键词") }; return }
        if (mutable.value.source == PlayerSubtitleSource.ASSRT && assrt.state.value.loading && assrtQuery == query ||
            mutable.value.source == PlayerSubtitleSource.OPEN_SUBTITLES && openSubtitles.state.value.loading && openSubtitlesQuery == query) return
        mutable.update { it.copy(error = null) }
        if (mutable.value.source == PlayerSubtitleSource.ASSRT) {
            assrtQuery = query
            assrt.search(context, query, "zh")
        } else {
            openSubtitlesQuery = query
            openSubtitles.search(context, query, "zh")
        }
    }
    fun choose(item: AssrtSubtitle) {
        if (!active || busy) return
        resultFocus = "quick:assrt:${item.id}"
        mutable.update { it.copy(showAll = false, error = null, downloadFocusKey = resultFocus) }
        assrt.choose(item)
    }
    fun choose(item: OpenSubtitlesEntry) {
        if (!active || busy) return
        resultFocus = "quick:os:${item.id}"
        mutable.update { it.copy(showAll = false, error = null, downloadFocusKey = resultFocus) }
        openSubtitles.choose(item)
    }
    fun download(file: AssrtFile) {
        if (!active || busy) return
        mutable.update { it.copy(downloadFocusKey = "quick:assrt-file:${assrt.state.value.files.indexOf(file)}") }
        assrt.download(file)
    }
    fun download(file: OpenSubtitlesFile) {
        if (!active || busy) return
        mutable.update { it.copy(downloadFocusKey = "quick:os-file:${openSubtitles.state.value.selected?.attributes?.files?.indexOf(file)}") }
        openSubtitles.download(file)
    }
    fun more() {
        if (!active || busy) return
        if (mutable.value.source == PlayerSubtitleSource.ASSRT) assrt.more() else openSubtitles.more()
    }
    fun retry() {
        if (!active || busy) return
        if (mutable.value.source == PlayerSubtitleSource.ASSRT) assrt.retry() else openSubtitles.retry()
    }
    fun showAll(value: Boolean) { if (active && !busy) mutable.update { it.copy(showAll = value) } }

    /** Match the complete filename set before narrowing to this episode. Unknowns stay visible. */
    fun related(names: List<String>): List<Int> {
        if (mutable.value.showAll || target?.media?.type != "Episode") return names.indices.toList()
        val episodes = SubtitleEpisodeMatcher.episodes(names, mutable.value.episodeNumbers)
        val current = target?.media?.episode
        return names.indices.filter { index ->
            val explicit = SubtitleEpisodeMatcher.explicitEpisode(names[index])
            val episode = explicit ?: episodes[index]
            episode == null || episode == current
        }
    }

    private fun downloaded(pack: ManagedSubtitle) {
        if (!active || pack.owner != searchContext?.owner) return
        if (pack.files.size != 1) dismissFiles()
        mutable.update { it.copy(pack = pack, showAll = false, error = null, selectingFile = pack.files.size > 1) }
        if (pack.files.size == 1) use(pack.files.single().fileId)
    }
    fun use(fileId: String) {
        val value = target ?: return
        val pack = mutable.value.pack ?: return
        if (!active || busy || pack.files.none { it.fileId == fileId }) return
        val token = generation
        mutable.update { it.copy(applying = true, selectedFileId = fileId, error = null) }
        applyJob = scope.launch {
            try {
                apply(value, pack, fileId)
                ensureActive()
                if (active && token == generation) close(applied = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (active && token == generation)
                    mutable.update { it.copy(applying = false, selectingFile = true,
                        error = "应用字幕失败，原字幕已保留。可重试，无需重新下载。") }
            }
        }
    }
    fun back() {
        if (!active) return
        if (mutable.value.pack != null || assrt.state.value.selected != null || openSubtitles.state.value.selected != null || download.state.value.needsEncoding) {
            applyJob?.cancel()
            dismissFiles()
            download.cancel()
            mutable.update { it.copy(pack = null, applying = false, selectedFileId = null,
                selectingFile = false, showAll = false, error = null, focusKey = resultFocus, focusRevision = it.focusRevision + 1) }
        } else close()
    }
    private fun dismissFiles() { assrt.dismissFiles(); openSubtitles.dismissFiles() }

    /** Cancellation happens at the start of exit; the dialog remains only for its exit animation. */
    fun close(applied: Boolean = false) {
        if (!active) return
        generation++
        setupJob?.cancel(); episodesJob?.cancel()
        if (!applied) applyJob?.cancel()
        assrt.cancel(); openSubtitles.cancel(); download.cancel()
        mutable.update { it.copy(closing = true, applying = false, applied = applied) }
    }
    fun finishClose() {
        if (!mutable.value.open || !mutable.value.closing) return
        val applied = mutable.value.applied
        reset()
        mutable.value = PlayerSubtitleSearchState()
        closed(applied)
    }
    /** For playback changes or activity stop: dispose synchronously without restoring old media. */
    fun discard() {
        reset()
        mutable.value = PlayerSubtitleSearchState()
    }
    private fun reset() {
        generation++
        setupJob?.cancel(); episodesJob?.cancel(); applyJob?.cancel()
        assrt.clear(); openSubtitles.clear(); download.cancel()
        target = null; searchContext = null
        assrtQuery = null; openSubtitlesQuery = null
        resultFocus = "quick:query"
    }
}
