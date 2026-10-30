package dev.joyfin.update

import dev.joyfin.BuildConfig
import dev.joyfin.data.AppJson
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*

/** A separate unauthenticated client: Jellyfin and subtitle credentials never leave their servers. */
internal class ReleaseRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val text = text("https://api.github.com/repos/guhh110/Joyfin/releases/latest", 512 * 1024)
        AppJson.decodeFromString<GitHubRelease>(text).asUpdate(BuildConfig.VERSION_NAME)
    }

    suspend fun download(release: AppRelease, temporary: File, progress: suspend (Int) -> Unit): Unit = withContext(Dispatchers.IO) {
        val expected = releaseChecksum(text(release.checksumUrl, 65_536), release.apkName)
        val directory = requireNotNull(temporary.parentFile)
        check(directory.usableSpace > release.apkBytes + 16L * 1024 * 1024) { "存储空间不足，请清理后重试" }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastPercent = -1
            response(release.apkUrl) { body ->
                check(body.contentLength() < 0 || body.contentLength() == release.apkBytes) { "安装包大小与发布信息不一致" }
                temporary.outputStream().use { output ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= release.apkBytes) { "安装包超过发布大小" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val percent = (received * 100 / release.apkBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) { progress(percent); lastPercent = percent }
                    }
                }
            }
            check(received == release.apkBytes) { "安装包下载不完整，请重试" }
            check(digest.digest().hex() == expected) { "安装包校验未通过，请重新下载" }
            currentCoroutineContext().ensureActive()
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    private suspend fun text(url: String, limit: Int): String = response(url) { body ->
        check(body.contentLength() <= limit) { "发布信息超过大小限制" }
        val output = ByteArrayOutputStream()
        val input = body.byteStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= limit) { "发布信息超过大小限制" }
            output.write(buffer, 0, count)
        }
        output.toByteArray().toString(Charsets.UTF_8)
    }

    private suspend fun <T> response(url: String, read: suspend (ResponseBody) -> T): T = coroutineScope {
        var next = url
        repeat(6) {
            val call = client.newCall(Request.Builder().url(next)
                .header("User-Agent", "Joyfin/${BuildConfig.VERSION_NAME}")
                .header("Accept", if (next == "https://api.github.com/repos/guhh110/Joyfin/releases/latest")
                    "application/vnd.github+json" else "application/octet-stream")
                .build())
            // Keep cancellation attached until streaming finishes, not only until headers arrive.
            val cancelBody = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                val result = call.awaitResponse()
                result.use {
                    if (result.code in setOf(301, 302, 303, 307, 308)) {
                        val target = result.header("Location")?.let { result.request.url.resolve(it) }
                        check(target != null && target.scheme == "https" && target.port == 443 &&
                            target.username.isEmpty() && target.password.isEmpty() &&
                            target.host in setOf("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                            "更新下载跳转地址无效"
                        }
                        next = target.toString()
                    } else {
                        check(result.isSuccessful) {
                            when (result.code) {
                                403, 429 -> "更新服务暂时限流，请稍后重试"
                                404 -> "暂未找到可用的正式发布"
                                else -> "更新服务暂时不可用（HTTP ${result.code}）"
                            }
                        }
                        return@coroutineScope read(checkNotNull(result.body) { "更新服务返回空响应" })
                    }
                }
            } finally {
                call.cancel()
                cancelBody.cancelAndJoin()
            }
        }
        error("更新下载重定向次数过多")
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
