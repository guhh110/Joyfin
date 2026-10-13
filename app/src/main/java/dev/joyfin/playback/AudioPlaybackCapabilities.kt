@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import dev.joyfin.data.*

/** Input channels can be decoded locally even when the final PCM output is stereo. */
data class AudioPlaybackCapabilities(val channelsByCodec: Map<String, Int>) {
    val codecs: String
        get() = channelsByCodec.keys.joinToString(",")

    fun codecProfiles(): List<CodecProfile> =
        channelsByCodec.map { (codec, channels) ->
            CodecProfile(
                "VideoAudio",
                codec,
                listOf(ProfileCondition(property = "AudioChannels", value = channels.toString())),
            )
        }

    companion object {
        val attributes: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
        private val mimeTypes =
            linkedMapOf(
                "aac" to listOf("audio/mp4a-latm"),
                "mp3" to listOf("audio/mpeg"),
                "mp1" to listOf("audio/mpeg-L1"),
                "mp2" to listOf("audio/mpeg-L2"),
                "alac" to listOf("audio/alac"),
                "flac" to listOf("audio/flac"),
                "opus" to listOf("audio/opus"),
                "vorbis" to listOf("audio/vorbis"),
                "ac3" to listOf("audio/ac3"),
                "ac4" to listOf("audio/ac4"),
                "eac3" to listOf("audio/eac3", "audio/eac3-joc"),
                "dts" to listOf("audio/vnd.dts"),
                "dca" to listOf("audio/vnd.dts"),
                "dtshd" to listOf("audio/vnd.dts.hd"),
                "dts_hd" to listOf("audio/vnd.dts.hd"),
                "truehd" to listOf("audio/true-hd"),
                "mlp" to listOf("audio/true-hd"),
                "amr_nb" to listOf("audio/3gpp"),
                "amr_wb" to listOf("audio/amr-wb"),
                "pcm_s16le" to listOf("audio/raw"),
                "pcm_s24le" to listOf("audio/raw"),
                "pcm_s32le" to listOf("audio/raw"),
                "pcm_f32le" to listOf("audio/raw"),
                "pcm_u8" to listOf("audio/raw"),
                "pcm_alaw" to listOf("audio/g711-alaw"),
                "pcm_mulaw" to listOf("audio/g711-mlaw"),
            )

        fun detect(
            context: Context,
            source: MediaSource?,
            audioIndex: Int? = null,
        ): AudioPlaybackCapabilities {
            val output = AudioCapabilities.getCapabilities(context, attributes, null, emptyList())
            // Query the renderer, including its PCM sink constraints, rather than just the MIME
            // list.
            val sink = DefaultAudioSink.Builder(context).build()
            sink.setAudioAttributes(attributes)
            val software = FfmpegAudioRenderer(null, null, sink)
            val selected =
                source?.streams?.firstOrNull {
                    it.type == "Audio" && it.index == (audioIndex ?: source.defaultAudioIndex)
                }
            return detectWithProbe(source, audioIndex) { mime, rate, channels ->
                val input =
                    selected?.takeIf { mime in mimeTypes[it.codec.lowercase()].orEmpty() }
                        ?: source?.streams?.firstOrNull {
                            it.type == "Audio" && mime in mimeTypes[it.codec.lowercase()].orEmpty()
                        }
                val format =
                    Format.Builder()
                        .setSampleMimeType(mime)
                        .setSampleRate(rate)
                        .setChannelCount(channels)
                        .setCodecs(
                            if (
                                mime == "audio/mp4a-latm" &&
                                    input?.profile?.contains("xhe", true) == true
                            )
                                "mp4a.40.42"
                            else null
                        )
                        .setPcmEncoding(
                            if (input?.codec == "pcm_f32le") C.ENCODING_PCM_FLOAT
                            else if (input?.codec == "pcm_u8") C.ENCODING_PCM_8BIT
                            else if (input?.bitDepth == 32) C.ENCODING_PCM_32BIT
                            else if (input?.bitDepth == 24) C.ENCODING_PCM_24BIT
                            else C.ENCODING_PCM_16BIT
                        )
                        .build()
                if (mime == "audio/raw")
                    runCatching {
                            sink.getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
                        }
                        .getOrDefault(false)
                else
                    runCatching { output.isPassthroughPlaybackSupported(format, attributes) }
                        .getOrDefault(false) ||
                        runCatching {
                                MediaCodecUtil.getDecoderInfosSoftMatch(
                                        MediaCodecSelector.DEFAULT,
                                        format,
                                        false,
                                        false,
                                    )
                                    .any { it.isFormatSupported(context, format) }
                            }
                            .getOrDefault(false) ||
                        runCatching {
                                RendererCapabilities.getFormatSupport(
                                    software.supportsFormat(format)
                                ) == C.FORMAT_HANDLED
                            }
                            .getOrDefault(false)
            }
        }

        /** Keep negotiation testable without loading Android codecs or the native extension. */
        internal fun detectWithProbe(
            source: MediaSource?,
            audioIndex: Int? = null,
            supports: (mime: String, sampleRate: Int, channels: Int) -> Boolean,
        ): AudioPlaybackCapabilities {
            val streams = source?.streams.orEmpty()
            val selected =
                streams.firstOrNull {
                    it.type == "Audio" && it.index == (audioIndex ?: source?.defaultAudioIndex)
                }
            val limits = linkedMapOf("pcm" to 8, "lpcm" to 8)
            mimeTypes.forEach { (codec, mimes) ->
                val rate =
                    (selected?.takeIf { canonical(it.codec) == canonical(codec) }
                            ?: streams.firstOrNull {
                                it.type == "Audio" && canonical(it.codec) == canonical(codec)
                            })
                        ?.sampleRate
                        ?.takeIf { it > 0 } ?: 48_000
                val max =
                    mimes.maxOf { mime ->
                        (8 downTo 1).firstOrNull { channels -> supports(mime, rate, channels) } ?: 0
                    }
                if (max > 0) limits[codec] = max
            }
            return AudioPlaybackCapabilities(limits)
        }

        private fun canonical(codec: String): String =
            when (val value = codec.lowercase()) {
                "dca" -> "dts"
                "dts_hd" -> "dtshd"
                "mlp" -> "truehd"
                else -> value
            }

        internal fun mimesFor(stream: MediaStream): List<String> =
            if (
                canonical(stream.codec) == "dtshd" ||
                    (canonical(stream.codec) == "dts" && stream.profile.contains("HD", true))
            )
            // MKV may label the same DTS-HD samples A_DTS or A_DTS/LOSSLESS.
            listOf("audio/vnd.dts.hd", "audio/vnd.dts")
            else
                mimeTypes[stream.codec.lowercase()].orEmpty().ifEmpty {
                    if (stream.codec.equals("pcm", true) || stream.codec.equals("lpcm", true))
                        listOf("audio/raw")
                    else emptyList()
                }

        /** Use the same per-codec limits in the Jellyfin request, never a fixed stereo ceiling. */
        fun requestChannelLimit(profile: DeviceProfileDto): Int =
            profile.codecProfiles
                .filter { it.type == "VideoAudio" }
                .flatMap { it.conditions }
                .filter { it.property == "AudioChannels" }
                .mapNotNull { it.value.toIntOrNull() }
                .maxOrNull()
                ?.coerceIn(2, 8) ?: 8
    }
}
