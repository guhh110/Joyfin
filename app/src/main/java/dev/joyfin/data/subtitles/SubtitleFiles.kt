package dev.joyfin.data.subtitles

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** No API client or credentials are used for file transfers. */
class SubtitleFiles {
    data class Entry(val name: String, val bytes: ByteArray)
    private val client = subtitleHttp()

    suspend fun download(name: String, address: String, onRequest: (String) -> Unit): List<Entry> = withContext(Dispatchers.IO) {
        var url = address.toHttpUrl()
        // ASSRT's official detail example contains HTTP file links; always use same-host HTTPS.
        if (url.scheme == "http" && url.host.endsWith(".assrt.net")) url = url.newBuilder().scheme("https").build()
        repeat(5) {
            currentCoroutineContext().ensureActive()
            require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) { "下载链接必须使用 HTTPS" }
            onRequest("GET $url")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    url = url.resolve(response.header("Location") ?: error("重定向缺少地址")) ?: error("重定向地址无效")
                } else {
                    check(response.isSuccessful) { "下载失败" }
                    val body = response.body ?: error("下载文件为空")
                    require(body.contentLength() <= MAX_BYTES) { "字幕文件过大" }
                    val bytes = body.byteStream().use { bounded(it, MAX_BYTES) }
                    currentCoroutineContext().ensureActive()
                    return@withContext unpack(name, bytes)
                }
            }
        }
        error("下载重定向过多")
    }

    private fun unpack(name: String, bytes: ByteArray): List<Entry> {
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            val files = mutableListOf<Entry>()
            var total = 0
            var count = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 200) { "压缩包文件过多" }
                    if (!entry.isDirectory) {
                        val content = bounded(zip, MAX_BYTES - total)
                        total += content.size
                        if (supported(entry.name)) files += Entry(entry.name, content)
                    }
                    zip.closeEntry()
                }
            }
            require(files.isNotEmpty()) { "压缩包内没有支持的文本字幕" }
            return files
        }
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            val decoded = GZIPInputStream(ByteArrayInputStream(bytes)).use { bounded(it, MAX_BYTES) }
            return unpack(name.removeSuffix(".gz"), decoded)
        }
        require(supported(name)) { "只支持 SRT、VTT、ASS、SSA 和 ZIP／GZIP" }
        return listOf(Entry(name, bytes))
    }

    fun decode(entry: Entry, encoding: String): String {
        val bytes = entry.bytes
        val charset = if (encoding != "自动") Charset.forName(encoding) else when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024
        private fun supported(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("srt", "vtt", "ass", "ssa")
        private fun bounded(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size() + n <= limit) { "字幕文件过大" }
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }
}
