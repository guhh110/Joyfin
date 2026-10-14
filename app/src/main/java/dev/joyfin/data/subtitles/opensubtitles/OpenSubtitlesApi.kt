package dev.joyfin.data.subtitles.opensubtitles

import dev.joyfin.data.subtitles.subtitleBody
import dev.joyfin.data.subtitles.subtitleHttp
import dev.joyfin.data.subtitles.subtitleRetrofit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable data class OpenSubtitlesFile(@SerialName("file_id") val id: Int, @SerialName("file_name") val name: String)
@Serializable data class OpenSubtitlesFeature(val title: String = "")
@Serializable data class OpenSubtitlesAttributes(
    val language: String,
    val release: String = "",
    val files: List<OpenSubtitlesFile>,
    @SerialName("feature_details") val feature: OpenSubtitlesFeature = OpenSubtitlesFeature(),
)
@Serializable data class OpenSubtitlesEntry(val id: String, val attributes: OpenSubtitlesAttributes)
@Serializable data class OpenSubtitlesPage(val data: List<OpenSubtitlesEntry>, @SerialName("total_pages") val totalPages: Int)
@Serializable internal data class LoginBody(val username: String, val password: String)
@Serializable internal data class LoginResponse(val token: String, @SerialName("base_url") val baseUrl: String = "api.opensubtitles.com")
@Serializable internal data class DownloadBody(@SerialName("file_id") val fileId: Int, @SerialName("sub_format") val format: String)
@Serializable data class OpenSubtitlesDownload(val link: String, @SerialName("file_name") val name: String, val remaining: Int? = null)
internal interface OpenSubtitlesService {
    @GET("subtitles") suspend fun search(@Header("Api-Key") key: String, @Header("Authorization") auth: String?, @QueryMap params: Map<String, String>): Response<OpenSubtitlesPage>
    @POST("login") suspend fun login(@Header("Api-Key") key: String, @Body body: LoginBody): Response<LoginResponse>
    @POST("download") suspend fun download(@Header("Api-Key") key: String, @Header("Authorization") auth: String?, @Body body: DownloadBody): Response<OpenSubtitlesDownload>
}
class OpenSubtitlesApi {
    private val mutableRequestUrls = MutableStateFlow<List<String>>(emptyList())
    val requestUrls = mutableRequestUrls.asStateFlow()
    internal fun recordRequest(url: String) { mutableRequestUrls.update { it + url } }
    fun clearRequestUrls() { mutableRequestUrls.value = emptyList() }
    // Official API normalizes search query URLs; authentication stays on this origin and path.
    private val client = subtitleHttp().newBuilder().addInterceptor { chain ->
        var request = chain.request()
        recordRequest("${request.method} ${request.url}")
        var response = chain.proceed(request)
        if (request.method == "GET" && request.url.encodedPath == "/api/v1/subtitles") {
            var count = 0
            while (response.code in setOf(301, 302, 307, 308) && count++ < 3) {
                val target = response.header("Location")?.let(request.url::resolve) ?: break
                if (target.scheme != request.url.scheme || target.host != request.url.host ||
                    target.port != request.url.port || target.encodedPath != request.url.encodedPath ||
                    target.username.isNotEmpty() || target.password.isNotEmpty()) break
                response.close()
                request = request.newBuilder().url(target).build()
                recordRequest("${request.method} ${request.url}")
                response = chain.proceed(request)
            }
        }
        response
    }.build()
    private fun service(account: OpenSubtitlesAccount): OpenSubtitlesService {
        require(account.host in setOf("api.opensubtitles.com", "vip-api.opensubtitles.com"))
        return subtitleRetrofit("https://${account.host}/api/v1/", client).create(OpenSubtitlesService::class.java)
    }
    private fun auth(account: OpenSubtitlesAccount): String? = account.token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    suspend fun search(account: OpenSubtitlesAccount, params: Map<String, String>): OpenSubtitlesPage =
        service(account).search(account.apiKey, auth(account), params.toSortedMap()).subtitleBody()
    suspend fun download(account: OpenSubtitlesAccount, id: Int): OpenSubtitlesDownload =
        service(account).download(account.apiKey, auth(account), DownloadBody(id, "srt")).subtitleBody()
    suspend fun login(account: OpenSubtitlesAccount, password: String): OpenSubtitlesAccount {
        val result = service(account).login(account.apiKey, LoginBody(account.username, password)).subtitleBody()
        val host = result.baseUrl.removePrefix("https://").trimEnd('/')
        require(host in setOf("api.opensubtitles.com", "vip-api.opensubtitles.com") && result.token.isNotBlank())
        return account.copy(token = result.token, host = host)
    }
}
