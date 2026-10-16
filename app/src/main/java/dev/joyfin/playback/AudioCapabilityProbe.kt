@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Util
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import dev.joyfin.data.MediaSource
import dev.joyfin.data.audioStream

/** Hardware/renderer probes only. Negotiation and fallback decisions live in playback.quality. */
internal object AudioCapabilityProbe {
    @Suppress("DEPRECATION")
    fun detect(context: Context, source: MediaSource?, audioIndex: Int?, route: AudioDeviceInfo? = null): AudioPlaybackCapabilities {
        val attributes = AudioPlaybackCapabilities.attributes
        val output = AudioCapabilities.getCapabilities(context, attributes, route, emptyList())
        // Snapshot-only sink: no Context means no playback-looper receivers are registered by probes.
        // The real player uses a Context-aware AudioOutputProvider in PlaybackAudioEngine.
        val sink = DefaultAudioSink.Builder().setAudioCapabilities(output).build()
        sink.setAudioAttributes(attributes)
        val software = FfmpegAudioRenderer(null, null, sink)
        val streams = source?.streams.orEmpty().filter { it.type == "Audio" }
        val selected = source?.audioStream(audioIndex)
        val paths = linkedMapOf<String, AudioCodecCapabilities>()
        try {
            (AudioPlaybackCapabilities.mimeTypes + mapOf("pcm" to listOf("audio/raw"), "lpcm" to listOf("audio/raw")))
                .forEach { (codec, defaultMimes) ->
                    val input = selected?.takeIf { AudioPlaybackCapabilities.canonical(it.codec) == AudioPlaybackCapabilities.canonical(codec) }
                        ?: streams.firstOrNull { AudioPlaybackCapabilities.canonical(it.codec) == AudioPlaybackCapabilities.canonical(codec) }
                    val mimes = input?.let(AudioPlaybackCapabilities::mimesFor)?.takeIf { it.isNotEmpty() } ?: defaultMimes
                    val rate = input?.sampleRate?.takeIf { it > 0 } ?: 48_000
                    fun maxChannels(probe: (Format) -> Boolean): Int = mimes.maxOf { mime ->
                        (8 downTo 1).firstOrNull { count ->
                            val format = Format.Builder().setSampleMimeType(mime).setSampleRate(rate).setChannelCount(count)
                                .setCodecs(if (mime == "audio/mp4a-latm" && input?.profile?.contains("xhe", true) == true) "mp4a.40.42" else null)
                                .setPcmEncoding(when {
                                    input?.codec == "pcm_f32le" -> C.ENCODING_PCM_FLOAT
                                    input?.codec == "pcm_u8" -> C.ENCODING_PCM_8BIT
                                    input?.bitDepth == 32 -> C.ENCODING_PCM_32BIT
                                    input?.bitDepth == 24 -> C.ENCODING_PCM_24BIT
                                    else -> C.ENCODING_PCM_16BIT
                                }).build()
                            runCatching { probe(format) }.getOrDefault(false)
                        } ?: 0
                    }
                    val passthrough = maxChannels { it.sampleMimeType != "audio/raw" && output.isPassthroughPlaybackSupported(it, attributes) }
                    val platform = maxChannels { format ->
                        if (format.sampleMimeType == "audio/raw") sink.getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
                        else MediaCodecUtil.getDecoderInfosSoftMatch(MediaCodecSelector.DEFAULT, format, false, false)
                            .any { it.isFormatSupported(context, format) }
                    }
                    val ffmpeg = maxChannels { format ->
                        format.sampleMimeType != "audio/raw" &&
                            RendererCapabilities.getFormatSupport(software.supportsFormat(format)) == C.FORMAT_HANDLED
                    }
                    paths[codec] = AudioCodecCapabilities(rate, passthrough, platform, ffmpeg)
                }
            val rates = (paths.values.map { it.sampleRate } + 48_000).distinct()
            val pcm = rates.associateWith { rate ->
                (8 downTo 3).firstOrNull { count ->
                    val mask = Util.getAudioTrackChannelConfig(count)
                    if (mask == AudioFormat.CHANNEL_INVALID) return@firstOrNull false
                    if (Build.VERSION.SDK_INT >= 29) runCatching {
                        AudioTrack.isDirectPlaybackSupported(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(mask).build(), attributes.audioAttributesV21.audioAttributes)
                    }.getOrDefault(false)
                    else route?.channelCounts?.contains(count) == true
                } ?: 2
            }
            return AudioPlaybackCapabilities(paths.mapValues { it.value.channels(false) }.filterValues { it > 0 }, paths, pcm)
        } finally {
            // Probes never submit audio or create an AudioTrack.
            sink.release()
        }
    }
}
