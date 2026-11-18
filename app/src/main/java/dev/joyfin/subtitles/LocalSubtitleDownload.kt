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
)

/** Downloads and decodes files for the player; playback explicitly applies the chosen file. */
class LocalSubtitleDownload(
    private val scope: CoroutineScope,
    private val store: LocalSubtitleStore,
    private val completed: (DownloadedSubtitle) -> Unit,
    httpClient: okhttp3.OkHttpClient = subtitleHttp(),
) {
    private val mutable = MutableStateFlow(LocalDownloadState())
    val state = mutable.asStateFlow()
    private val files = SubtitleFiles(httpClient)
    private var task: Job? = null
    private var entries: List<SubtitleFiles.Entry> = emptyList()
    private var context: SubtitleSearchContext? = null
    private var origin = ""
    private var language: String? = null

    fun open(value: SubtitleSearchContext, name: String, url: String, source: String, lang: String?) {
        if (mutable.value.busy || mutable.value.needsEncoding) return
        context = value
        origin = source
        language = lang
        entries = emptyList()
        mutable.value = LocalDownloadState(busy = true, fileName = name)
        task = scope.launch {
            try {
                entries = files.download(name, url)
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
        val pack = withContext(Dispatchers.IO) {
            val records = decoded.map { (entry, text) ->
                ensureActive()
                val file = store.save(value.owner, entry.name, origin, language, text)
                DownloadedSubtitleFile(file.id, entry.name.replace('\\', '/'))
            }
            ensureActive()
            DownloadedSubtitle(id, value.owner, records)
        }
        entries = emptyList()
        mutable.update { it.copy(busy = false, needsEncoding = false, error = null) }
        completed(pack)
    }
    private fun failure(e: Exception) {
        mutable.update { it.copy(busy = false, needsEncoding = entries.isNotEmpty(),
            error = if (e is java.nio.charset.CharacterCodingException) "编码不符，请选择编码后重试" else subtitleFailure(e)) }
    }

    fun cancel() { task?.cancel(); entries = emptyList(); mutable.value = LocalDownloadState() }
}
