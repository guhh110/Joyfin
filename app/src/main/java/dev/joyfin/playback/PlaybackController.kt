@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.joyfin.BuildConfig
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import dev.joyfin.subtitles.PlayerSubtitleSearchController
import dev.joyfin.subtitles.PlayerSubtitleTarget
import dev.joyfin.playback.quality.PlaybackEnvironment
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
    private var diagnosticMediaId = currentId
    private var diagnosticToken = PlaybackDiagnostics.claim(currentId)
    private var initialTicks = restored?.getLong("position") ?: startTicks
    private var sourceId = restored?.getString("source") ?: initialSourceId
    private var bitrate = restored?.getInt("bitrate", PlaybackQuality.DEFAULT) ?: PlaybackQuality.DEFAULT
    private var audioIndex = restored?.getInt("audio", -1)?.takeIf { it >= 0 }
    private var subtitleIndex: Int? =
        restored?.takeIf { it.containsKey("subtitle") }?.getInt("subtitle")
    private var compatible = restored?.getBoolean("compatible") ?: false
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

    private val localStore = dev.joyfin.data.subtitles.LocalSubtitleStore.get(context)
    private val localPlayback = LocalSubtitlePlayback(localStore)
    private var localSubtitle: PreparedLocalSubtitle? = null
    private var localSubtitleKey: String? = null
    private var subtitleJob: Job? = null
    private var subtitleSearchResume: Boolean? = null
    private var searchSubtitleApplying = false

    private val completion = PlaybackCompletionGate()
    private val environment = PlaybackEnvironment(context, scope, ::environmentChanged) { notice ->
        mutable.update { it.copy(playbackNotice = notice) }
    }
    private val subtitleRendering = SubtitleRendering()
    private val http =
        DefaultHttpDataSource.Factory()
            .setUserAgent("Joyfin/${BuildConfig.VERSION_NAME}")
            .setDefaultRequestProperties(mapOf("Authorization" to api.authorization()))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
    val player: ExoPlayer =
        subtitleRendering
            .configure(ExoPlayer.Builder(context), DefaultDataSource.Factory(context, http), environment.audioEngine.renderers(context))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    private val mediaSession = MediaSession.Builder(context, player).build()
    private val diagnosticObserver = PlaybackDiagnosticsObserver(player, { diagnosticToken },
        { !released && plan != null && player.currentMediaItem?.localConfiguration?.tag == playbackGeneration },
        environment::videoInput)

    val subtitleSearch = PlayerSubtitleSearchController(context, scope, api, localStore,
        ::applySearchedSubtitle, ::subtitleSearchClosed)

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
                    if (localSubtitle != null) {
                        // Explicit server/off choice clears only this media version's local selection.
                        subtitleJob?.cancel()
                        val key = localSubtitleKey
                        subtitleJob = scope.launch {
                            try {
                                key?.let { withContext(Dispatchers.IO) { localStore.select(it, null) } }
                                localSubtitle = null
                                mutable.update { it.copy(localSubtitle = null, localSubtitleError = null) }
                                if (selectLocalSubtitle(index)) commitPreference()
                                else { subtitleIndex = index; load(positionTicks()) }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { mutable.update { it.copy(localSubtitleError = "无法清除本地字幕选择，请重试") } }
                        }
                    } else if (subtitleIndex == index || selectLocalSubtitle(index)) commitPreference()
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
                environment.setSpeed(it)
                mutable.update { ui -> ui.copy(speed = it) }
            },
            selectVersion = {
                if (sourceId != it) {
                    pendingPreference = null
                    sourceId = it
                    audioIndex = null
                    subtitleIndex = null
                    compatible = item?.let { media ->
                        api.session?.let { preferenceStore.load(it, media).compatible }
                    } ?: false
                    load(positionTicks())
                }
            },
            compatible = { selectCompatibility(true) },
            selectCompatibility = ::selectCompatibility,
            retry = { load(positionTicks()) },
            cancelNext = ::cancelCountdown,
            nextNow = ::playNextEpisode,
            close = { onClose(false) },
            subtitleOffset = ::setSubtitleOffset,
            selectLocalFile = ::selectManagedSubtitle,
            searchSubtitles = ::openSubtitleSearch,
            setAudioOutputMode = environment::setAudioMode,
        )

    init {
        player.addAnalyticsListener(diagnosticObserver)
        subtitleRendering.bind(player)
        player.setAudioAttributes(
            AudioPlaybackCapabilities.attributes,
            true,
        )
        player.setHandleAudioBecomingNoisy(true)
        player.setPlaybackSpeed(restored?.getFloat("speed", 1f) ?: 1f)
        environment.setSpeed(player.playbackParameters.speed)
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING && plan != null && !searchSubtitleApplying &&
                        player.currentMediaItem?.localConfiguration?.tag == playbackGeneration)
                        PlaybackDiagnostics.event(diagnosticToken, DiagnosticStage.STREAM, DiagnosticOutcome.RUNNING)
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
                    if (playbackState == Player.STATE_READY && plan != null && !searchSubtitleApplying) {
                        if (player.currentMediaItem?.localConfiguration?.tag == playbackGeneration)
                            PlaybackDiagnostics.event(diagnosticToken, DiagnosticStage.STREAM, DiagnosticOutcome.READY)
                        completion.ready(player.currentMediaItem?.localConfiguration?.tag as? Long)
                        if (player.currentMediaItem?.localConfiguration?.tag == playbackGeneration)
                            commitPreference()
                    }
                    if (playbackState == Player.STATE_READY && plan != null && !started && !searchSubtitleApplying) {
                        started = true
                        mutable.update { it.copy(loading = false, error = null) }
                        report("")
                    }
                    if (
                        playbackState == Player.STATE_ENDED &&
                            player.playbackState == Player.STATE_ENDED &&
                            plan != null &&
                            !searchSubtitleApplying &&
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
                    diagnosticObserver.sample()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mutable.update { it.copy(playing = isPlaying) }
                    if (plan != null) diagnosticObserver.sample()
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
                    if (plan != null) diagnosticObserver.sample()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!searchSubtitleApplying && plan != null &&
                        player.currentMediaItem?.localConfiguration?.tag == playbackGeneration) handleError(error)
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
            subtitleSearchResume ?: pendingPlay ?: if (player.mediaItemCount > 0) player.playWhenReady else true,
    ) {
        discardSubtitleSearch()
        if (diagnosticMediaId != currentId) {
            diagnosticMediaId = currentId
            diagnosticToken = PlaybackDiagnostics.begin(currentId)
        }
        val diagnostics = diagnosticToken
        PlaybackDiagnostics.preparing(diagnostics, bitrate, compatible, environment.forceAudioTranscode, environment.isFallback)
        val generation = completion.begin()
        playbackGeneration = generation
        environment.begin(generation, diagnostics)
        subtitleJob?.cancel()
        localSubtitle = null
        localSubtitleKey = null
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
                localSubtitle = null,
                localSubtitles = emptyList(),
                localSubtitleError = null,
                playbackNotice = null,
                subtitleApplying = false,
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
            var diagnosticStage = DiagnosticStage.METADATA
            try {
                if (fetch || item == null) {
                    PlaybackDiagnostics.event(diagnostics, DiagnosticStage.METADATA, DiagnosticOutcome.RUNNING)
                    val fetched = api.item(currentId)
                    currentCoroutineContext().ensureActive()
                    if (generation != playbackGeneration || released) return@launch
                    item = fetched
                    PlaybackDiagnostics.event(diagnostics, DiagnosticStage.METADATA, DiagnosticOutcome.READY)
                }
                val media = requireNotNull(item)
                val preferences = api.session?.let { preferenceStore.load(it, media) }
                if (settingsItemId != media.id) {
                    bitrate = preferences?.bitrate ?: PlaybackQuality.DEFAULT
                    compatible = preferences?.compatible ?: false
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
                                    source.id,
                                    source.name.ifBlank {
                                        source.container.uppercase()
                                    },
                                )
                            },
                    )
                }
                if (fetch) loadEpisodeMetadata(media)
                val source =
                    media.sources.firstOrNull { sourceId == null || it.id == sourceId }
                PlaybackDiagnostics.preparing(diagnostics, bitrate, compatible, environment.forceAudioTranscode, environment.isFallback)
                source?.let { PlaybackDiagnostics.source(diagnostics, it, audioIndex ?: it.defaultAudioIndex) }
                val localKey = api.session?.let { session -> source?.id?.let { version ->
                    dev.joyfin.data.subtitles.LocalSubtitleStore.key(session.server, session.userId, media.id, version)
                } }
                var localError: String? = null
                val localOptions = try {
                    localKey?.let { key -> withContext(Dispatchers.IO) { localStore.files(key) } }.orEmpty()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
                val selectedLocal = try {
                    localKey?.let { localPlayback.selected(it) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    PlaybackDiagnostics.failure(diagnostics, DiagnosticStage.SUBTITLE, e, fatal = false)
                    localError = "本地字幕无法读取，请在详情页重新选择"; null
                }
                var negotiation = environment.negotiate(media.id, source, audioIndex, bitrate, compatible)
                var profile = negotiation.profile
                diagnosticStage = DiagnosticStage.NEGOTIATION
                PlaybackDiagnostics.event(diagnostics, diagnosticStage, DiagnosticOutcome.RUNNING)
                val prepared =
                    api.playback(
                        media,
                        profile,
                        ticks,
                        bitrate,
                        audioIndex,
                        if (selectedLocal != null) -1 else subtitleIndex,
                        sourceId,
                        compatible,
                        preferences,
                        sourcePolicy = { discovered, selectedAudio ->
                            negotiation = environment.negotiate(media.id, discovered, selectedAudio, bitrate, compatible)
                            profile = negotiation.profile
                            negotiation.requestPolicy
                        },
                    )
                unclaimed = prepared
                currentCoroutineContext().ensureActive()
                check(!released && generation == playbackGeneration) { "播放器已关闭或播放内容已变化" }
                if (!environment.isCurrent(negotiation)) {
                    load(ticks, autoPlay = autoPlay)
                    return@launch
                }
                environment.activate(negotiation, prepared.transcodeReasons)
                item =
                    media.copy(
                        sources =
                            if (media.sources.any { it.id == prepared.sourceId }) {
                                media.sources.map {
                                    if (it.id == prepared.sourceId) prepared.source
                                    else it
                                }
                            } else media.sources + prepared.source,
                    )
                plan = prepared
                PlaybackDiagnostics.plan(diagnostics, prepared)
                diagnosticStage = DiagnosticStage.STREAM
                PlaybackDiagnostics.event(diagnostics, diagnosticStage, DiagnosticOutcome.RUNNING)
                // Never log playback URLs: they may contain server addresses and credentials.
                android.util.Log.i(
                    "JoyfinPlayback",
                    "method=${prepared.method}, compatible=$compatible, audioFallback=${environment.forceAudioTranscode}, " +
                        "video=${prepared.source.streams.firstOrNull { it.type == "Video" }?.let { "${it.codec}/${it.profile}/${it.bitDepth}bit/${it.width}x${it.height}" }}, " +
                        "subtitle=${prepared.source.streams.firstOrNull { it.type == "Subtitle" && it.index == prepared.subtitleIndex }?.codec}, " +
                        "audioCodecs=${profile.directPlayProfiles.flatMap { it.audioCodec.split(',') }.distinct().joinToString(",")}, " +
                        "reasons=${prepared.transcodeReasons.joinToString()}",
                )
                unclaimed = null
                sourceId = prepared.sourceId
                audioIndex = prepared.audioIndex ?: prepared.source.defaultAudioIndex.takeIf { it >= 0 }
                subtitleIndex = prepared.subtitleIndex
                localSubtitle = selectedLocal?.takeIf { prepared.sourceId == source?.id }
                localSubtitleKey = localKey?.takeIf { prepared.sourceId == source?.id }
                val builder =
                    MediaItem.Builder()
                        .setUri(prepared.url)
                        .setMediaId(media.id)
                        .setTag(generation)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(media.label).build())
                builder.setMimeType(prepared.mimeType)
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
                    } + listOfNotNull(localSubtitle?.configuration),
                )
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .clearOverrides()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleIndex == -1 && localSubtitle == null)
                        .build()
                mutable.update {
                    it.copy(
                        method = prepared.method,
                        audio = prepared.trackChoices("Audio"),
                        subtitles = prepared.trackChoices("Subtitle"),
                        audioIndex =
                            audioIndex
                                ?: prepared.source.defaultAudioIndex.takeIf {
                                    index ->
                                    index >= 0
                                },
                        subtitleIndex = prepared.subtitleIndex,
                        localSubtitle = localSubtitle?.let { local -> LocalSubtitleSelection(local.file.name, local.offsetMs, local.file.id) },
                        localSubtitles = if (prepared.sourceId == source?.id) localOptions.map { LocalSubtitleOption(it.id, it.name) } else emptyList(),
                        localSubtitleError = localError,
                        bitrate = bitrate,
                        compatible = compatible,
                        audioOutputMode = environment.audioMode,
                        playbackNotice = environment.notice,
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
                currentCoroutineContext().ensureActive()
                if (generation != playbackGeneration || released) return@launch
                PlaybackDiagnostics.failure(diagnostics, diagnosticStage, e)
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

    private fun playNextEpisode() {
        val ui = mutable.value
        if (released || ui.loading || ui.error != null || ui.subtitleApplying || ui.subtitleSearchOpen) return
        val upcoming = next?.takeIf { it.id != currentId } ?: return
        playEpisode(upcoming)
    }

    private fun playEpisode(media: Media) {
        pendingPreference = null
        currentId = media.id
        item = media
        sourceId = null
        audioIndex = null
        subtitleIndex = null
        compatible = false
        settingsItemId = null
        load(0, fetch = true, autoPlay = true)
    }

    private fun canSelectTrack(type: String, index: Int): Boolean {
        if (mutable.value.loading || mutable.value.error != null || !started) return false
        val current = plan ?: return false
        return (type == "Subtitle" && index == -1) ||
            current.streams.any {
                it.type == type && it.index == index
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
        if (compatible == value && !environment.isFallback && started && mutable.value.error == null) {
            PlaybackDiagnostics.selection(diagnosticToken, compatible, environment.forceAudioTranscode, environment.isFallback)
            commitPreference()
        }
        else {
            compatible = value
            environment.resetAttempts()
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

    private fun subtitleTrack(tracks: Tracks, current: PlaybackPlan, index: Int): Pair<Tracks.Group, Int>? {
        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).map { group to it } }
        text.firstOrNull { (group, track) ->
            SubtitleTrackId.matches(group.getTrackFormat(track).id, index) && group.isTrackSupported(track)
        }?.let { return it }
        val target = current.streams.firstOrNull { it.type == "Subtitle" && it.index == index } ?: return null
        if (current.method != "DirectPlay" || !SubtitleDelivery.embedded(target, current.source.container)) return null
        val embedded = text.filterNot { (group, track) ->
            SubtitleTrackId.isExternal(group.getTrackFormat(track).id) || LocalSubtitlePlayback.matches(group.getTrackFormat(track).id)
        }
        val ordinal = EmbeddedSubtitleMatcher.find(index, current.streams, embedded.map { (group, track) ->
            val format = group.getTrackFormat(track)
            EmbeddedSubtitleMatcher.Track(
                if (format.sampleMimeType == "application/x-media3-cues") format.codecs else format.sampleMimeType,
                format.language, format.label)
        }) ?: return null
        return embedded.getOrNull(ordinal)?.takeIf { (group, track) -> group.isTrackSupported(track) }
    }

    /** Local subtitle selection changes neither the video source nor the playback session. */
    private fun selectLocalSubtitle(index: Int): Boolean {
        val current = plan ?: return false
        if (
            !started ||
                mutable.value.loading ||
                mutable.value.error != null ||
                dev.joyfin.data.SubtitleDelivery.requiresBurnIn(
                    current.subtitleIndex,
                    current.streams,
                    current.source.container,
                )
        )
            return false
        val selected =
            if (index < 0) null
            else {
                subtitleTrack(player.currentTracks, current, index) ?: return false
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
            val audioTracks =
                tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { group -> (0 until group.length).map { group to it } }
            val ordinal = AudioTrackMatcher.find(requireNotNull(audioIndex), current.streams,
                audioTracks.map { (group, index) ->
                    val format = group.getTrackFormat(index)
                    AudioTrackMatcher.Track(format.sampleMimeType, format.language, format.label, format.channelCount)
                })
            ordinal?.let { audioTracks.getOrNull(it) }?.let { (group, index) ->
                if (group.isTrackSupported(index) && !group.isTrackSelected(index)) {
                    builder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(index)),
                    )
                    changed = true
                }
            }
        }
        if (localSubtitle != null) {
            tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                (0 until group.length).firstOrNull { LocalSubtitlePlayback.matches(group.getTrackFormat(it).id) && group.isTrackSupported(it) }?.let { index ->
                    if (!group.isTrackSelected(index)) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                        changed = true
                    }
                }
            }
        } else if (current.subtitleIndex >= 0) subtitleTrack(tracks, current, current.subtitleIndex)?.let { (group, index) ->
            if (!group.isTrackSelected(index)) {
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                changed = true
            }
        }
        if (changed) player.trackSelectionParameters = builder.build()
    }

    private fun openSubtitleSearch() {
        val current = plan ?: return
        val session = api.session ?: return
        if (released || !started || mutable.value.loading || mutable.value.error != null ||
            mutable.value.subtitleApplying || mutable.value.subtitleSearchOpen) return
        seekPreview.cancel()
        cancelCountdown()
        subtitleSearchResume = player.playWhenReady
        player.pause()
        mutable.update { it.copy(subtitleSearchOpen = true) }
        subtitleSearch.open(PlayerSubtitleTarget(session, item ?: current.item, current.source, playbackGeneration))
    }

    private fun subtitleSearchClosed(applied: Boolean) {
        val resume = subtitleSearchResume ?: false
        subtitleSearchResume = null
        mutable.update { it.copy(subtitleSearchOpen = false,
            subtitleSearchApplied = it.subtitleSearchApplied + if (applied) 1 else 0) }
        if (!released && isStarted() && mutable.value.error == null) player.playWhenReady = resume
    }

    private fun discardSubtitleSearch() {
        subtitleSearchResume = null
        subtitleSearch.discard()
        searchSubtitleApplying = false
        mutable.update { it.copy(subtitleSearchOpen = false, subtitleApplying = false) }
    }

    private fun matchesSubtitleTarget(target: PlayerSubtitleTarget): Boolean =
        !released && !suspendedPlayback && target.session == api.session &&
            target.generation == playbackGeneration && target.media.id == currentId &&
            target.source.id == plan?.sourceId

    private suspend fun applySearchedSubtitle(target: PlayerSubtitleTarget, pack: ManagedSubtitle, fileId: String) {
        check(matchesSubtitleTarget(target))
        val key = LocalSubtitleStore.key(target.session.server, target.session.userId, target.media.id, target.source.id)
        val (file, offset) = withContext(Dispatchers.IO) {
            val owner = LocalSubtitleStore.owner(target.session.server, target.session.userId)
            require(pack.owner == owner)
            val file = localStore.files(owner).firstOrNull { it.id == fileId } ?: error("下载文件已失效")
            file to (localStore.choice(key)?.takeIf { it.first.id == fileId }?.second ?: 0L)
        }
        val prepared = localPlayback.file(file).let { if (offset == 0L) it else localPlayback.shifted(it, offset) }
        currentCoroutineContext().ensureActive()
        check(matchesSubtitleTarget(target))
        val oldItem = player.currentMediaItem ?: error("播放已结束")
        val oldPlan = requireNotNull(plan)
        val oldNegotiation = environment.currentNegotiation
        val oldLocal = localSubtitle
        val oldParameters = player.trackSelectionParameters
        val position = player.currentPosition.coerceAtLeast(0)
        val speed = player.playbackParameters.speed
        var replacement: PlaybackPlan? = null
        var committed = false
        var replaced = false
        mutable.update { it.copy(subtitleApplying = true) }
        try {
            // A burned-in subtitle cannot be removed by switching text tracks in the same stream.
            val burnedIn = oldLocal == null && oldPlan.method == "Transcode" &&
                SubtitleDelivery.requiresBurnIn(oldPlan.subtitleIndex, oldPlan.streams, oldPlan.source.container)
            val playbackItem = if (burnedIn) {
                var negotiation = environment.negotiate(target.media.id, target.source, audioIndex, bitrate, compatible)
                val nextPlan = api.playback(target.media, negotiation.profile, position * 10_000, bitrate,
                    audioIndex, -1, target.source.id, compatible, sourcePolicy = { source, selectedAudio ->
                        negotiation = environment.negotiate(target.media.id, source, selectedAudio, bitrate, compatible)
                        negotiation.requestPolicy
                    })
                replacement = nextPlan
                currentCoroutineContext().ensureActive()
                check(matchesSubtitleTarget(target) && nextPlan.sourceId == target.source.id && environment.isCurrent(negotiation))
                environment.activate(negotiation, nextPlan.transcodeReasons)
                val builder = oldItem.buildUpon().setUri(nextPlan.url)
                    .setMimeType(nextPlan.mimeType)
                builder.setSubtitleConfigurations(nextPlan.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mime).setLanguage(subtitle.language).setLabel(subtitle.label)
                        .setId(SubtitleTrackId.external(subtitle.index)).build()
                } + prepared.configuration).build()
            } else oldItem.buildUpon().setSubtitleConfigurations(
                oldItem.localConfiguration?.subtitleConfigurations.orEmpty()
                    .filterNot { LocalSubtitlePlayback.matches(it.id) } + prepared.configuration).build()
            searchSubtitleApplying = true
            localSubtitle = prepared
            replacement?.let { plan = it }
            player.trackSelectionParameters = oldParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            replaced = true
            player.prepareLocalSubtitle(playbackItem, position, speed)
            currentCoroutineContext().ensureActive()
            check(matchesSubtitleTarget(target) && subtitleSearch.state.value.open && !subtitleSearch.state.value.closing)
            val mapping = SubtitleMapping(target.media.id, target.media.label, target.source.id,
                target.source.name.ifBlank { target.source.container.uppercase() }, target.source.path, target.media.episode)
            val mapped = pack.copy(files = pack.files.map { row ->
                if (row.fileId == fileId) row.copy(mapping = mapping, manualMapping = true) else row
            })
            val options = withContext(Dispatchers.IO) { localStore.files(key) }
            currentCoroutineContext().ensureActive()
            check(matchesSubtitleTarget(target) && !subtitleSearch.state.value.closing)
            // One synchronous atomic index commit: a close/switch cannot interleave a late binding.
            localStore.bindPackage(mapped, target.session.server, target.session.userId, selectFileId = fileId)
            committed = true
            localSubtitleKey = key
            mutable.update { it.copy(subtitleApplying = false, localSubtitleError = null,
                localSubtitle = LocalSubtitleSelection(file.name, prepared.offsetMs, file.id),
                localSubtitles = (options.map { option -> LocalSubtitleOption(option.id, option.name) } +
                    LocalSubtitleOption(file.id, file.name)).distinctBy { it.id },
                method = plan?.method ?: it.method,
                subtitleIndex = plan?.subtitleIndex ?: it.subtitleIndex) }
            replacement?.let {
                PlaybackDiagnostics.plan(diagnosticToken, it)
                reporter.send(oldPlan, "Stopped", position * 10_000, true)
                subtitleIndex = it.subtitleIndex
                report("")
            }
        } finally {
            searchSubtitleApplying = false
            if (!committed) {
                replacement?.let { reporter.send(it, "Stopped", position * 10_000, true) }
                if (matchesSubtitleTarget(target)) {
                    plan = oldPlan
                    oldNegotiation?.let { environment.activate(it, oldPlan.transcodeReasons) }
                    PlaybackDiagnostics.plan(diagnosticToken, oldPlan)
                    localSubtitle = oldLocal
                    if (replaced) {
                        val playing = player.playWhenReady
                        player.trackSelectionParameters = oldParameters
                        player.setMediaItem(oldItem, position)
                        player.prepare()
                        player.setPlaybackSpeed(speed)
                        player.playWhenReady = playing
                    }
                    mutable.update { it.copy(subtitleApplying = false) }
                } else if (replacement != null) {
                    reporter.send(oldPlan, "Stopped", position * 10_000, true)
                }
            }
        }
    }

    private fun selectManagedSubtitle(id: String) {
        val key = localSubtitleKey ?: return
        if (mutable.value.loading || mutable.value.subtitleApplying || mutable.value.localSubtitles.none { it.id == id }) return
        val generation = playbackGeneration
        val ticks = positionTicks()
        val playing = player.playWhenReady
        mutable.update { it.copy(subtitleApplying = true) }
        subtitleJob?.cancel()
        subtitleJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { localStore.select(key, id) }
                ensureActive()
                if (generation == playbackGeneration) load(ticks, autoPlay = playing)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(subtitleApplying = false, localSubtitleError = "切换本地字幕失败") } }
        }
    }

    private fun setSubtitleOffset(offset: Long) {
        val selected = localSubtitle ?: return
        val key = localSubtitleKey ?: return
        val mediaItem = player.currentMediaItem ?: return
        if (mutable.value.loading || mutable.value.subtitleApplying || offset !in -60_000..60_000) return
        val generation = playbackGeneration
        mutable.update { it.copy(subtitleApplying = true, localSubtitleError = null) }
        subtitleJob = scope.launch {
            try {
                val shifted = localPlayback.shifted(selected, offset)
                ensureActive()
                if (generation != playbackGeneration) return@launch
                withContext(Dispatchers.IO) { localStore.offset(key, offset) }
                ensureActive()
                if (generation != playbackGeneration) return@launch
                val position = player.currentPosition.coerceAtLeast(0)
                val playing = player.playWhenReady
                val speed = player.playbackParameters.speed
                val configurations = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
                    .filterNot { LocalSubtitlePlayback.matches(it.id) } + shifted.configuration
                localSubtitle = shifted
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                player.setMediaItem(mediaItem.buildUpon().setSubtitleConfigurations(configurations).build(), position)
                player.prepare()
                player.setPlaybackSpeed(speed)
                player.playWhenReady = playing
                mutable.update { it.copy(localSubtitle = LocalSubtitleSelection(shifted.file.name, shifted.offsetMs, shifted.file.id), subtitleApplying = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(subtitleApplying = false, localSubtitleError = "字幕偏移调整失败，请重试") } }
        }
    }

    private fun handleError(error: PlaybackException) {
        if (
            generateSequence<Throwable>(error) { it.cause }
                .any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == 401 }
        ) {
            PlaybackDiagnostics.failure(diagnosticToken, DiagnosticStage.STREAM, error)
            onClose(true)
            return
        }
        if (environment.fallback(error)) {
            PlaybackDiagnostics.failure(diagnosticToken, DiagnosticStage.STREAM, error, fatal = false)
            PlaybackDiagnostics.event(diagnosticToken, DiagnosticStage.STREAM, DiagnosticOutcome.FALLBACK)
            load(positionTicks())
        } else {
            PlaybackDiagnostics.failure(diagnosticToken, DiagnosticStage.STREAM, error)
            showError(environment.failureMessage(error))
        }
    }

    private fun environmentChanged() {
        mutable.update { it.copy(audioOutputMode = environment.audioMode) }
        if (!released && !suspendedPlayback && item != null && mutable.value.error == null &&
            plan != null && environment.needsReload)
            load(positionTicks())
    }

    private fun stopCurrentStream() {
        if (plan != null) diagnosticObserver.sample()
        PlaybackDiagnostics.canceled(diagnosticToken)
        initialTicks = positionTicks()
        plan?.let { reporter.send(it, "Stopped", initialTicks, true) }
        plan = null
        started = false
        player.stop()
        player.clearMediaItems()
    }

    private fun showError(message: String) {
        discardSubtitleSearch()
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
        environment.start()
        mutable.update { it.copy(audioOutputMode = environment.audioMode) }
        if (suspendedPlayback) {
            suspendedPlayback = false
            if (mutable.value.error == null) load(initialTicks, fetch = true, autoPlay = wasPlaying)
        } else if (wasPlaying && mutable.value.error == null) player.play()
        pollJob?.cancel()
        reportJob?.cancel()
        pollJob = scope.launch {
            var diagnosticSecond = -1L
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
                val second = android.os.SystemClock.elapsedRealtime() / 1000
                if (plan != null && diagnosticSecond != second) {
                    diagnosticSecond = second
                    diagnosticObserver.sample()
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
        environment.stop()
        seekPreview.cancel()
        cancelCountdown()
        pollJob?.cancel()
        reportJob?.cancel()
        wasPlaying = subtitleSearchResume ?: pendingPlay ?: player.playWhenReady
        suspendedPlayback = true
        discardSubtitleSearch()
        loadJob?.cancel()
        subtitleJob?.cancel()
        metadataJob?.cancel()
        seasonJob?.cancel()
        stopCurrentStream()
    }

    fun handleMediaKey(code: Int): Boolean {
        if (mutable.value.subtitleSearchOpen && code in setOf(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND)) return true
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
        out.putBoolean("playing", if (suspendedPlayback) wasPlaying else subtitleSearchResume ?: pendingPlay ?: player.playWhenReady)
        out.putBoolean("compatible", compatible)
        out.putFloat("speed", player.playbackParameters.speed)
        out.putInt("audio", audioIndex ?: -1)
        subtitleIndex?.let { out.putInt("subtitle", it) }
        out.putInt("bitrate", bitrate)
    }

    fun release() {
        seekPreview.cancel()
        if (released) return
        released = true
        environment.stop()
        player.removeAnalyticsListener(diagnosticObserver)
        discardSubtitleSearch()
        loadJob?.cancel()
        subtitleJob?.cancel()
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
