package dev.joyfin.subtitles

import dev.joyfin.data.subtitles.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocalDownloadState(
    val busy: Boolean = false,
    val fileName: String = "",
    val encoding: String = "自动",
    val needsEncoding: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** Resolves files to a persistent pending package; downloading never selects playback subtitles. */
class LocalSubtitleDownload(
    private val scope: CoroutineScope,
    private val store: LocalSubtitleStore,
    private val saved: () -> Unit,
) {
    private val mutable = MutableStateFlow(LocalDownloadState())
    val state = mutable.asStateFlow()
    private val files = SubtitleFiles()
    private var task: Job? = null
    private var entries: List<SubtitleFiles.Entry> = emptyList()
    private var context: SubtitleSearchContext? = null
    private var origin = ""
    private var language: String? = null

    fun open(value: SubtitleSearchContext, name: String, url: String, source: String, lang: String?, onRequest: (String) -> Unit) =
        openFiles(value, listOf(name to url), source, lang, onRequest)

    fun openFiles(value: SubtitleSearchContext, references: List<Pair<String, String>>, source: String, lang: String?, onRequest: (String) -> Unit) {
        if (mutable.value.busy || mutable.value.needsEncoding) return
        context = value
        origin = source
        language = lang
        entries = emptyList()
        mutable.value = LocalDownloadState(busy = true, fileName = if (references.size == 1) references.first().first else "整包文件")
        task = scope.launch {
            try {
                require(references.isNotEmpty() && references.size <= 200)
                val downloaded = mutableListOf<SubtitleFiles.Entry>()
                for ((name, url) in references) {
                    ensureActive()
                    downloaded += files.download(name, url, onRequest)
                    require(downloaded.size <= 200 && downloaded.sumOf { it.bytes.size.toLong() } <= 20L * 1024 * 1024)
                }
                entries = downloaded
                persist()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    fun encoding(value: String) { mutable.update { it.copy(encoding = value) } }
    fun retryEncoding() {
        if (mutable.value.busy || entries.isEmpty()) return
        mutable.update { it.copy(busy = true, error = null) }
        task = scope.launch {
            try { persist() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    private suspend fun persist() {
        val value = requireNotNull(context)
        val decoded = withContext(Dispatchers.IO) { entries.map { it to files.decode(it, mutable.value.encoding) } }
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            val records = decoded.map { (entry, text) ->
                ensureActive()
                val file = store.save(value.owner, entry.name, origin, language, text)
                ManagedSubtitleFile(file.id, entry.name.replace('\\', '/'))
            }
            ensureActive()
            store.putPackage(ManagedSubtitle(id, value.owner, mutable.value.fileName, origin, System.currentTimeMillis(), records,
                workId = value.work?.id, workName = value.work?.name.orEmpty(),
                seasonId = value.seasonMedia?.id, seasonName = value.seasonMedia?.name.orEmpty(),
                language = when (language) { "en" -> "eng"; "zh-tw" -> "cht"; "zh", "zh-cn" -> "chi"; else -> language ?: "chi" }))
        }
        entries = emptyList()
        mutable.update { it.copy(busy = false, needsEncoding = false, error = null, notice = "下载完成，已加入待整理") }
        saved()
    }
    private fun failure(e: Exception) {
        mutable.update { it.copy(busy = false, needsEncoding = entries.isNotEmpty(),
            error = if (e is java.nio.charset.CharacterCodingException) "编码不符，请选择编码后重新保存" else subtitleFailure(e)) }
    }
    fun cancel() { task?.cancel(); entries = emptyList(); mutable.value = LocalDownloadState() }
}
