@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback.quality

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.audio.AudioSink

/** Finite, per-media/version/audio/route attempts. Automatic choices never enter saved preferences. */
internal class PlaybackFallbackPolicy {
    private data class Key(val media: String, val source: String?, val audio: Int?, val revision: Long)
    private var key: Key? = null
    var forcePcm = false
        private set
    var forceAudio = false
        private set
    var forceSoftware = false
        private set
    var forceVideo = false
        private set
    val active: Boolean get() = forcePcm || forceSoftware || forceAudio || forceVideo

    fun context(media: String, source: String?, audio: Int?, revision: Long) {
        val next = Key(media, source, audio, revision)
        if (key != next) { reset(); key = next }
    }
    fun reset() { forcePcm = false; forceSoftware = false; forceAudio = false; forceVideo = false; key = null }
    fun requireVideoConversion(): Boolean {
        if (forceVideo) return false
        forceVideo = true
        return true
    }

    fun advance(error: PlaybackException, decision: PlaybackDecision?): Boolean {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        // A network failure must not change codecs or turn HDR into SDR.
        if (error.errorCode in 2000..2999 || causes.any { it is HttpDataSource.HttpDataSourceException }) return false
        val audio = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType?.startsWith("audio/") == true ||
            error.errorCode in setOf(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED) ||
            causes.any { it is AudioSink.InitializationException || it is AudioSink.WriteException || it is AudioSink.ConfigurationException }
        val formatError = error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        if (!audio && !formatError) return false
        if (audio) {
            if (!forcePcm && decision?.pcmOnly != true && decision?.audioTranscode != true &&
                (decision?.audioSupport?.decodedChannels ?: 0) >= (decision?.audioInputChannels ?: 1)) {
                forcePcm = true; return true
            }
            if (!forceSoftware && decision?.audioPath != AudioPath.SOFTWARE_PCM && decision?.audioTranscode != true &&
                (decision?.audioSupport?.softwareChannels ?: 0) >= (decision?.audioInputChannels ?: 1)) {
                forcePcm = true; forceSoftware = true; return true
            }
            if (!forceAudio && decision?.audioTranscode != true) {
                forceAudio = true; forcePcm = false; forceSoftware = false; return true
            }
        } else if (!forceVideo && decision?.videoTranscode != true) { forceVideo = true; return true }
        return false
    }

    fun failureMessage(error: PlaybackException, decision: PlaybackDecision?): String = when {
        error.errorCode in 2000..2999 -> "播放中断：${error.errorCodeName}。请检查网络或服务器后重试。"
        decision?.discSource == true -> "服务器原盘主影片流未能播放，请检查服务器提取与转码能力，或使用 MKV Remux；客户端不支持原盘菜单导航。"
        else -> "播放中断：${error.errorCodeName}。当前可用播放路径未成功，请检查设备或服务器。"
    }
}
