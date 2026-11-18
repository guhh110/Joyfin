package dev.joyfin.data.subtitles

import android.content.Context
import dev.joyfin.data.AppJson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class LocalSubtitleFile(
    val id: String,
    val name: String,
    val origin: String,
    val path: String,
    val language: String? = null,
)
@Serializable data class LocalSubtitleChoice(val fileId: String, val offsetMs: Long = 0)
@Serializable
private data class LocalSubtitleIndex(
    val files: Map<String, LocalSubtitleFile> = emptyMap(),
    val downloads: Map<String, List<String>> = emptyMap(),
    val choices: Map<String, LocalSubtitleChoice> = emptyMap(),
)

/** Local file storage knows nothing about subtitle-site DTOs or accounts. */
class LocalSubtitleStore private constructor(context: Context) {
    private val directory = File(context.noBackupFilesDir, "local-subtitles")
    private val indexFile = File(directory, "index.json")
    private fun read(): LocalSubtitleIndex = if (indexFile.exists())
        AppJson.decodeFromString(indexFile.readText()) else LocalSubtitleIndex()
    private fun write(index: LocalSubtitleIndex) {
        check(directory.isDirectory || directory.mkdirs())
        val temp = File(directory, "index.tmp")
        temp.writeText(AppJson.encodeToString(index))
        Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    private fun valid(file: LocalSubtitleFile): Boolean =
        Regex("[a-f0-9]{64}\\.(srt|vtt|ass|ssa)").matches(file.path) && File(directory, file.path).isFile

    @Synchronized fun files(key: String): List<LocalSubtitleFile> {
        val index = read()
        return index.downloads[key].orEmpty().mapNotNull { index.files[it]?.takeIf(::valid) }
    }

    /** Read and validate the index once for a library audit; callers compare opaque binding keys. */
    @Synchronized fun availableBindingSnapshot(): Set<String> {
        val index = read()
        val validFiles = index.files.filterValues(::valid).keys
        return index.downloads.filterValues { files -> files.any { it in validFiles } }.keys.toSet()
    }
    /** Bind and select the downloaded file for the actual playing media version in one commit. */
    @Synchronized fun use(key: String, fileId: String) {
        val index = read()
        require(index.files[fileId]?.let(::valid) == true) { "本地字幕已失效，请重新下载" }
        val choice = index.choices[key]?.takeIf { it.fileId == fileId } ?: LocalSubtitleChoice(fileId)
        write(index.copy(
            downloads = index.downloads + (key to (index.downloads[key].orEmpty() + fileId).distinct()),
            choices = index.choices + (key to choice),
        ))
    }
    @Synchronized fun choice(key: String): Pair<LocalSubtitleFile, Long>? {
        val index = read()
        val choice = index.choices[key] ?: return null
        return index.files[choice.fileId]?.takeIf(::valid)?.let { it to choice.offsetMs }
    }
    @Synchronized fun select(key: String, id: String?) {
        val index = read()
        require(id == null || files(key).any { it.id == id })
        write(index.copy(choices = if (id == null) index.choices - key else index.choices + (key to (index.choices[key]?.takeIf { it.fileId == id } ?: LocalSubtitleChoice(id)))))
    }
    @Synchronized fun offset(key: String, offsetMs: Long) {
        require(offsetMs in -60_000..60_000)
        val index = read()
        val selected = index.choices[key] ?: return
        write(index.copy(choices = index.choices + (key to selected.copy(offsetMs = offsetMs))))
    }
    @Synchronized fun save(key: String, name: String, origin: String, language: String?, text: String): LocalSubtitleFile {
        val safeName = name.replace('\\', '/').substringAfterLast('/')
        val extension = safeName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("srt", "vtt", "ass", "ssa")) throw SubtitleOperationException("无法保存：文件不是支持的文本字幕格式")
        if (text.isBlank()) throw SubtitleOperationException("下载的字幕内容为空")
        if (text.trimStart().startsWith("<html", true) || text.trimStart().startsWith("<!doctype", true))
            throw SubtitleOperationException("下载返回了网页内容，未取得字幕文件")
        val id = digest("$extension\u0000$text")
        check(directory.isDirectory || directory.mkdirs())
        val file = LocalSubtitleFile(id, safeName, origin, "$id.$extension", language)
        val destination = File(directory, file.path)
        val temp = File(directory, "$id.tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val index = read()
        write(index.copy(files = index.files + (id to file),
            downloads = index.downloads + (key to (index.downloads[key].orEmpty() + id).distinct())))
        return file
    }
    @Synchronized fun playbackFile(file: LocalSubtitleFile, offsetMs: Long): File {
        require(valid(file)) { "本地字幕已失效" }
        val original = File(directory, file.path)
        if (offsetMs == 0L) return original
        val shifted = File(directory, "playback-${file.id}.${original.extension}")
        shifted.writeText(shiftSubtitle(original.readText(), original.extension, offsetMs))
        return shifted
    }
    companion object {
        fun owner(server: String, user: String): String = digest("$server\u0000$user")
        fun key(server: String, user: String, item: String, version: String): String =
            digest(listOf(server, user, item, version).joinToString("\u0000"))
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private val instances = mutableMapOf<String, LocalSubtitleStore>()
        fun get(context: Context): LocalSubtitleStore = synchronized(instances) {
            val application = context.applicationContext
            instances.getOrPut(application.noBackupFilesDir.canonicalPath) { LocalSubtitleStore(application) }
        }
    }
}

private fun shiftSubtitle(text: String, extension: String, offsetMs: Long): String {
    fun shift(value: String, ass: Boolean): String {
        val parts = value.replace(',', '.').split(':', '.')
        val hours = if (parts.size == 4) parts[0].toLong() else 0L
        val minutes = parts[parts.size - 3].toLong()
        val seconds = parts[parts.size - 2].toLong()
        val fraction = parts.last().padEnd(3, '0').take(3).toLong()
        val time = (hours * 3_600_000 + minutes * 60_000 + seconds * 1000 + fraction + offsetMs).coerceAtLeast(0)
        return if (ass) "%d:%02d:%02d.%02d".format(java.util.Locale.ROOT, time / 3_600_000, time / 60_000 % 60, time / 1000 % 60, time % 1000 / 10)
        else {
            val separator = if (extension == "srt") ',' else '.'
            "%02d:%02d:%02d%c%03d".format(java.util.Locale.ROOT, time / 3_600_000, time / 60_000 % 60, time / 1000 % 60, separator, time % 1000)
        }
    }
    if (extension in setOf("ass", "ssa")) {
        var events = false
        var start = 1
        var end = 2
        return text.lineSequence().joinToString("\n") { line ->
            when {
                line.trim().startsWith("[") -> { events = line.trim().equals("[Events]", true); line }
                events && line.startsWith("Format:", true) -> {
                    val columns = line.substringAfter(':').split(',').map { it.trim() }
                    start = columns.indexOfFirst { it.equals("Start", true) }
                    end = columns.indexOfFirst { it.equals("End", true) }
                    line
                }
                events && line.startsWith("Dialogue:", true) -> {
                    require(start >= 0 && end >= 0)
                    // Keep all commas in dialogue text intact.
                    val columns = line.substringAfter(':').split(',').toMutableList()
                    columns[start] = shift(columns[start].trim(), true)
                    columns[end] = shift(columns[end].trim(), true)
                    line.substringBefore(':') + ":" + columns.joinToString(",")
                }
                else -> line
            }
        }
    }
    val timing = Regex("(?m)^((?:\\d{2,}:)?\\d{2}:\\d{2}[.,]\\d{3})[ \\t]+-->[ \\t]+((?:\\d{2,}:)?\\d{2}:\\d{2}[.,]\\d{3})")
    return timing.replace(text) { "${shift(it.groupValues[1], false)} --> ${shift(it.groupValues[2], false)}" }
}
