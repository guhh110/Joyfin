package dev.joyfin.data.subtitles

import android.content.Context
import dev.joyfin.data.AppJson
import java.io.File
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
    val packages: Map<String, ManagedSubtitle> = emptyMap(),
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
        check(temp.renameTo(indexFile)) { "无法保存字幕索引" }
    }
    private fun valid(file: LocalSubtitleFile): Boolean =
        Regex("[a-f0-9]{64}\\.(srt|vtt|ass|ssa)").matches(file.path) && File(directory, file.path).isFile

    @Synchronized fun files(key: String): List<LocalSubtitleFile> {
        val index = read()
        return index.downloads[key].orEmpty().mapNotNull { index.files[it]?.takeIf(::valid) }
    }
    @Synchronized fun packages(server: String, user: String): List<ManagedSubtitle> {
        val index = read()
        val owner = owner(server, user)
        return index.packages.values.filter { it.owner == owner }.map { pack ->
            val active = pack.localSaved && pack.boundFiles.isNotEmpty() && pack.boundFiles.all { row ->
                row.mapping?.let { mapping ->
                    row.fileId in index.downloads[key(server, user, mapping.itemId, mapping.versionId)].orEmpty()
                } == true
            }
            pack.copy(localSaved = active)
        }.sortedByDescending { it.created }
    }

    @Synchronized fun putPackage(value: ManagedSubtitle) {
        val index = read()
        require(value.files.all { index.files[it.fileId]?.let(::valid) == true }) { "本地字幕已失效" }
        write(index.copy(packages = index.packages + (value.id to value)))
    }

    @Synchronized fun forgetReceipt(server: String, user: String, item: String, path: String) {
        val index = read()
        val owner = owner(server, user)
        write(index.copy(packages = index.packages.mapValues { (_, pack) ->
            if (pack.owner != owner) pack else {
                val removed = pack.receipts.filter { it.itemId == item && it.path == path }
                pack.copy(receipts = pack.receipts - removed.toSet(), files = pack.files.map { row ->
                    if (removed.any { it.fileId == row.fileId && it.mapping == row.mapping }) row.copy(upload = "", uploadError = null, uploadBefore = null) else row
                })
            }
        }))
    }

    @Synchronized fun content(id: String): ByteArray {
        val file = read().files[id]?.takeIf(::valid) ?: error("本地字幕已失效，请重新下载")
        return File(directory, file.path).readBytes()
    }

    /** Files, choices and workflow state share one atomic index commit. */
    @Synchronized fun bindPackage(value: ManagedSubtitle, server: String, user: String): ManagedSubtitle {
        val index = read()
        val old = index.packages[value.id] ?: error("字幕下载记录不存在")
        require(old.owner == value.owner && value.owner == owner(server, user))
        val assigned = value.files.filter { it.mapping != null }
        val keys = assigned.map { row -> row.mapping!!.let { key(server, user, it.itemId, it.versionId) } }
        val choices = index.choices.toMutableMap()
        val downloads = index.downloads.toMutableMap()
        val oldGroups = old.boundFiles.filter { it.mapping != null }.groupBy { row -> row.mapping!!.let { key(server, user, it.itemId, it.versionId) } }
        oldGroups.forEach { (binding, rows) ->
            val retainedByOtherPacks = index.packages.values.filter { it.id != value.id && it.owner == value.owner }
                .flatMap { it.boundFiles }.filter { row -> row.mapping?.let { key(server, user, it.itemId, it.versionId) == binding } == true }.map { it.fileId }.toSet()
            downloads[binding] = downloads[binding].orEmpty().filter { id -> id !in rows.map { it.fileId } || id in retainedByOtherPacks }
        }
        assigned.forEachIndexed { i, row ->
            require(index.files[row.fileId]?.let(::valid) == true) { "本地字幕已失效" }
            downloads[keys[i]] = (downloads[keys[i]].orEmpty() + row.fileId).distinct()
        }
        (oldGroups.keys + keys).forEach { binding ->
            val available = downloads[binding].orEmpty()
            val preferred = assigned.firstOrNull { row -> row.mapping?.let { key(server, user, it.itemId, it.versionId) == binding && value.defaults[it.itemId + ":" + it.versionId] == row.fileId } == true }?.fileId
            val current = choices[binding]
            val selected = preferred ?: current?.fileId?.takeIf { it in available } ?: available.firstOrNull()
            if (selected == null) choices.remove(binding)
            else choices[binding] = current?.takeIf { it.fileId == selected } ?: LocalSubtitleChoice(selected)
        }
        val saved = value.copy(localSaved = true, boundFiles = assigned)
        write(index.copy(choices = choices, downloads = downloads, packages = index.packages + (value.id to saved)))
        return saved
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
        require(extension in setOf("srt", "vtt", "ass", "ssa") && text.isNotBlank())
        require(!text.trimStart().startsWith("<html", true) && !text.trimStart().startsWith("<!doctype", true))
        val id = digest("$extension\u0000$text")
        check(directory.isDirectory || directory.mkdirs())
        val file = LocalSubtitleFile(id, safeName, origin, "$id.$extension", language)
        val destination = File(directory, file.path)
        val temp = File(directory, "$id.tmp")
        temp.writeText(text)
        check(temp.renameTo(destination))
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
        @Volatile private var instance: LocalSubtitleStore? = null
        fun get(context: Context): LocalSubtitleStore = instance ?: synchronized(this) {
            instance ?: LocalSubtitleStore(context.applicationContext).also { instance = it }
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
