package dev.joyfin.subtitles

import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SubtitleOrganizerState(
    val pack: ManagedSubtitle? = null,
    val work: Media? = null,
    val seasons: List<Media> = emptyList(),
    val media: List<Media> = emptyList(),
    val available: List<ManagedSubtitleFile> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** Episode-first editor. Each episode/version can hold several local subtitle files. */
class SubtitleOrganizer(
    private val scope: CoroutineScope,
    private val api: JellyfinApi,
    private val store: LocalSubtitleStore,
    private val changed: () -> Unit,
) {
    private val mutable = MutableStateFlow(SubtitleOrganizerState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var owner: Session? = null

    fun open(pack: ManagedSubtitle) {
        job?.cancel()
        owner = api.session
        mutable.value = SubtitleOrganizerState(pack = pack)
        load {
            val candidates = withContext(Dispatchers.IO) { store.files(pack.owner) }
                .map { ManagedSubtitleFile(it.id, it.name) }
            mutable.update { it.copy(available = candidates) }
            pack.workId?.let { loadWork(api.item(it), pack.seasonId) }
        }
    }
    fun work(media: Media) = load { loadWork(media, null) }
    fun season(id: String) = load {
        val work = requireNotNull(mutable.value.work)
        loadMedia(work, id, reset = true)
    }
    private fun load(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                check(api.session == owner)
                block()
                ensureActive()
                mutable.update { it.copy(loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "读取媒体失败，请重试") } }
        }
    }
    private suspend fun loadWork(work: Media, seasonId: String?) {
        require(work.type in setOf("Movie", "Series"))
        val seasons = if (work.type == "Series") api.seasons(work.id) else emptyList()
        currentCoroutineContext().ensureActive()
        val old = mutable.value.pack
        val id = seasons.firstOrNull { it.id == seasonId }?.id ?: seasons.firstOrNull()?.id
        require(work.type != "Series" || id != null) { "当前剧没有可选季" }
        mutable.update { it.copy(work = work, seasons = seasons) }
        loadMedia(work, id, reset = old?.workId != work.id || old?.seasonId != id)
    }
    private suspend fun loadMedia(work: Media, seasonId: String?, reset: Boolean) {
        val seeds = if (work.type == "Series") api.episodes(work.id, seasonId) else listOf(work)
        // Load concrete media versions before matching; never reuse another episode's source ID.
        val media = seeds.map { seed -> currentCoroutineContext().ensureActive(); if (seed.sources.isEmpty()) api.item(seed.id) else seed }
            .sortedBy { it.episode }
        currentCoroutineContext().ensureActive()
        val pack = requireNotNull(mutable.value.pack)
        val updated = pack.copy(workId = work.id, workName = work.name, seasonId = seasonId,
            seasonName = mutable.value.seasons.firstOrNull { it.id == seasonId }?.name.orEmpty())
        val automatic = match(updated.files, media, work.type == "Movie")
        val rows = if (reset) automatic
            else updated.files.map { file ->
                val m = file.mapping
                if (m == null && !file.manualMapping) automatic.first { it.fileId == file.fileId && it.name == file.name }
                else if (m == null || media.any { it.id == m.itemId && it.sources.any { source -> source.id == m.versionId } }) file
                else file.copy(mapping = null, upload = "", uploadError = null)
            }
        val defaults = pack.defaults.toMutableMap()
        val session = requireNotNull(owner)
        rows.filter { it.mapping != null }.groupBy { row -> requireNotNull(row.mapping).let { it.itemId + ":" + it.versionId } }.forEach { (key, files) ->
            if (files.none { it.fileId == defaults[key] }) {
                val target = requireNotNull(files.first().mapping)
                val selected = withContext(Dispatchers.IO) { store.choice(LocalSubtitleStore.key(session.server, session.userId, target.itemId, target.versionId))?.first?.id }
                defaults[key] = selected?.takeIf { id -> files.any { it.fileId == id } } ?: files.first().fileId
            }
        }
        mutable.update { it.copy(media = media, pack = updated.copy(files = rows, defaults = defaults,
            localSaved = pack.localSaved && !reset && rows == pack.files), dirty = it.dirty || reset || rows != pack.files) }
    }
    private fun match(rows: List<ManagedSubtitleFile>, media: List<Media>, movie: Boolean): List<ManagedSubtitleFile> {
        val episodes = SubtitleEpisodeMatcher.episodes(rows.map { it.name }, media.map { it.episode }.toSet())
        val candidates = rows.indices.map { index ->
            if (movie) media.singleOrNull()
            else episodes[index]?.let { episode -> media.singleOrNull { it.episode == episode } }
        }
        return rows.mapIndexed { index, row ->
            val item = candidates[index]
            row.copy(mapping = item?.sources?.singleOrNull()?.let { mapping(item, it) }, upload = "", uploadError = null, manualMapping = false)
        }
    }
    fun toggleAvailable(fileId: String, mediaId: String, versionId: String) {
        edit { pack ->
            val media = mutable.value.media.firstOrNull { it.id == mediaId } ?: return@edit pack
            val version = media.sources.firstOrNull { it.id == versionId } ?: return@edit pack
            val row = pack.files.firstOrNull { it.fileId == fileId }
                ?: mutable.value.available.firstOrNull { it.fileId == fileId } ?: return@edit pack
            val target = mapping(media, version)
            val changed = row.copy(mapping = if (row.mapping == target) null else target, upload = "", uploadError = null, manualMapping = true)
            val rows = if (pack.files.any { it.fileId == fileId }) pack.files.map { if (it.fileId == fileId) changed else it }
                else pack.files + changed
            pack.copy(files = rows)
        }
    }
    fun defaultFile(mediaId: String, versionId: String, fileId: String) {
        edit { pack ->
            if (pack.files.none { it.fileId == fileId && it.mapping?.let { m -> m.itemId == mediaId && m.versionId == versionId } == true }) pack
            else pack.copy(defaults = pack.defaults + ("$mediaId:$versionId" to fileId))
        }
    }
    fun versionFamily(name: String) {
        edit { pack ->
            val episodes = SubtitleEpisodeMatcher.episodes(pack.files.map { it.name }, mutable.value.media.map { it.episode }.toSet())
            pack.copy(files = pack.files.mapIndexed { index, row ->
                val item = row.mapping?.let { m -> mutable.value.media.firstOrNull { it.id == m.itemId } }
                    ?: episodes[index]?.let { episode -> mutable.value.media.singleOrNull { it.episode == episode } }
                    ?: mutable.value.media.singleOrNull()
                val source = item?.sources?.singleOrNull { it.name == name }
                val target = if (item != null && source != null) mapping(item, source) else null
                if (target == row.mapping) row else row.copy(mapping = target, upload = "", uploadError = null, manualMapping = true)
            })
        }
    }
    fun options(language: String, forced: Boolean, hearingImpaired: Boolean) {
        val current = mutable.value.pack ?: return
        if (current.language == language && current.forced == forced && current.hearingImpaired == hearingImpaired) return
        edit { it.copy(language = language, forced = forced, hearingImpaired = hearingImpaired,
            files = it.files.map { row -> row.copy(upload = "", uploadError = null) }) }
    }
    private fun edit(change: (ManagedSubtitle) -> ManagedSubtitle) {
        val ui = mutable.value
        if (ui.loading || ui.saving) return
        val pack = ui.pack ?: return
        val result = change(pack)
        if (result == pack) return
        mutable.update { it.copy(pack = result.copy(localSaved = false), dirty = true, error = null, notice = null) }
    }
    suspend fun saveForUpload(): ManagedSubtitle {
        val session = requireNotNull(owner)
        check(api.session == session) { "账号已切换" }
        val pack = requireNotNull(mutable.value.pack)
        val saved = withContext(Dispatchers.IO) { store.bindPackage(pack, session.server, session.userId) }
        mutable.update { it.copy(pack = saved, dirty = false) }
        changed()
        return saved
    }
    fun save() {
        if (mutable.value.loading || mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        job = scope.launch {
            try { saveForUpload(); mutable.update { it.copy(saving = false, notice = "本地关联已保存") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(saving = false, error = e.message ?: "保存失败") } }
        }
    }
    fun uploadBusy(value: Boolean) { mutable.update { it.copy(saving = value) } }
    fun uploaded(pack: ManagedSubtitle) {
        if (mutable.value.pack?.id == pack.id) mutable.update { it.copy(pack = pack, saving = false, dirty = false) }
    }
    fun close() { if (!mutable.value.saving) job?.cancel() }
    companion object {
        private fun mapping(item: Media, source: MediaSource) = SubtitleMapping(item.id, item.label,
            source.id, source.name.ifBlank { source.container.uppercase() }, source.path, item.episode)
    }
}
