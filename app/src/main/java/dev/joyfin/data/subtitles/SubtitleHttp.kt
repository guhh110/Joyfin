package dev.joyfin.data.subtitles

import dev.joyfin.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Only locally authored, non-sensitive messages may be shown verbatim. */
internal class SubtitleOperationException(message: String) : IOException(message)

internal fun subtitleHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
        .header("User-Agent", "Joyfin/${BuildConfig.VERSION_NAME}").build()) }.build()

internal fun subtitleRetrofit(base: String, client: OkHttpClient) = Retrofit.Builder()
    .baseUrl(base).client(client)
    .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
    .build()

internal fun <T> Response<T>.subtitleBody(): T {
    if (!isSuccessful) {
        errorBody()?.close()
        throw SubtitleOperationException(when (code()) {
            401 -> "认证失效，请检查来源配置"
            403, 406 -> "请求被拒绝，请检查密钥或下载额度"
            429 -> "请求过于频繁，请稍后重试"
            else -> "字幕站请求失败（HTTP ${code()}）"
        })
    }
    return body() ?: throw SubtitleOperationException("字幕站返回空响应")
}

/** Never show response bodies, signed URLs or credentials in UI errors. */
fun subtitleFailure(error: Throwable): String = when (error) {
    is SubtitleOperationException -> error.message ?: "字幕操作失败"
    is SerializationException -> "字幕站响应不符合官方 API 结构"
    is java.nio.charset.CharacterCodingException -> "字幕编码不符，请更换编码后重新保存"
    is java.net.SocketTimeoutException -> "请求超时，请重试"
    is java.net.UnknownHostException -> "无法解析下载服务器地址，请检查网络"
    is javax.net.ssl.SSLException -> "无法建立安全连接，请检查网络与系统时间"
    is java.util.zip.ZipException -> "压缩包读取失败，文件可能损坏或不支持该压缩方式"
    is IOException -> "请求或文件处理失败，请检查网络、来源配置与下载额度后重试"
    is IllegalArgumentException, is IllegalStateException -> "操作失败，请检查输入、文件格式及来源配置"
    else -> "字幕操作失败，请重试"
}
