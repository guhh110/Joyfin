package dev.joyfin.data.subtitles.assrt

import dev.joyfin.data.subtitles.SubtitleOperationException
import dev.joyfin.data.subtitles.subtitleBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

@Serializable
private data class AssrtErrorResponse(val status: Int)

private val errorJson = Json { ignoreUnknownKeys = true }

/** ASSRT reports API errors in HTTP 200 and client/server failures in HTTP 4xx/5xx. */
internal fun Response<AssrtResponse>.assrtBody(): AssrtResponse {
    if (!isSuccessful) {
        val text = errorBody()?.use { it.string() }
        val error = text?.let {
            try { errorJson.decodeFromString<AssrtErrorResponse>(it) }
            catch (_: SerializationException) { null }
        }
        if (error != null && error.status != 0) throw assrtStatusError(error.status, code())
    }
    return subtitleBody().also {
        if (it.status != 0) throw assrtStatusError(it.status)
    }
}

private fun assrtStatusError(status: Int, http: Int? = null): SubtitleOperationException {
    val reason = when (status) {
        1 -> "用户不存在"
        101 -> "搜索关键词长度必须大于 3 个字符"
        20000 -> "请求缺少必要参数"
        20001 -> "Token 无效，请检查 ASSRT 配置"
        20400 -> "API 接口不存在"
        20900 -> "字幕不存在"
        30000 -> "服务器发生错误，请稍后重试"
        30001 -> "数据库不可用，请稍后重试"
        30002 -> "搜索引擎不可用，请稍后重试"
        30300 -> "API 暂时不可用，请稍后重试"
        30900 -> "请求配额超限，请稍后重试"
        else -> "未知 API 错误"
    }
    val codes = if (http == null) "status $status" else "HTTP $http，status $status"
    return SubtitleOperationException("ASSRT $reason（$codes）")
}
