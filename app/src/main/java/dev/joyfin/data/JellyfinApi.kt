package dev.joyfin.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class JellyfinApi(
    val deviceId: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
) : SessionConnection {
    @Volatile override var session: Session? = null
    private val browseFields =
        "Overview,People,Genres,MediaSources,ProductionLocations,ChildCount,SeasonUserData"

    companion object {
        fun normalizeServer(value: String): String {
            val raw = value.trim()
            require(raw.isNotBlank()) { "请填写服务器地址" }
            val candidate =
                if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(raw)) raw
                else "http://$raw"
            val url = requireNotNull(candidate.toHttpUrlOrNull()) { "地址格式不正确，请检查协议、主机和端口" }
            require(
                url.username.isEmpty() &&
                    url.password.isEmpty() &&
                    url.query == null &&
                    url.fragment == null,
            ) {
                "请填写服务器根地址，不包含账号、查询参数或页面锚点"
            }
            return url.toString().trimEnd('/')
        }
    }

    fun authorization(token: String? = session?.token): String =
        "MediaBrowser Client=\"Joyfin\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"0.1.0\"" +
            if (token.isNullOrBlank()) "" else ", Token=\"${token.replace("\"", "")}\""

    fun url(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        server: String = requireSession().server,
    ): HttpUrl {
        val builder = (server.trimEnd('/') + "/" + path.trimStart('/')).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v.toString()) }
        return builder.build()
    }

    fun playbackUrl(relative: String): String {
        val base = (requireSession().server + "/").toHttpUrl()
        // Root-relative URLs include the server's reverse-proxy base path; ordinary relatives
        // retain it.
        val resolved = base.resolve(relative) ?: throw IOException("服务器返回了无效播放地址")
        require(
            resolved.scheme == base.scheme &&
                resolved.host == base.host &&
                resolved.port == base.port,
        ) {
            "服务器返回了不同来源的播放地址，无法安全附加登录凭证"
        }
        return resolved.toString()
    }

    private fun requireSession() = session ?: throw ApiException(401, "请先登录")

    suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        query: Map<String, Any?> = emptyMap(),
        server: String = requireSession().server,
        token: String? = session?.token,
    ): String {
        val builder =
            Request.Builder()
                .url(url(path, query, server))
                .header("Authorization", authorization(token))
                .header("Accept", "application/json")
        if (method != "GET")
            builder.method(
                method,
                if (method == "DELETE") null
                else
                    (body?.toString() ?: "{}").toRequestBody(
                        "application/json; charset=utf-8".toMediaType(),
                    ),
            )
        val call = client.newCall(builder.build())
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (!cont.isActive) return
                                val text = it.body?.string().orEmpty()
                                if (it.isSuccessful) cont.resume(text)
                                else
                                    cont.resumeWithException(
                                        ApiException(
                                            it.code,
                                            when (it.code) {
                                                401 -> "登录已过期或用户名/密码错误"
                                                403 -> "账号无权访问此内容或使用转码"
                                                404 -> "内容或接口不存在，请检查服务器地址和子路径"
                                                in 300..399 -> "服务器地址发生重定向，请直接填写最终地址"
                                                else -> "服务器请求失败（HTTP ${it.code}）"
                                            },
                                        ),
                                    )
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                },
            )
        }
    }

    override suspend fun connect(server: String): JSONObject =
        JSONObject(request("System/Info/Public", server = normalizeServer(server), token = null))

    override suspend fun login(server: String, name: String, password: String): Session {
        val normalized = normalizeServer(server)
        val response =
            JSONObject(
                request(
                    "Users/AuthenticateByName",
                    "POST",
                    JSONObject().put("Username", name).put("Pw", password),
                    server = normalized,
                    token = null,
                ),
            )
        val user = response.getJSONObject("User")
        return Session(
            normalized,
            response.getString("AccessToken"),
            user.getString("Id"),
            user.optString("Name", name),
        )
    }

    override suspend fun validateSession() {
        request("Users/${requireSession().userId}")
    }

    override suspend fun logout() {
        request("Sessions/Logout", "POST")
    }

    suspend fun resumeVideo(): List<Media> =
        withContext(Dispatchers.Default) {
            JSONObject(
                    request(
                        "UserItems/Resume",
                        query =
                            mapOf(
                                "UserId" to requireSession().userId,
                                "Limit" to 20,
                                "MediaTypes" to "Video",
                                "Fields" to browseFields,
                            ),
                    ),
                )
                .mediaPage()
                .items
        }

    suspend fun videoLibraries(): List<Media> =
        withContext(Dispatchers.Default) {
            JSONObject(request("UserViews", query = mapOf("UserId" to requireSession().userId)))
                .mediaPage()
                .items
                .filter { it.isVideoLibrary() }
        }

    suspend fun recentVideo(): List<Media> = latestVideo()

    suspend fun latestInLibrary(id: String): List<Media> = latestVideo(id)

    private suspend fun latestVideo(id: String? = null): List<Media> =
        withContext(Dispatchers.Default) {
            val result =
                JSONArray(
                        request(
                            "Items/Latest",
                            query =
                                mapOf(
                                    "UserId" to requireSession().userId,
                                    "ParentId" to id,
                                    "Limit" to 20,
                                    "GroupItems" to true,
                                    "IncludeItemTypes" to "Movie,Episode,Video,MusicVideo",
                                    "Fields" to browseFields,
                                ),
                        ),
                    )
                    .objects()
                    .map(Media::from)
            return@withContext groupLatestVideo(result)
        }

    suspend fun items(parent: String? = null, search: String? = null, start: Int = 0): MediaPage =
        withContext(Dispatchers.Default) {
            val query =
                mutableMapOf<String, Any?>(
                    "UserId" to requireSession().userId,
                    "StartIndex" to start,
                    "Limit" to 40,
                    "Fields" to browseFields,
                    "SortBy" to "SortName",
                    "SortOrder" to "Ascending",
                )
            if (parent != null) query["ParentId"] = parent
            if (!search.isNullOrBlank()) {
                query["SearchTerm"] = search
                query["Recursive"] = true
                query["IncludeItemTypes"] = "Movie,Series,Episode"
            }
            return@withContext JSONObject(request("Items", query = query)).mediaPage()
        }

    suspend fun item(id: String): Media =
        withContext(Dispatchers.Default) {
            Media.from(
                JSONObject(
                    request("Items/$id", query = mapOf("UserId" to requireSession().userId)),
                ),
            )
        }

    suspend fun seasons(seriesId: String): List<Media> =
        withContext(Dispatchers.Default) {
            JSONObject(
                    request(
                        "Shows/$seriesId/Seasons",
                        query =
                            mapOf("UserId" to requireSession().userId, "Fields" to browseFields),
                    ),
                )
                .mediaPage()
                .items
        }

    suspend fun episodes(seriesId: String, seasonId: String? = null): List<Media> =
        withContext(Dispatchers.Default) {
            JSONObject(
                    request(
                        "Shows/$seriesId/Episodes",
                        query =
                            mapOf(
                                "UserId" to requireSession().userId,
                                "SeasonId" to seasonId,
                                "Fields" to browseFields,
                            ),
                    ),
                )
                .mediaPage()
                .items
        }

    suspend fun similar(id: String): List<Media> =
        withContext(Dispatchers.Default) {
            JSONObject(
                    request(
                        "Items/$id/Similar",
                        query =
                            mapOf(
                                "UserId" to requireSession().userId,
                                "Limit" to 12,
                                "Fields" to browseFields,
                            ),
                    ),
                )
                .mediaPage()
                .items
        }

    suspend fun person(name: String): Media =
        withContext(Dispatchers.Default) {
            Media.from(
                JSONObject(
                    request(
                        "Persons/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}",
                        query = mapOf("UserId" to requireSession().userId),
                    ),
                ),
            )
        }

    suspend fun personItems(id: String, start: Int = 0): MediaPage =
        withContext(Dispatchers.Default) {
            require(id.isNotBlank()) { "服务器未提供演员 ID" }
            return@withContext JSONObject(
                    request(
                        "Items",
                        query =
                            mapOf(
                                "UserId" to requireSession().userId,
                                "PersonIds" to id,
                                "Recursive" to true,
                                "IncludeItemTypes" to "Movie,Series",
                                "Fields" to browseFields,
                                "StartIndex" to start,
                                "Limit" to 40,
                            ),
                    ),
                )
                .mediaPage()
        }

    suspend fun favorite(id: String, selected: Boolean) {
        request(
            "UserFavoriteItems/$id",
            if (selected) "POST" else "DELETE",
            query = mapOf("UserId" to requireSession().userId),
        )
    }

    suspend fun nextEpisode(current: Media): Media? {
        if (current.type != "Episode" || current.seriesId.isBlank()) return null
        val all = episodes(current.seriesId)
        val index = all.indexOfFirst { it.id == current.id }
        return if (index >= 0) all.getOrNull(index + 1) else null
    }

    fun image(media: Media): String =
        url(
                "Items/${media.imageId}/Images/Primary",
                mapOf(
                    "MaxWidth" to 320,
                    "Quality" to 85,
                    "Tag" to media.imageTag.takeIf { it.isNotBlank() },
                ),
            )
            .toString()

    /** Episode thumbnails must never inherit a series poster or backdrop. */
    fun episodeImage(media: Media): String? =
        media.episodeImageTag
            .takeIf { media.type == "Episode" && it.isNotBlank() }
            ?.let { tag ->
                url(
                        "Items/${media.id}/Images/Primary",
                        mapOf("MaxWidth" to 640, "Quality" to 85, "Tag" to tag),
                    )
                    .toString()
            }

    fun backdrop(media: Media): String =
        if (media.backdropTag.isNotBlank())
            url(
                    "Items/${media.backdropId}/Images/Backdrop/0",
                    mapOf("MaxWidth" to 1920, "Quality" to 85, "Tag" to media.backdropTag),
                )
                .toString()
        else image(media)

    fun logo(media: Media): String? =
        media.logoTag
            .takeIf { it.isNotBlank() }
            ?.let {
                url("Items/${media.logoId}/Images/Logo", mapOf("MaxWidth" to 700, "Tag" to it))
                    .toString()
            }

    fun personImage(person: Person): String? =
        person.id
            .takeIf { it.isNotBlank() && person.imageTag.isNotBlank() }
            ?.let {
                url("Items/$it/Images/Primary", mapOf("MaxWidth" to 240, "Tag" to person.imageTag))
                    .toString()
            }

    suspend fun playback(
        item: Media,
        profile: JSONObject,
        startTicks: Long,
        bitrate: Int = 80_000_000,
        audio: Int? = null,
        subtitle: Int? = -1,
        sourceId: String? = null,
        compatible: Boolean = false,
        preferences: PlaybackTrackPreferences? = null,
    ): PlaybackPlan {
        val knownSource =
            item.sources.firstOrNull { sourceId == null || it.optString("Id") == sourceId }
        val discovering = (subtitle != -1 || preferences != null) && knownSource == null
        val knownStreams = knownSource?.optJSONArray("MediaStreams").objects()
        val selectedAudio = audio ?: preferences?.audio?.match("Audio", knownStreams)
        val selectedSubtitle =
            SubtitleDelivery.select(
                subtitle ?: preferences?.subtitleIndex(knownStreams),
                knownStreams,
            )
        val burnIn =
            SubtitleDelivery.requiresBurnIn(
                selectedSubtitle,
                knownSource?.optJSONArray("MediaStreams").objects(),
            )
        val body =
            JSONObject()
                .put("UserId", requireSession().userId)
                .put("DeviceProfile", profile)
                .put("StartTimeTicks", startTicks)
                .put("MaxStreamingBitrate", bitrate)
                .put(
                    "MaxAudioChannels",
                    dev.joyfin.playback.AudioPlaybackCapabilities.requestChannelLimit(profile),
                )
                .put("EnableDirectPlay", !compatible && !burnIn)
                .put("EnableDirectStream", !compatible && !burnIn)
                .put("EnableTranscoding", true)
                .put("AllowVideoStreamCopy", !compatible && !burnIn)
                .put("AllowAudioStreamCopy", !compatible)
                .put("SubtitleStreamIndex", selectedSubtitle)
                .put("AutoOpenLiveStream", !discovering)
        selectedAudio?.let { body.put("AudioStreamIndex", it) }
        sourceId?.let { body.put("MediaSourceId", it) }
        val result =
            JSONObject(
                request(
                    "Items/${item.id}/PlaybackInfo",
                    "POST",
                    body,
                    mapOf("UserId" to requireSession().userId, "IsPlayback" to !discovering),
                ),
            )
        if (result.optString("ErrorCode").isNotBlank() && result.optString("ErrorCode") != "null")
            throw IOException("服务器无法提供可用播放方案：${result.optString("ErrorCode")}")
        val sources = result.optJSONArray("MediaSources").objects()
        val source =
            sources.firstOrNull { sourceId == null || it.optString("Id") == sourceId }
                ?: throw IOException("没有可播放的媒体源")
        if (discovering) {
            return playback(
                item.copy(sources = listOf(source)),
                profile,
                startTicks,
                bitrate,
                audio,
                subtitle,
                source.optString("Id"),
                compatible,
                preferences,
            )
        }
        val sessionId = result.optString("PlaySessionId")
        val directStreamUrl =
            source.optString("DirectStreamUrl").takeIf { it.isNotBlank() && it != "null" }
        val transcodingUrl =
            source.optString("TranscodingUrl").takeIf { it.isNotBlank() && it != "null" }
        val direct = !compatible && !burnIn && source.optBoolean("SupportsDirectPlay")
        val stream =
            !compatible &&
                !burnIn &&
                source.optBoolean("SupportsDirectStream") &&
                directStreamUrl != null
        val path =
            when {
                direct ->
                    url(
                            "Videos/${item.id}/stream",
                            mapOf(
                                "Static" to true,
                                "MediaSourceId" to source.optString("Id"),
                                "DeviceId" to deviceId,
                            ),
                        )
                        .toString()
                stream -> playbackUrl(directStreamUrl!!)
                transcodingUrl != null ->
                    playbackUrl(transcodingUrl)
                        .toHttpUrl()
                        .newBuilder()
                        .setQueryParameter("StartTimeTicks", "0")
                        .build()
                        .toString()
                else -> throw IOException("此格式无法直接播放，服务器也未提供转码方案，请检查转码权限")
            }
        val subtitles =
            source.optJSONArray("MediaStreams").objects().mapNotNull { track ->
                if (
                    track.optString("Type") != "Subtitle" ||
                        track.optInt("Index", -1) < 0 ||
                        track.optString("DeliveryMethod").equals("Encode", ignoreCase = true)
                )
                    return@mapNotNull null
                val format =
                    SubtitleDelivery.format(track.optString("Codec")) ?: return@mapNotNull null
                Subtitle(
                    url(
                            "Videos/${item.id}/${source.optString("Id")}/Subtitles/${track.optInt("Index")}/Stream.${format.extension}",
                        )
                        .toString(),
                    format.mime,
                    track.optString("Language", "und"),
                    track.optString("DisplayTitle", "字幕"),
                    track.optInt("Index"),
                )
            }
        return PlaybackPlan(
            item,
            source,
            sessionId,
            path,
            if (direct) "DirectPlay" else if (stream) "DirectStream" else "Transcode",
            startTicks,
            selectedAudio,
            selectedSubtitle,
            bitrate,
            subtitles,
        )
    }

    suspend fun report(plan: PlaybackPlan, event: String, positionTicks: Long, paused: Boolean) {
        val body = reportBody(plan, event, positionTicks, paused)
        request("Sessions/Playing${if (event.isEmpty()) "" else "/$event"}", "POST", body)
    }

    fun reportBody(
        plan: PlaybackPlan,
        event: String,
        positionTicks: Long,
        paused: Boolean,
    ): JSONObject {
        val body =
            JSONObject()
                .put("ItemId", plan.item.id)
                .put("MediaSourceId", plan.sourceId)
                .put("PlaySessionId", plan.sessionId)
                .put("PositionTicks", positionTicks)
        if (event != "Stopped")
            body
                .put("IsPaused", paused)
                .put("CanSeek", true)
                .put("PlayMethod", plan.method)
                .put("AudioStreamIndex", plan.audioIndex)
                .put("SubtitleStreamIndex", plan.subtitleIndex)
        return body
    }

    suspend fun stopTranscoding(plan: PlaybackPlan) {
        if (plan.method != "DirectPlay") stopTranscoding(plan.sessionId)
    }

    suspend fun stopTranscoding(sessionId: String) {
        // A blank session id must never become a device-wide kill request.
        if (sessionId.isBlank()) return
        request(
            "Videos/ActiveEncodings",
            "DELETE",
            query = mapOf("DeviceId" to deviceId, "PlaySessionId" to sessionId),
        )
    }
}
