@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException

/** Reads typed Media3 facts only. Never records request URLs, headers, bodies or raw logs. */
internal class PlaybackDiagnosticsObserver(
    private val player: ExoPlayer,
    private val token: () -> Long,
    private val acceptsEvents: () -> Boolean,
    private val onVideoInput: (androidx.media3.common.Format) -> Unit,
) : AnalyticsListener {
    private fun accepts(eventTime: AnalyticsListener.EventTime): Boolean = acceptsEvents() &&
        (eventTime.timeline.isEmpty || eventTime.timeline == player.currentTimeline)

    private fun generation(event: AnalyticsListener.EventTime): Long? {
        if (event.windowIndex !in 0 until event.timeline.windowCount) return null
        return event.timeline.getWindow(event.windowIndex, Timeline.Window())
            .mediaItem.localConfiguration?.tag as? Long
    }

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        if (accepts(eventTime)) generation(eventTime)?.let { PlaybackDiagnostics.firstFrame(token(), it) }
    }

    override fun onPositionDiscontinuity(eventTime: AnalyticsListener.EventTime, oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK && accepts(eventTime)) generation(eventTime)?.let {
            PlaybackDiagnostics.bufferOperation(token(), it, PlaybackBufferKind.SEEK)
        }
    }

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        if (!acceptsEvents() || !(events.contains(AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(AnalyticsListener.EVENT_POSITION_DISCONTINUITY))) return
        val event = if (events.contains(AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED))
            events.getEventTime(AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED)
        else events.getEventTime(AnalyticsListener.EVENT_POSITION_DISCONTINUITY)
        if (!accepts(event)) return
        generation(event)?.let {
            val state = when (player.playbackState) {
                Player.STATE_BUFFERING -> TimingPlayerState.BUFFERING
                Player.STATE_READY -> TimingPlayerState.READY
                else -> TimingPlayerState.STOPPED
            }
            PlaybackDiagnostics.timingState(token(), it, state, player.currentPosition, player.playWhenReady)
        }
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int,
        totalBytesLoaded: Long, bitrateEstimate: Long) {
        if (accepts(eventTime)) generation(eventTime)?.let {
            PlaybackDiagnostics.bandwidth(token(), it, bitrateEstimate, totalLoadTimeMs, totalBytesLoaded)
        }
    }
    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long) {
        if (accepts(eventTime)) PlaybackDiagnostics.decoder(token(), true, decoderName)
    }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long) {
        if (accepts(eventTime)) PlaybackDiagnostics.decoder(token(), false, decoderName)
    }
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (!wasCanceled && accepts(eventTime)) PlaybackDiagnostics.failure(token(),
            if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) DiagnosticStage.SUBTITLE else DiagnosticStage.STREAM,
            error, fatal = false)
    }
    fun sample() {
        if (!acceptsEvents()) return
        val state = when {
            player.playerError != null -> DiagnosticPlayerState.FAILED
            player.playbackState == Player.STATE_BUFFERING -> DiagnosticPlayerState.BUFFERING
            player.isPlaying -> DiagnosticPlayerState.PLAYING
            player.playbackState == Player.STATE_READY -> DiagnosticPlayerState.PAUSED
            else -> DiagnosticPlayerState.STOPPED
        }
        PlaybackDiagnostics.playback(token(), state, player.currentPosition,
            player.totalBufferedDuration, player.videoFormat, player.audioFormat)
        player.videoFormat?.let(onVideoInput)
    }
}
