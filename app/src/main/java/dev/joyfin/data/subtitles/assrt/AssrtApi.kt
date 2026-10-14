package dev.joyfin.data.subtitles.assrt

import dev.joyfin.data.subtitles.SubtitleRequestHistory
import dev.joyfin.data.subtitles.SubtitleOperationException
import dev.joyfin.data.subtitles.subtitleHttp
import dev.joyfin.data.subtitles.subtitleRetrofit
import dev.joyfin.data.subtitles.subtitleFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.MissingFieldException
import retrofit2.Response
import retrofit2.http.*

// https://assrt.net/api/doc — empty list variants are normalized only at the API boundary.
@Serializable data class AssrtLanguage(val desc: String = "")
@Serializable data class AssrtFile(val f: String, val url: String)
@Serializable data class AssrtSubtitle(
    val id: Long,
    @SerialName("native_name") val nativeName: String = "",
    val videoname: String = "",
    val subtype: String = "",
    val lang: AssrtLanguage = AssrtLanguage(),
    val filename: String = "",
    val url: String = "",
    @Serializable(with = AssrtFilesSerializer::class)
    val filelist: List<AssrtFile> = emptyList(),
)
@Serializable internal data class AssrtSubs(
    @Serializable(with = AssrtResultsSerializer::class)
    val subs: List<AssrtSubtitle> = emptyList(),
)
@Serializable internal data class AssrtResponse(val status: Int, val sub: AssrtSubs? = null)
internal interface AssrtService {
    @GET("sub/search") suspend fun search(@Header("Authorization") auth: String,
        @Query("q") query: String, @Query("pos") position: Int, @Query("cnt") count: Int = 15,
        @Query("is_file") isFile: Int = 0): Response<AssrtResponse>
    @GET("sub/detail") suspend fun detail(@Header("Authorization") auth: String, @Query("id") id: Long): Response<AssrtResponse>
}
class AssrtApi {
    private val history = SubtitleRequestHistory()
    val requestHistory = history.records
    private val client = subtitleHttp().newBuilder().addInterceptor { chain ->
        val request = chain.request()
        history.proceed(chain, request)
    }.build()
    private val service = subtitleRetrofit("https://api.assrt.net/v1/", client).create(AssrtService::class.java)
    internal fun recordRequest(url: String) { history.recordRequest(url) }
    fun clearRequestHistory() { history.clear() }
    private val mutex = Mutex()
    private var nextRequest = 0L
    private suspend fun request(call: suspend () -> Response<AssrtResponse>): AssrtResponse = mutex.withLock {
        delay((nextRequest - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
        nextRequest = android.os.SystemClock.elapsedRealtime() + 3100
        call().assrtBody()
    }
    suspend fun search(account: AssrtAccount, query: String, position: Int, fileName: Boolean): List<AssrtSubtitle> {
        if (query.length <= 3) throw SubtitleOperationException("ASSRT 搜索关键词长度必须大于 3 个字符")
        val sub = request {
            service.search("Bearer ${account.token}", query, position, isFile = if (fileName) 1 else 0)
        }.sub ?: error("ASSRT 缺少搜索结果")
        return sub.subs
    }
    suspend fun detail(account: AssrtAccount, id: Long): AssrtSubtitle =
        request { service.detail("Bearer ${account.token}", id) }.sub?.subs?.firstOrNull() ?: error("ASSRT 没有返回文件")
}

/** Report only schema field names and indexes, never response values or signed URLs. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal fun assrtFailure(endpoint: String, error: Throwable): String {
    if (error !is SerializationException) return subtitleFailure(error)
    val fields = setOf("status", "sub", "subs", "id", "native_name", "videoname", "subtype",
        "lang", "desc", "filename", "url", "filelist", "f")
    val path = Regex("(?:at path:|at element:)\\s*(\\$[.A-Za-z0-9_\\[\\]]*)")
        .find(error.message.orEmpty())?.groupValues?.get(1)?.takeIf { candidate ->
            candidate.length <= 120 && Regex("[A-Za-z_][A-Za-z_0-9]*").findAll(candidate)
                .all { it.value in fields }
        }
    val missing = (error as? MissingFieldException)?.missingFields.orEmpty().filter { it in fields }
    return "ASSRT $endpoint 响应解析失败" +
        (path?.let { "（字段 $it）" } ?: "（无法定位字段）") +
        (if (missing.isEmpty()) "" else "，缺少：${missing.joinToString()}")
}
