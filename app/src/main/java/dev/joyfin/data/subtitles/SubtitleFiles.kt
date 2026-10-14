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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/** No API client or credentials are used for file transfers. */
class SubtitleFiles {
    data class Entry(val name: String, val bytes: ByteArray)
    private val client = subtitleHttp()

    suspend fun download(name: String, address: String, onRequest: (String) -> Unit): List<Entry> = withContext(Dispatchers.IO) {
        var url = address.toHttpUrlOrNull() ?: throw SubtitleOperationException("字幕站返回的下载链接无效")
        // Use the site's signed URL as returned, including its original HTTP/HTTPS scheme.
        repeat(5) {
            currentCoroutineContext().ensureActive()
            fileCheck(url.scheme in setOf("http", "https") && url.username.isEmpty() && url.password.isEmpty(), "下载链接不符合要求：需使用无账号信息的 HTTP 或 HTTPS 地址")
            onRequest("GET $url")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    url = url.resolve(response.header("Location") ?: throw SubtitleOperationException("下载重定向缺少地址")) ?: throw SubtitleOperationException("下载重定向地址无效")
                } else {
                    fileCheck(response.isSuccessful, "字幕文件下载失败（HTTP ${response.code}）")
                    val body = response.body ?: throw SubtitleOperationException("下载响应为空")
                    fileCheck(body.contentLength() <= MAX_BYTES, "字幕文件超过 20 MiB 限制")
                    val bytes = body.byteStream().use { bounded(it, MAX_BYTES) }
                    currentCoroutineContext().ensureActive()
                    return@withContext unpack(name, bytes)
                }
            }
        }
        throw SubtitleOperationException("下载重定向次数过多")
    }

    private suspend fun unpack(name: String, bytes: ByteArray): List<Entry> {
        val rarSignature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07)
        if (name.endsWith(".rar", ignoreCase = true) ||
            (bytes.size >= rarSignature.size && rarSignature.indices.all { bytes[it] == rarSignature[it] })) {
            return RarSubtitleArchive.unpack(bytes, MAX_BYTES, ::supported)
        }
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            val files = mutableListOf<Entry>()
            var total = 0
            var count = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    fileCheck(++count <= 200, "压缩包超过 200 个条目限制")
                    if (!entry.isDirectory) {
                        val content = bounded(zip, MAX_BYTES - total)
                        total += content.size
                        if (supported(entry.name)) files += Entry(entry.name, content)
                    }
                    zip.closeEntry()
                }
            }
            fileCheck(files.isNotEmpty(), "压缩包内没有 SRT、VTT、ASS 或 SSA 字幕")
            return files
        }
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            val decoded = GZIPInputStream(ByteArrayInputStream(bytes)).use { bounded(it, MAX_BYTES) }
            return unpack(name.removeSuffix(".gz"), decoded)
        }
        fileCheck(supported(name), when (name.substringAfterLast('.', "").lowercase()) {
            "7z" -> "暂不支持 7z 压缩包，请选择站点提供的单独字幕文件"
            else -> "下载文件格式不支持，仅支持 SRT、VTT、ASS、SSA 和 ZIP／GZIP／RAR"
        })
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
        private fun fileCheck(value: Boolean, message: String) { if (!value) throw SubtitleOperationException(message) }
        private const val MAX_BYTES = 20 * 1024 * 1024
        private fun supported(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("srt", "vtt", "ass", "ssa")
        private fun bounded(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                fileCheck(output.size() + n <= limit, "字幕下载或解压内容超过 20 MiB 限制")
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }
}
