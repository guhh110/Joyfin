package dev.joyfin.data.subtitles

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/** In-memory diagnostics only; each subtitle site owns its own history instance. */
data class SubtitleRequestRecord(
    val id: Long,
    val request: String,
    val status: Int? = null,
    val responseJson: String? = null,
    val note: String? = null,
)

internal class SubtitleRequestHistory {
    private val sequence = AtomicLong()
    private val mutableRecords = MutableStateFlow<List<SubtitleRequestRecord>>(emptyList())
    val records = mutableRecords.asStateFlow()
    private val json = Json { prettyPrint = true }

    fun clear() { mutableRecords.value = emptyList() }
    fun recordRequest(request: String) { begin(request, "文件下载：仅记录 URL，不记录二进制内容") }
    private fun begin(request: String, note: String? = null): Long {
        val id = sequence.incrementAndGet()
        mutableRecords.update { (it + SubtitleRequestRecord(id, request, note = note)).takeLast(50) }
        return id
    }
    private fun finish(id: Long, status: Int? = null, body: String? = null, note: String? = null) {
        mutableRecords.update { rows -> rows.map { if (it.id == id) it.copy(status = status, responseJson = body, note = note) else it } }
    }

    /** Peek before Retrofit parses DTOs, so invalid API structures are still visible. */
    fun proceed(chain: Interceptor.Chain, request: okhttp3.Request): Response {
        val id = begin("${request.method} ${request.url}")
        val response = try { chain.proceed(request) } catch (error: IOException) {
            finish(id, note = "未收到响应：网络请求失败")
            throw error
        }
        try {
            val bytes = response.peekBody(MAX_BODY_BYTES + 1).bytes()
            when {
                bytes.size > MAX_BODY_BYTES -> finish(id, response.code, note = "响应超过 256 KiB，未记录正文")
                bytes.isEmpty() -> finish(id, response.code, note = "响应正文为空")
                else -> {
                    // Headers and request bodies never enter history; only use secrets to redact echoes.
                    val secrets = listOfNotNull(request.header("Authorization")?.removePrefix("Bearer "), request.header("Api-Key"))
                        .filter { it.isNotEmpty() }
                    val element = json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
                    finish(id, response.code, json.encodeToString(JsonElement.serializer(), redact(element, secrets)))
                }
            }
        } catch (_: kotlinx.serialization.SerializationException) {
            finish(id, response.code, note = "响应不是有效 JSON，未记录正文")
        } catch (_: IOException) {
            finish(id, response.code, note = "无法读取响应正文")
        }
        return response
    }

    private fun redact(value: JsonElement, secrets: List<String>): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            if (key.lowercase().replace("-", "").replace("_", "") in SECRET_KEYS) JsonPrimitive("[已隐藏]")
            else redact(child, secrets)
        })
        is JsonArray -> JsonArray(value.map { redact(it, secrets) })
        is JsonPrimitive -> if (value.isString) JsonPrimitive(secrets.fold(value.content) { text, secret -> text.replace(secret, "[已隐藏]") }) else value
    }

    private companion object {
        const val MAX_BODY_BYTES = 256L * 1024
        val SECRET_KEYS = setOf("token", "accesstoken", "refreshtoken", "apikey", "password", "authorization", "secret")
    }
}
