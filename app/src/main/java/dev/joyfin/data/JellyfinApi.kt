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
) : SessionConnection, QuickConnectConnection {
    @Volatile override var session: Session? = null
    private val browseFields =
        "Overview,People,Genres,MediaSources,ProductionLocations,ChildCount,SeasonUserData,OriginalTitle,ProviderIds,Path"
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
        private const val AUDIT_FIELDS = "Overview,ProviderIds,MediaSources,MediaStreams"
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

    suspend fun auditWorks(owner: Session, parent: String?, start: Int): AuditPageDto {
        check(session == owner) { "账号已切换" }
        return service(owner.server).auditItems(authorization(owner.token), params(
            "UserId" to owner.userId, "ParentId" to parent, "Recursive" to true,
            "IncludeItemTypes" to "Movie,Series", "Fields" to AUDIT_FIELDS,
            "SortBy" to "SortName", "SortOrder" to "Ascending", "StartIndex" to start, "Limit" to 100,
            "EnableUserData" to false,
        )).value().also { check(session == owner) { "账号已切换" } }
    }

    suspend fun auditItem(owner: Session, id: String): AuditItemDto {
        check(session == owner) { "账号已切换" }
        return service(owner.server).auditItem(authorization(owner.token), id, owner.userId, AUDIT_FIELDS)
            .value().also { check(session == owner) { "账号已切换" } }
    }

    suspend fun auditEpisodes(owner: Session, id: String, missing: Boolean, start: Int): AuditPageDto {
        check(session == owner) { "账号已切换" }
        return service(owner.server).auditEpisodes(authorization(owner.token), id, params(
            "UserId" to owner.userId, "IsMissing" to missing, "Fields" to AUDIT_FIELDS,
            "StartIndex" to start, "Limit" to 100, "EnableUserData" to false,
        )).value().also { check(session == owner) { "账号已切换" } }
    }

    suspend fun subtitleWorks(search: String, start: Int = 0): MediaPage =
        service().items(authorization(), params(
            "UserId" to requireSession().userId, "SearchTerm" to search,
            "Recursive" to true, "IncludeItemTypes" to "Movie,Series",
            "Fields" to browseFields, "StartIndex" to start, "Limit" to 40,
            "SortBy" to "SortName", "SortOrder" to "Ascending",
        )).value().toMediaPage()

    suspend fun uploadSubtitle(owner: Session, itemId: String, body: UploadSubtitleRequest) {
        check(session == owner) { "账号已切换，请重新打开字幕管理" }
        service(owner.server).uploadSubtitle(authorization(owner.token), itemId, body).checked()
    }

    suspend fun deleteSubtitle(owner: Session, itemId: String, index: Int) {
        check(session == owner) { "账号已切换，请重新打开字幕管理" }
        service(owner.server).deleteSubtitle(authorization(owner.token), itemId, index).checked()
    }

    suspend fun subtitleContent(owner: Session, itemId: String, index: Int, format: String): ByteArray = withContext(Dispatchers.IO) {
        check(session == owner) { "账号已切换" }
        service(owner.server).subtitleContent(authorization(owner.token), itemId, itemId, index, format).value().use { body ->
            val output = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 20 * 1024 * 1024) { "服务器字幕过大" }
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        }
    }

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

    override suspend fun initiateQuickConnect(server: String): QuickConnectResult {
        val endpoint = service(server)
        if (!endpoint.quickConnectEnabled(authorization(null)).value())
            throw QuickConnectException("此服务器未开启快速连接，请使用账号密码登录，或联系管理员开启 Quick Connect")
        return endpoint.initiateQuickConnect(authorization(null)).value().also {
            if (it.secret.isBlank() || !it.code.matches(Regex("[0-9]{6}")))
                throw QuickConnectException("服务器返回的快速登录码无效，请重新获取")
        }
    }

    override suspend fun pollQuickConnect(server: String, secret: String): QuickConnectResult =
        service(server).pollQuickConnect(authorization(null), secret).value()

    override suspend fun authenticateQuickConnect(server: String, secret: String): Session {
        val normalized = normalizeServer(server)
        val response = service(normalized).authenticateQuickConnect(authorization(null), QuickConnectRequest(secret)).value()
        if (response.accessToken.isBlank() || response.user.id.isBlank())
            throw QuickConnectException("服务器未返回有效登录信息")
        return Session(normalized, response.accessToken, response.user.id, response.user.name)
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

    /** Home library rails sort works by their own addition date, not new episodes in old series. */
    suspend fun latestInLibrary(id: String): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .items(
                    authorization(),
                    params(
                        "UserId" to requireSession().userId,
                        "ParentId" to id,
                        "Recursive" to true,
                        "IncludeItemTypes" to "Movie,Series,Video,MusicVideo",
                        "SortBy" to "DateCreated,SortName",
                        "SortOrder" to "Descending",
                        "Limit" to 20,
                        "EnableTotalRecordCount" to false,
                        "Fields" to browseFields,
                    ),
                )
                .value()
                .toMediaPage()
                .items
        }

    private suspend fun latestVideo(): List<Media> =
        withContext(Dispatchers.Default) {
            groupLatestVideo(
                service()
                    .latest(
                        authorization(),
                        params(
                            "UserId" to requireSession().userId,
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

    suspend fun items(parent: String? = null, search: String? = null, start: Int = 0,
        libraryOptions: LibraryBrowseOptions? = null, worksOnly: Boolean = false): MediaPage =
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
                        "SortBy" to (libraryOptions?.sortBy ?: "SortName"),
                        "SortOrder" to (libraryOptions?.sortOrder ?: "Ascending"),
                        "IsPlayed" to libraryOptions?.watched?.played,
                        "ParentId" to parent,
                        "SearchTerm" to search?.takeIf { searching },
                        "Recursive" to true.takeIf { searching || worksOnly },
                        "IncludeItemTypes" to when {
                            searching -> "Movie,Series,Episode"
                            worksOnly -> "Movie,Series,Video,MusicVideo"
                            else -> null
                        },
                    ),
                )
                .value()
                .toMediaPage()
        }

    suspend fun item(id: String): Media =
        withContext(Dispatchers.Default) {
            Media.from(service().item(authorization(), id, requireSession().userId).value())
        }

    suspend fun mediaSegments(id: String): List<MediaSegment> {
        val response = service().segments(authorization(), id)
        // Older servers have no segment endpoint. This optional metadata never gates playback.
        if (response.code() in setOf(404, 405)) { response.errorBody()?.close(); return emptyList() }
        return response.value().items.filter { it.itemId.isBlank() || it.itemId == id }
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

    /** Whole-series viewing order needs user data, not every episode's playback sources or artwork. */
    suspend fun episodeProgress(seriesId: String): List<Media> =
        withContext(Dispatchers.Default) {
            service()
                .episodes(
                    authorization(),
                    seriesId,
                    params(
                        "UserId" to requireSession().userId,
                        "Fields" to "ParentId",
                        "EnableImages" to false,
                        "EnableUserData" to true,
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

    suspend fun setPlayed(id: String, played: Boolean) {
        val user = requireSession().userId
        val response =
            if (played) service().markPlayed(authorization(), user, id)
            else service().markUnplayed(authorization(), user, id)
        response.checked()
    }

    suspend fun removeResume(id: String) {
        service().removeResume(authorization(), requireSession().userId, id).checked()
    }

    suspend fun nextEpisode(current: Media): Media? {
        if (current.type != "Episode" || current.seriesId.isBlank()) return null
        return withContext(Dispatchers.Default) {
            // Keep the server's series viewing order (including cross-season transitions),
            // but only transfer the current and following episode, without playback sources.
            val rows = service().episodes(authorization(), current.seriesId, params(
                "UserId" to requireSession().userId,
                "StartItemId" to current.id,
                "Limit" to 2,
                "IsMissing" to false,
                "Fields" to "ParentId",
                "EnableImages" to false,
                "EnableUserData" to false,
            )).value().toMediaPage().items
            if (rows.firstOrNull()?.id != current.id) null else rows.getOrNull(1)?.takeIf {
                it.id != current.id && it.type == "Episode" &&
                    (it.seriesId.isBlank() || it.seriesId == current.seriesId)
            }
        }
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

    fun chapterImage(media: Media, index: Int, tag: String): String? =
        tag.takeIf { it.isNotBlank() }
            ?.let {
                url("Items/${media.id}/Images/Chapter/$index",
                    mapOf("MaxWidth" to 320, "Quality" to 85, "Tag" to it)).toString()
            }

    suspend fun playback(
        item: Media,
        profile: DeviceProfileDto,
        startTicks: Long,
        bitrate: Int = PlaybackQuality.DEFAULT,
        audio: Int? = null,
        subtitle: Int? = -1,
        sourceId: String? = null,
        compatible: Boolean = false,
        preferences: PlaybackTrackPreferences? = null,
        profileForSource: ((MediaSource, Int?) -> DeviceProfileDto)? = null,
        audioTranscode: Boolean = false,
        sourcePolicy: (suspend (MediaSource, Int?) -> PlaybackSourcePolicy)? = null,
    ): PlaybackPlan {
        val knownSource = item.sources.firstOrNull { sourceId == null || it.id == sourceId }
        val discovering =
            (sourcePolicy != null || profileForSource != null || subtitle != -1 || preferences != null) &&
                knownSource == null
        val knownStreams = knownSource?.streams.orEmpty()
        val selectedSourceId = sourceId ?: knownSource?.id
        val selectedAudio = audio ?: preferences?.audio?.match("Audio", knownStreams)
        val policy = knownSource?.let { sourcePolicy?.invoke(it, selectedAudio) }
        val effectiveProfile = policy?.profile ?: knownSource?.let { profileForSource?.invoke(it, selectedAudio) } ?: profile
        val convertVideo = compatible || policy?.videoTranscode == true
        val convertAudio = audioTranscode || policy?.audioTranscode == true
        fun unavailableSourceMessage(): String = if (policy?.discSource == true)
            "服务器未提供可播放的原盘主影片流；当前不支持客户端 ISO／蓝光菜单导航，请使用主影片流或 MKV Remux"
            else "此格式无法直接播放，服务器也未提供转码方案，请检查转码权限"
        fun usableStreamUrl(url: String?): String? = url?.takeIf { value ->
            value.isNotBlank() && value != "null" && (policy?.discSource != true ||
                listOf(".iso", ".bdmv", ".mpls", ".ifo").none { value.substringBefore('?').endsWith(it, true) })
        }
        val selectedSubtitle =
            SubtitleDelivery.select(
                subtitle ?: preferences?.subtitleIndex(knownStreams),
                knownStreams,
            )
        val burnIn =
            SubtitleDelivery.requiresBurnIn(
                selectedSubtitle,
                knownStreams,
                knownSource?.container.orEmpty(),
            )
        val body =
            PlaybackRequest(
                userId = requireSession().userId,
                deviceProfile = effectiveProfile,
                startTimeTicks = startTicks,
                maxStreamingBitrate = bitrate,
                maxAudioChannels =
                    policy?.maxAudioChannels ?: dev.joyfin.playback.AudioPlaybackCapabilities.requestChannelLimit(
                        effectiveProfile
                    ),
                enableDirectPlay = !convertVideo && !burnIn && !convertAudio && policy?.discSource != true,
                enableDirectStream = !convertVideo && !burnIn && !convertAudio,
                allowVideoStreamCopy = !convertVideo && !burnIn,
                allowAudioStreamCopy = !compatible && !convertAudio,
                subtitleStreamIndex = selectedSubtitle,
                autoOpenLiveStream = !discovering,
                audioStreamIndex = selectedAudio,
                mediaSourceId = selectedSourceId,
            )
        val result =
            service()
                .playback(authorization(), item.id, requireSession().userId, !discovering, body)
                .value()
        if (!result.errorCode.isNullOrBlank() && result.errorCode != "null")
            throw IOException(if (policy?.discSource == true) unavailableSourceMessage()
                else "服务器无法提供可用播放方案：${result.errorCode}")
        val source =
            result.sources.firstOrNull { selectedSourceId == null || it.id == selectedSourceId }
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
                profileForSource,
                audioTranscode,
                sourcePolicy,
            )
        val directStreamUrl = usableStreamUrl(source.directStreamUrl)
        val transcodingUrl = usableStreamUrl(source.transcodingUrl)
        val direct = !convertVideo && !burnIn && !convertAudio && policy?.discSource != true && source.supportsDirectPlay
        val stream =
            !convertVideo &&
                !burnIn &&
                !convertAudio &&
                source.supportsDirectStream &&
                directStreamUrl != null
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
                else -> throw IOException(unavailableSourceMessage())
            }
        val subtitles =
            source.streams.mapNotNull { track ->
                if (!SubtitleDelivery.needsExternalSource(track, selectedSubtitle, direct, source.container))
                    return@mapNotNull null
                val format = SubtitleDelivery.externalFormat(track) ?: return@mapNotNull null
                Subtitle(
                    if (
                        track.deliveryMethod.equals("External", true) &&
                            track.deliveryUrl.isNotBlank()
                    )
                        playbackUrl(track.deliveryUrl)
                    else
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
            if (direct) emptyList()
            else
                path
                    .toHttpUrl()
                    .queryParameter("TranscodeReasons")
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    .orEmpty(),
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
