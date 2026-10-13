package dev.joyfin.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

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
    private var serviceBase: String? = null
    private var cachedService: JellyfinService? = null

    @Synchronized
    private fun service(server: String = requireSession().server): JellyfinService {
        val base = normalizeServer(server) + "/"
        if (serviceBase != base) {
            cachedService =
                Retrofit.Builder()
                    .baseUrl(base)
                    .client(client)
                    .addConverterFactory(
                        AppJson.asConverterFactory("application/json; charset=utf-8".toMediaType())
                    )
                    .build()
                    .create(JellyfinService::class.java)
            serviceBase = base
        }
        return requireNotNull(cachedService)
    }

    private fun <T> Response<T>.checked(): Response<T> {
        if (!isSuccessful) {
            errorBody()?.close()
            throw ApiException(
                code(),
                when (code()) {
                    401 -> "登录已过期或用户名/密码错误"
                    403 -> "账号无权访问此内容或使用转码"
                    404 -> "内容或接口不存在，请检查服务器地址和子路径"
                    in 300..399 -> "服务器地址发生重定向，请直接填写最终地址"
                    else -> "服务器请求失败（HTTP ${code()}）"
                },
            )
        }
        return this
    }

    private fun <T> Response<T>.value(): T = checked().body() ?: throw IOException("服务器返回了空响应")

    private fun params(vararg values: Pair<String, Any?>): Map<String, String> =
        values.mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

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
                    url.fragment == null
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
                resolved.port == base.port
        ) {
            "服务器返回了不同来源的播放地址，无法安全附加登录凭证"
        }
        return resolved.toString()
    }

    private fun requireSession() = session ?: throw ApiException(401, "请先登录")

    override suspend fun connect(server: String): ServerInfo =
        service(server).publicInfo(authorization(null)).value()

    override suspend fun login(server: String, name: String, password: String): Session {
        val normalized = normalizeServer(server)
        val response =
            service(normalized).login(authorization(null), LoginRequest(name, password)).value()
        return Session(
            normalized,
            response.accessToken,
            response.user.id,
            response.user.name.ifBlank { name },
        )
    }

    override suspend fun validateSession() {
        service().validate(authorization(), requireSession().userId).value()
    }

    override suspend fun logout() {
        service().logout(authorization()).checked()
    }

    suspend fun resumeVideo(): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .resume(
                    authorization(),
                    params(
                        "UserId" to requireSession().userId,
                        "Limit" to 20,
                        "MediaTypes" to "Video",
                        "Fields" to browseFields,
                    ),
                )
                .value()
                .toMediaPage()
                .items
        }

    suspend fun videoLibraries(): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .views(authorization(), requireSession().userId)
                .value()
                .toMediaPage()
                .items
                .filter { it.isVideoLibrary() }
        }

    suspend fun recentVideo(): List<Media> = latestVideo()

    suspend fun latestInLibrary(id: String): List<Media> = latestVideo(id)

    private suspend fun latestVideo(id: String? = null): List<Media> =
        withContext(Dispatchers.Default) {
            groupLatestVideo(
                service()
                    .latest(
                        authorization(),
                        params(
                            "UserId" to requireSession().userId,
                            "ParentId" to id,
                            "Limit" to 20,
                            "GroupItems" to true,
                            "IncludeItemTypes" to "Movie,Episode,Video,MusicVideo",
                            "Fields" to browseFields,
                        ),
                    )
                    .value()
                    .map(Media::from)
            )
        }

    suspend fun items(parent: String? = null, search: String? = null, start: Int = 0): MediaPage =
        withContext(Dispatchers.Default) {
            val searching = !search.isNullOrBlank()
            service()
                .items(
                    authorization(),
                    params(
                        "UserId" to requireSession().userId,
                        "StartIndex" to start,
                        "Limit" to 40,
                        "Fields" to browseFields,
                        "SortBy" to "SortName",
                        "SortOrder" to "Ascending",
                        "ParentId" to parent,
                        "SearchTerm" to search?.takeIf { searching },
                        "Recursive" to true.takeIf { searching },
                        "IncludeItemTypes" to "Movie,Series,Episode".takeIf { searching },
                    ),
                )
                .value()
                .toMediaPage()
        }

    suspend fun item(id: String): Media =
        withContext(Dispatchers.Default) {
            Media.from(service().item(authorization(), id, requireSession().userId).value())
        }

    suspend fun seasons(seriesId: String): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .seasons(
                    authorization(),
                    seriesId,
                    params("UserId" to requireSession().userId, "Fields" to browseFields),
                )
                .value()
                .toMediaPage()
                .items
        }

    suspend fun episodes(seriesId: String, seasonId: String? = null): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .episodes(
                    authorization(),
                    seriesId,
                    params(
                        "UserId" to requireSession().userId,
                        "SeasonId" to seasonId,
                        "Fields" to browseFields,
                    ),
                )
                .value()
                .toMediaPage()
                .items
        }

    suspend fun similar(id: String): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .similar(
                    authorization(),
                    id,
                    params(
                        "UserId" to requireSession().userId,
                        "Limit" to 12,
                        "Fields" to browseFields,
                    ),
                )
                .value()
                .toMediaPage()
                .items
        }

    suspend fun person(name: String): Media =
        withContext(Dispatchers.Default) {
            Media.from(service().person(authorization(), name, requireSession().userId).value())
        }

    suspend fun personItems(id: String, start: Int = 0): MediaPage =
        withContext(Dispatchers.Default) {
            require(id.isNotBlank()) { "服务器未提供演员 ID" }
            service()
                .items(
                    authorization(),
                    params(
                        "UserId" to requireSession().userId,
                        "PersonIds" to id,
                        "Recursive" to true,
                        "IncludeItemTypes" to "Movie,Series",
                        "Fields" to browseFields,
                        "StartIndex" to start,
                        "Limit" to 40,
                    ),
                )
                .value()
                .toMediaPage()
        }

    suspend fun favorite(id: String, selected: Boolean) {
        val response =
            if (selected) service().favorite(authorization(), id, requireSession().userId)
            else service().unfavorite(authorization(), id, requireSession().userId)
        response.checked()
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
        profile: DeviceProfileDto,
        startTicks: Long,
        bitrate: Int = 80_000_000,
        audio: Int? = null,
        subtitle: Int? = -1,
        sourceId: String? = null,
        compatible: Boolean = false,
        preferences: PlaybackTrackPreferences? = null,
    ): PlaybackPlan {
        val knownSource = item.sources.firstOrNull { sourceId == null || it.id == sourceId }
        val discovering = (subtitle != -1 || preferences != null) && knownSource == null
        val knownStreams = knownSource?.streams.orEmpty()
        val selectedAudio = audio ?: preferences?.audio?.match("Audio", knownStreams)
        val selectedSubtitle =
            SubtitleDelivery.select(
                subtitle ?: preferences?.subtitleIndex(knownStreams),
                knownStreams,
            )
        val burnIn = SubtitleDelivery.requiresBurnIn(selectedSubtitle, knownStreams)
        val body =
            PlaybackRequest(
                userId = requireSession().userId,
                deviceProfile = profile,
                startTimeTicks = startTicks,
                maxStreamingBitrate = bitrate,
                maxAudioChannels =
                    dev.joyfin.playback.AudioPlaybackCapabilities.requestChannelLimit(profile),
                enableDirectPlay = !compatible && !burnIn,
                enableDirectStream = !compatible && !burnIn,
                allowVideoStreamCopy = !compatible && !burnIn,
                allowAudioStreamCopy = !compatible,
                subtitleStreamIndex = selectedSubtitle,
                autoOpenLiveStream = !discovering,
                audioStreamIndex = selectedAudio,
                mediaSourceId = sourceId,
            )
        val result =
            service()
                .playback(authorization(), item.id, requireSession().userId, !discovering, body)
                .value()
        if (!result.errorCode.isNullOrBlank() && result.errorCode != "null")
            throw IOException("服务器无法提供可用播放方案：${result.errorCode}")
        val source =
            result.sources.firstOrNull { sourceId == null || it.id == sourceId }
                ?: throw IOException("没有可播放的媒体源")
        if (discovering)
            return playback(
                item.copy(sources = listOf(source)),
                profile,
                startTicks,
                bitrate,
                audio,
                subtitle,
                source.id,
                compatible,
                preferences,
            )
        val directStreamUrl = source.directStreamUrl?.takeIf { it.isNotBlank() && it != "null" }
        val transcodingUrl = source.transcodingUrl?.takeIf { it.isNotBlank() && it != "null" }
        val direct = !compatible && !burnIn && source.supportsDirectPlay
        val stream =
            !compatible && !burnIn && source.supportsDirectStream && directStreamUrl != null
        val path =
            when {
                direct ->
                    url(
                            "Videos/${item.id}/stream",
                            mapOf(
                                "Static" to true,
                                "MediaSourceId" to source.id,
                                "DeviceId" to deviceId,
                            ),
                        )
                        .toString()
                stream -> playbackUrl(requireNotNull(directStreamUrl))
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
            source.streams.mapNotNull { track ->
                if (
                    track.type != "Subtitle" ||
                        track.index < 0 ||
                        track.deliveryMethod.equals("Encode", true)
                )
                    return@mapNotNull null
                val format = SubtitleDelivery.format(track.codec) ?: return@mapNotNull null
                Subtitle(
                    url(
                            "Videos/${item.id}/${source.id}/Subtitles/${track.index}/Stream.${format.extension}"
                        )
                        .toString(),
                    format.mime,
                    track.language.ifBlank { "und" },
                    track.displayTitle.ifBlank { "字幕" },
                    track.index,
                )
            }
        return PlaybackPlan(
            item,
            source,
            result.sessionId,
            path,
            if (direct) "DirectPlay" else if (stream) "DirectStream" else "Transcode",
            startTicks,
            selectedAudio,
            selectedSubtitle,
            bitrate,
            subtitles,
        )
    }

    suspend fun report(plan: PlaybackPlan, event: String, positionTicks: Long, paused: Boolean) =
        reportSnapshot(reportBody(plan, event, positionTicks, paused), event)

    fun reportBody(
        plan: PlaybackPlan,
        event: String,
        positionTicks: Long,
        paused: Boolean,
    ): PlaybackReport =
        PlaybackReport(
            itemId = plan.item.id,
            mediaSourceId = plan.sourceId,
            playSessionId = plan.sessionId,
            positionTicks = positionTicks,
            isPaused = paused.takeUnless { event == "Stopped" },
            canSeek = true.takeUnless { event == "Stopped" },
            playMethod = plan.method.takeUnless { event == "Stopped" },
            audioStreamIndex = plan.audioIndex.takeUnless { event == "Stopped" },
            subtitleStreamIndex = plan.subtitleIndex.takeUnless { event == "Stopped" },
        )

    suspend fun reportSnapshot(body: PlaybackReport, event: String) {
        val response =
            when (event) {
                "" -> service().started(authorization(), body)
                "Progress" -> service().progress(authorization(), body)
                "Stopped" -> service().stopped(authorization(), body)
                else -> error("未知播放上报事件")
            }
        response.checked()
    }

    suspend fun stopTranscoding(plan: PlaybackPlan) {
        if (plan.method != "DirectPlay") stopTranscoding(plan.sessionId)
    }

    suspend fun stopTranscoding(sessionId: String) {
        if (sessionId.isBlank()) return
        service().stopEncoding(authorization(), deviceId, sessionId).checked()
    }
}
