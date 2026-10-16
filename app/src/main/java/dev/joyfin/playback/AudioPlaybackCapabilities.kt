@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import dev.joyfin.data.*

/** Input channels can be decoded locally even when the final PCM output is stereo. */
data class AudioCodecCapabilities(
    val sampleRate: Int,
    val passthroughChannels: Int,
    val platformChannels: Int,
    val softwareChannels: Int,
) {
    val decodedChannels: Int get() = maxOf(platformChannels, softwareChannels)
    fun channels(pcmOnly: Boolean): Int = if (pcmOnly) decodedChannels else maxOf(passthroughChannels, decodedChannels)
}

data class AudioPlaybackCapabilities(
    val channelsByCodec: Map<String, Int>,
    val pathsByCodec: Map<String, AudioCodecCapabilities> = emptyMap(),
    val pcmChannelsByRate: Map<Int, Int> = emptyMap(),
) {
    val codecs: String
        get() = channelsByCodec.keys.joinToString(",")

    fun codecProfiles(): List<CodecProfile> =
        channelsByCodec.map { (codec, channels) ->
            CodecProfile(
                "VideoAudio",
                codec,
                listOf(ProfileCondition(property = "AudioChannels", value = channels.toString())) +
                    listOfNotNull(pathsByCodec[codec]?.sampleRate?.takeIf { it > 0 }?.let {
                        ProfileCondition(condition = "Equals", property = "AudioSampleRate", value = it.toString())
                    }),
            )
        }

    companion object {
        val attributes: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
        internal val mimeTypes =
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

        /** Shared probe entry point, also used by the existing device capability checks. */
        fun detect(context: Context, source: MediaSource?, audioIndex: Int? = null): AudioPlaybackCapabilities =
            AudioCapabilityProbe.detect(context, source, audioIndex)

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

        internal fun canonical(codec: String): String =
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
            else if (stream.codec.equals("eac3", true) &&
                (stream.profile.contains("Atmos", true) || stream.audioSpatialFormat == "DolbyAtmos"))
                listOf("audio/eac3-joc")
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
