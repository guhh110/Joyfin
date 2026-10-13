@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.joyfin.BuildConfig
import dev.joyfin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One playback engine, independent of Compose, activities, dialogs and visual styling. */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isStarted: () -> Boolean,
    private val onClose: (authExpired: Boolean) -> Unit,
    itemId: String,
    startTicks: Long,
    initialSourceId: String?,
    restored: Bundle?,
) {
    private val store = SessionStore(context)
    private val api = JellyfinApi(store.deviceId).apply { session = store.load() }
    private val reporter = PlaybackReporter(api, store)
    private val preferenceStore = PlaybackPreferenceStore(context)
    private var pendingPreference: Pair<String, PlaybackTrackPreferences>? = null
    private val mutable = MutableStateFlow(PlayerUiState())
    val state = mutable.asStateFlow()
    private var currentId = restored?.getString("itemId") ?: itemId
    private var initialTicks = restored?.getLong("position") ?: startTicks
    private var sourceId = restored?.getString("source") ?: initialSourceId
    private var bitrate = restored?.getInt("bitrate", 80_000_000) ?: 80_000_000
    private var audioIndex = restored?.getInt("audio", -1)?.takeIf { it >= 0 }
    private var subtitleIndex: Int? =
        restored?.takeIf { it.containsKey("subtitle") }?.getInt("subtitle")
    private var compatible = restored?.getBoolean("compatible") ?: false
    private var fallbackUsed = restored?.getBoolean("fallbackUsed") ?: false
    private var settingsItemId: String? = if (restored != null) currentId else null
    private var playbackGeneration = 0L
    private var item: Media? = null
    private var plan: PlaybackPlan? = null
    private var next: Media? = null
    private var started = false
    private var released = false
    private var pendingPlay: Boolean? = null
    private var wasPlaying = false
    private var suspendedPlayback = false
    private var loadJob: Job? = null
    private var metadataJob: Job? = null
    private var seasonJob: Job? = null
    private var countdown: Job? = null
    private var pollJob: Job? = null
    private var reportJob: Job? = null

    private val completion = PlaybackCompletionGate()
    private val subtitleRendering = SubtitleRendering()
    private val http =
        DefaultHttpDataSource.Factory()
            .setUserAgent("Joyfin/${BuildConfig.VERSION_NAME}")
            .setDefaultRequestProperties(mapOf("Authorization" to api.authorization()))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
    val player: ExoPlayer =
        subtitleRendering
            .configure(ExoPlayer.Builder(context), context, http)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    private val mediaSession = MediaSession.Builder(context, player).build()

    fun bindSubtitles(view: androidx.media3.ui.SubtitleView) = subtitleRendering.bind(view)

    private val seekPreview =
        SeekPreviewController(
            scope,
            {
                val ui = mutable.value
                SeekEnvironment(
                    player.currentPosition.coerceAtLeast(0),
                    ui.durationMs,
                    ui.canSeek &&
                        player.isCurrentMediaItemSeekable &&
                        !ui.loading &&
                        ui.error == null,
                )
            },
            { preview -> mutable.update { it.copy(seekPreview = preview) } },
            ::seek,
        )

    val actions =
        PlayerActions(
            toggle = ::toggle,
            preview = seekPreview.actions,
            seek = ::seek,
            selectEpisode = ::playEpisode,
            selectSeason = ::selectSeason,
            selectAudio = { index ->
                if (canSelectTrack("Audio", index)) {
                    stagePreference("Audio", index)
                    if (mutable.value.audioIndex == index) commitPreference()
                    else {
                        audioIndex = index
                        load(positionTicks())
                    }
                }
            },
            selectSubtitle = { index ->
                if (canSelectTrack("Subtitle", index)) {
                    stagePreference("Subtitle", index)
                    if (subtitleIndex == index || selectLocalSubtitle(index)) commitPreference()
                    else {
                        subtitleIndex = index
                        load(positionTicks())
                    }
                }
            },
            selectBitrate = {
                if (it > 0 && !mutable.value.loading && mutable.value.error == null && started) {
                    stagePreference { preference -> preference.copy(bitrate = it) }
                    if (bitrate == it) commitPreference()
                    else {
                        bitrate = it
                        load(positionTicks())
                    }
                }
            },
            speed = {
                player.setPlaybackSpeed(it)
                mutable.update { ui -> ui.copy(speed = it) }
            },
            selectVersion = {
                if (sourceId != it) {
                    pendingPreference = null
                    sourceId = it
                    audioIndex = null
                    subtitleIndex = null
                    compatible = fallbackUsed
                    load(positionTicks())
                }
            },
            compatible = { selectCompatibility(true) },
            selectCompatibility = ::selectCompatibility,
            retry = { load(positionTicks()) },
            cancelNext = ::cancelCountdown,
            nextNow = { next?.let(::playEpisode) },
            close = { onClose(false) },
        )

    init {
        subtitleRendering.bind(player)
        player.setAudioAttributes(
            AudioPlaybackCapabilities.attributes,
            true,
        )
        player.setHandleAudioBecomingNoisy(true)
        player.setPlaybackSpeed(restored?.getFloat("speed", 1f) ?: 1f)
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        playbackState == Player.STATE_BUFFERING && seekPreview.state?.active == true
                    )
                        seekPreview.cancel()
                    mutable.update {
                        it.copy(
                            loading = playbackState == Player.STATE_BUFFERING,
                            canSeek =
                                player.isCurrentMediaItemSeekable &&
                                    (playbackState == Player.STATE_READY ||
                                        playbackState == Player.STATE_ENDED),
                        )
                    }
                    if (playbackState == Player.STATE_READY && plan != null) {
                        completion.ready(player.currentMediaItem?.localConfiguration?.tag as? Long)
                        if (player.currentMediaItem?.localConfiguration?.tag == playbackGeneration)
                            commitPreference()
                    }
                    if (playbackState == Player.STATE_READY && plan != null && !started) {
                        started = true
                        mutable.update { it.copy(loading = false, error = null) }
                        report("")
                    }
                    if (
                        playbackState == Player.STATE_ENDED &&
                            player.playbackState == Player.STATE_ENDED &&
                            plan != null &&
                            started
                    ) {
                        when (
                            completion.ended(
                                player.currentMediaItem?.localConfiguration?.tag as? Long,
                                player.currentPosition,
                                maxOf(
                                    player.duration.coerceAtLeast(0),
                                    (item?.ticks ?: 0) / 10_000,
                                ),
                                player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO) ||
                                    player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO),
                            )
                        ) {
                            PlaybackCompletionGate.Result.Completed -> ended()
                            PlaybackCompletionGate.Result.Premature ->
                                showError("播放提前结束，未自动切换下一集。请重试当前视频。")
                            PlaybackCompletionGate.Result.Ignore -> Unit
                        }
                    }
                }

                override fun onRenderedFirstFrame() {
                    mutable.update { it.copy(hasRenderedFrame = true) }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mutable.update { it.copy(playing = isPlaying) }
                    if (started) report("Progress")
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (started && reason == Player.DISCONTINUITY_REASON_SEEK) report("Progress")
                }

                override fun onTracksChanged(tracks: Tracks) {
                    selectTracks(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    handleError(error)
                }
            },
        )
        if (api.session == null) onClose(true)
        else
            load(
                initialTicks,
                fetch = true,
                autoPlay = restored?.getBoolean("playing", true) ?: true,
            )
    }

    private fun positionTicks(): Long =
        if (player.mediaItemCount == 0) initialTicks
        else player.currentPosition.coerceAtLeast(0) * 10_000

    private fun report(event: String) {
        plan?.let { reporter.send(it, event, positionTicks(), !player.playWhenReady) }
    }

    private fun load(
        ticks: Long,
        fetch: Boolean = false,
        autoPlay: Boolean =
            pendingPlay ?: if (player.mediaItemCount > 0) player.playWhenReady else true,
    ) {
        val generation = completion.begin()
        playbackGeneration = generation
        seekPreview.cancel()
        loadJob?.cancel()
        cancelCountdown()
        pendingPlay = autoPlay
        plan?.let { reporter.send(it, "Stopped", positionTicks(), true) }
        plan = null
        started = false
        initialTicks = ticks
        player.stop()
        player.clearMediaItems()
        mutable.update {
            it.copy(
                loading = true,
                hasRenderedFrame = false,
                canSeek = false,
                error = null,
                playing = false,
                positionMs = ticks / 10_000,
                bufferedMs = 0,
                durationMs = if (fetch) 0 else it.durationMs,
            )
        }
        if (fetch) {
            metadataJob?.cancel()
            seasonJob?.cancel()
            next = null
            mutable.update {
                it.copy(
                    episodes = emptyList(),
                    seasons = emptyList(),
                    nextItem = null,
                    episodeError = null,
                )
            }
        }
        loadJob = scope.launch {
            var unclaimed: PlaybackPlan? = null
            try {
                if (fetch || item == null) item = api.item(currentId)
                val media = requireNotNull(item)
                val preferences = api.session?.let { preferenceStore.load(it, media) }
                if (settingsItemId != media.id) {
                    bitrate = preferences?.bitrate ?: 80_000_000
                    compatible = preferences?.compatible ?: false
                    fallbackUsed = compatible
                    settingsItemId = media.id
                }
                mutable.update {
                    it.copy(
                        itemId = media.id,
                        bitrate = bitrate,
                        compatible = compatible,
                        title = media.seriesName.ifBlank { media.name },
                        subtitle =
                            if (media.type == "Episode")
                                "第 ${media.season} 季 · 第 ${media.episode} 集 · ${media.name}"
                            else
                                listOfNotNull(
                                        media.year.takeIf { year -> year > 0 }?.toString(),
                                        media.ticks
                                            .takeIf { time -> time > 0 }
                                            ?.let { time -> "${time / 600_000_000} 分钟" },
                                    )
                                    .joinToString(" · "),
                        versions =
                            media.sources.map { source ->
                                VersionChoice(
                                    source.optString("Id"),
                                    source.optString("Name").ifBlank {
                                        source.optString("Container").uppercase()
                                    },
                                )
                            },
                    )
                }
                if (fetch) loadEpisodeMetadata(media)
                val source =
                    media.sources.firstOrNull { sourceId == null || it.optString("Id") == sourceId }
                val prepared =
                    api.playback(
                        media,
                        DeviceProfile.build(context, source, bitrate),
                        ticks,
                        bitrate,
                        audioIndex,
                        subtitleIndex,
                        sourceId,
                        compatible,
                        preferences,
                    )
                unclaimed = prepared
                currentCoroutineContext().ensureActive()
                check(!released) { "播放器已关闭" }
                item =
                    media.copy(
                        sources =
                            if (media.sources.any { it.optString("Id") == prepared.sourceId }) {
                                media.sources.map {
                                    if (it.optString("Id") == prepared.sourceId) prepared.source
                                    else it
                                }
                            } else media.sources + prepared.source,
                    )
                plan = prepared
                unclaimed = null
                sourceId = prepared.sourceId
                audioIndex = prepared.audioIndex
                subtitleIndex = prepared.subtitleIndex
                val builder =
                    MediaItem.Builder()
                        .setUri(prepared.url)
                        .setMediaId(media.id)
                        .setTag(generation)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(media.label).build())
                if (prepared.method == "Transcode" || prepared.url.contains(".m3u8"))
                    builder.setMimeType("application/x-mpegURL")
                builder.setSubtitleConfigurations(
                    prepared.subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                            .setMimeType(subtitle.mime)
                            .setLanguage(subtitle.language)
                            .setLabel(subtitle.label)
                            .setSelectionFlags(
                                if (subtitle.index == prepared.subtitleIndex)
                                    C.SELECTION_FLAG_DEFAULT
                                else 0,
                            )
                            .setId(SubtitleTrackId.external(subtitle.index))
                            .build()
                    },
                )
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .clearOverrides()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleIndex == -1)
                        .build()
                mutable.update {
                    it.copy(
                        method = prepared.method,
                        audio = prepared.trackChoices("Audio"),
                        subtitles = prepared.trackChoices("Subtitle"),
                        audioIndex =
                            audioIndex
                                ?: prepared.source.optInt("DefaultAudioStreamIndex", -1).takeIf {
                                    index ->
                                    index >= 0
                                },
                        subtitleIndex = prepared.subtitleIndex,
                        bitrate = bitrate,
                        compatible = compatible,
                        sourceId = sourceId,
                    )
                }
                player.setMediaItem(builder.build(), ticks / 10_000)
                player.prepare()
                wasPlaying = autoPlay
                player.playWhenReady = autoPlay && isStarted()
                pendingPlay = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onClose(true)
                else showError(e.message ?: "获取播放信息失败")
            } finally {
                unclaimed?.let { reporter.send(it, "Stopped", ticks, true) }
            }
        }
    }

    /** Metadata browsing has its own job: changing an audio track cannot cancel season loading. */
    private fun loadEpisodeMetadata(media: Media) {
        if (media.type != "Episode" || media.seriesId.isBlank()) return
        metadataJob = scope.launch {
            try {
                val seasons = api.seasons(media.seriesId)
                val episodes =
                    api.episodes(media.seriesId, media.seasonId.takeIf { it.isNotBlank() })
                val index = episodes.indexOfFirst { it.id == media.id }
                next =
                    episodes.getOrNull(index + 1).takeIf { index >= 0 }
                        ?: try {
                            api.nextEpisode(media)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e is ApiException && e.status == 401) throw e else null
                        }
                mutable.update {
                    it.copy(
                        nextItem = next,
                        seasons = seasons,
                        episodes = episodes,
                        selectedSeasonId = media.seasonId.takeIf { id -> id.isNotBlank() },
                        episodeError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onClose(true)
                else mutable.update { it.copy(episodeError = "读取剧集信息失败，请重新选择季重试") }
            }
        }
    }

    private fun selectSeason(id: String) {
        val media = item ?: return
        if (media.seriesId.isBlank()) return
        metadataJob?.cancel()
        seasonJob?.cancel()
        mutable.update {
            it.copy(selectedSeasonId = id, episodes = emptyList(), episodeError = null)
        }
        seasonJob = scope.launch {
            try {
                val episodes = api.episodes(media.seriesId, id)
                mutable.update { it.copy(episodes = episodes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onClose(true)
                else mutable.update { it.copy(episodeError = e.message ?: "读取剧集失败，请重试") }
            }
        }
    }

    private fun playEpisode(media: Media) {
        pendingPreference = null
        currentId = media.id
        item = media
        sourceId = null
        audioIndex = null
        subtitleIndex = null
        compatible = false
        fallbackUsed = false
        settingsItemId = null
        load(0, fetch = true, autoPlay = true)
    }

    private fun canSelectTrack(type: String, index: Int): Boolean {
        if (mutable.value.loading || mutable.value.error != null || !started) return false
        val current = plan ?: return false
        return (type == "Subtitle" && index == -1) ||
            current.streams.any {
                it.optString("Type") == type && it.optInt("Index", -1) == index
            }
    }

    private fun stagePreference(type: String, index: Int) {
        val current = plan ?: return
        stagePreference { it.selected(type, index, current.streams) }
    }

    private fun stagePreference(update: (PlaybackTrackPreferences) -> PlaybackTrackPreferences) {
        val media = item ?: return
        val session = api.session ?: return
        val previous =
            pendingPreference?.takeIf { it.first == media.id }?.second
                ?: preferenceStore.load(session, media)
        pendingPreference = media.id to update(previous)
    }

    private fun selectCompatibility(value: Boolean) {
        if (mutable.value.loading || item == null) return
        stagePreference { it.copy(compatible = value) }
        if (compatible == value && started && mutable.value.error == null) commitPreference()
        else {
            compatible = value
            fallbackUsed = value
            load(positionTicks())
        }
    }

    private fun commitPreference() {
        val pending = pendingPreference ?: return
        val media = item ?: return
        val session = api.session ?: return
        if (pending.first == media.id) preferenceStore.save(session, media, pending.second)
        pendingPreference = null
    }

    private fun toggle() {
        if (player.playbackState == Player.STATE_ENDED) load(0, autoPlay = true)
        else if (player.isPlaying) player.pause() else player.play()
    }

    private fun seek(target: Long) {
        if (!mutable.value.canSeek || mutable.value.loading || mutable.value.error != null) return
        val safe = target.coerceIn(0, mutable.value.durationMs.coerceAtLeast(0))
        cancelCountdown()
        player.seekTo(safe)
        mutable.update { it.copy(positionMs = safe) }
    }

    /** Local text selection changes neither the video source nor the playback session. */
    private fun selectLocalSubtitle(index: Int): Boolean {
        val current = plan ?: return false
        if (
            !started ||
                mutable.value.loading ||
                mutable.value.error != null ||
                dev.joyfin.data.SubtitleDelivery.requiresBurnIn(
                    current.subtitleIndex,
                    current.streams,
                )
        )
            return false
        val selected =
            if (index < 0) null
            else {
                if (current.subtitles.none { it.index == index }) return false
                player.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .firstNotNullOfOrNull { group ->
                        (0 until group.length)
                            .firstOrNull {
                                SubtitleTrackId.matches(group.getTrackFormat(it).id, index) &&
                                    group.isTrackSupported(it)
                            }
                            ?.let { group to it }
                    } ?: return false
            }
        cancelCountdown()
        val parameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, index < 0)
        selected?.let { (group, track) ->
            parameters.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(track)),
            )
        }
        subtitleIndex = index
        plan = current.copy(subtitleIndex = index)
        mutable.update { it.copy(subtitleIndex = index) }
        player.trackSelectionParameters = parameters.build()
        report("Progress")
        return true
    }

    private fun selectTracks(tracks: Tracks) {
        val current = plan ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        var changed = false
        if (current.method == "DirectPlay" && audioIndex != null) {
            val ordinal =
                current.streams
                    .filter { it.optString("Type") == "Audio" }
                    .indexOfFirst { it.optInt("Index") == audioIndex }
            val audioTracks =
                tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { group -> (0 until group.length).map { group to it } }
            audioTracks.getOrNull(ordinal)?.let { (group, index) ->
                if (group.isTrackSupported(index) && !group.isTrackSelected(index)) {
                    builder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(index)),
                    )
                    changed = true
                }
            }
        }
        if (current.subtitles.isNotEmpty()) {
            tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .forEach { group ->
                    val index =
                        (0 until group.length).firstOrNull {
                            SubtitleTrackId.matches(
                                group.getTrackFormat(it).id,
                                current.subtitleIndex,
                            )
                        }
                    if (
                        index != null &&
                            group.isTrackSupported(index) &&
                            !group.isTrackSelected(index)
                    ) {
                        builder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, listOf(index)),
                        )
                        changed = true
                    }
                }
        }
        if (changed) player.trackSelectionParameters = builder.build()
    }

    private fun handleError(error: PlaybackException) {
        if (
            generateSequence<Throwable>(error) { it.cause }
                .any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == 401 }
        ) {
            onClose(true)
            return
        }
        val decode =
            error.errorCode in
                setOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                )
        if (decode && !fallbackUsed) {
            fallbackUsed = true
            compatible = true
            stagePreference { it.copy(compatible = true) }
            load(positionTicks())
        } else
            showError(
                if (decode) "当前格式无法播放，兼容转码也未成功。请检查服务端转码日志。"
                else "播放中断：${error.errorCodeName}。请检查网络或服务器。",
            )
    }

    private fun stopCurrentStream() {
        initialTicks = positionTicks()
        plan?.let { reporter.send(it, "Stopped", initialTicks, true) }
        plan = null
        started = false
        player.stop()
        player.clearMediaItems()
    }

    private fun showError(message: String) {
        seekPreview.cancel()
        stopCurrentStream()
        mutable.update { it.copy(loading = false, playing = false, canSeek = false, error = message) }
    }

    private fun ended() {
        seekPreview.cancel()
        stopCurrentStream()
        mutable.update { it.copy(playing = false, canSeek = false) }
        val upcoming = next
        if (upcoming == null || !store.autoNext) {
            onClose(false)
            return
        }
        countdown = scope.launch {
            for (seconds in 8 downTo 1) {
                mutable.update { it.copy(nextSeconds = seconds, nextItem = upcoming) }
                delay(1000)
            }
            playEpisode(upcoming)
        }
    }

    fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        mutable.update { it.copy(nextSeconds = 0) }
    }

    fun onStart() {
        if (released) return
        if (suspendedPlayback) {
            suspendedPlayback = false
            if (mutable.value.error == null) load(initialTicks, fetch = true, autoPlay = wasPlaying)
        } else if (wasPlaying && mutable.value.error == null) player.play()
        pollJob?.cancel()
        reportJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                mutable.update {
                    it.copy(
                        playing = player.isPlaying,
                        canSeek =
                            player.isCurrentMediaItemSeekable &&
                                (player.playbackState == Player.STATE_READY ||
                                    player.playbackState == Player.STATE_ENDED),
                        positionMs = if (player.mediaItemCount > 0) player.currentPosition.coerceAtLeast(0) else initialTicks / 10_000,
                        durationMs =
                            player.duration.takeIf { duration -> duration > 0 } ?: it.durationMs,
                        bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                        speed = player.playbackParameters.speed,
                    )
                }
                delay(300)
            }
        }
        reportJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (started) report("Progress")
            }
        }
    }

    fun onStop() {
        seekPreview.cancel()
        cancelCountdown()
        pollJob?.cancel()
        reportJob?.cancel()
        wasPlaying = pendingPlay ?: player.playWhenReady
        suspendedPlayback = true
        loadJob?.cancel()
        metadataJob?.cancel()
        seasonJob?.cancel()
        stopCurrentStream()
    }

    fun handleMediaKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY ->
                if (player.playbackState == Player.STATE_ENDED) load(0, autoPlay = true)
                else player.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekPreview.adjust(10_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekPreview.adjust(-10_000)
            else -> return false
        }
        return true
    }

    fun save(out: Bundle) {
        out.putString("itemId", currentId)
        out.putLong("position", positionTicks())
        out.putString("source", sourceId)
        out.putBoolean("playing", if (suspendedPlayback) wasPlaying else pendingPlay ?: player.playWhenReady)
        out.putBoolean("compatible", compatible)
        out.putBoolean("fallbackUsed", fallbackUsed)
        out.putFloat("speed", player.playbackParameters.speed)
        out.putInt("audio", audioIndex ?: -1)
        subtitleIndex?.let { out.putInt("subtitle", it) }
        out.putInt("bitrate", bitrate)
    }

    fun release() {
        seekPreview.cancel()
        if (released) return
        released = true
        loadJob?.cancel()
        metadataJob?.cancel()
        seasonJob?.cancel()
        countdown?.cancel()
        pollJob?.cancel()
        reportJob?.cancel()
        stopCurrentStream()
        mediaSession.release()
        player.release()
        subtitleRendering.release()
        reporter.close()
    }
}
