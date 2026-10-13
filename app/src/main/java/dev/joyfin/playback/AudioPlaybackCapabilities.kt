@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioCapabilities
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
                "flac" to listOf("audio/flac"),
                "opus" to listOf("audio/opus"),
                "vorbis" to listOf("audio/vorbis"),
                "ac3" to listOf("audio/ac3"),
                "eac3" to listOf("audio/eac3", "audio/eac3-joc"),
                "dts" to listOf("audio/vnd.dts"),
                "dtshd" to listOf("audio/vnd.dts.hd"),
                "truehd" to listOf("audio/true-hd"),
            )

        fun detect(context: Context, source: MediaSource?): AudioPlaybackCapabilities {
            val output = AudioCapabilities.getCapabilities(context, attributes, null, emptyList())
            val streams = source?.streams.orEmpty()
            val limits = linkedMapOf("pcm" to 8, "lpcm" to 8)
            mimeTypes.forEach { (codec, mimes) ->
                val rate =
                    streams
                        .firstOrNull { it.type == "Audio" && it.codec.equals(codec, true) }
                        ?.sampleRate
                        ?.takeIf { it > 0 } ?: 48_000
                val max =
                    mimes.maxOf { mime ->
                        val decoders =
                            runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false) }
                                .getOrDefault(emptyList())
                        (8 downTo 1).firstOrNull { channels ->
                            val format =
                                Format.Builder()
                                    .setSampleMimeType(mime)
                                    .setSampleRate(rate)
                                    .setChannelCount(channels)
                                    .build()
                            runCatching {
                                    output.isPassthroughPlaybackSupported(format, attributes)
                                }
                                .getOrDefault(false) ||
                                decoders.any { decoder ->
                                    runCatching { decoder.isFormatSupported(context, format) }
                                        .getOrDefault(false)
                                }
                        } ?: 0
                    }
                if (max > 0) limits[codec] = max
            }
            return AudioPlaybackCapabilities(limits)
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
