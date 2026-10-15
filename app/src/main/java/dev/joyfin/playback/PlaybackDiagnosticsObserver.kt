@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.common.C
import androidx.media3.common.Player
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
) : AnalyticsListener {
    private fun accepts(eventTime: AnalyticsListener.EventTime): Boolean = acceptsEvents() &&
        (eventTime.timeline.isEmpty || eventTime.timeline == player.currentTimeline)

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int,
        totalBytesLoaded: Long, bitrateEstimate: Long) {
        if (accepts(eventTime)) PlaybackDiagnostics.bandwidth(token(), bitrateEstimate)
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
    }
}
