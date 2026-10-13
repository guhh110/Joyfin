package dev.joyfin.playback

import android.content.Context
import dev.joyfin.data.*

object DeviceProfile {
    fun build(
        context: Context,
        source: MediaSource? = null,
        bitrate: Int = PlaybackQuality.DEFAULT,
        audioIndex: Int? = null,
    ): DeviceProfileDto =
        create(
            VideoPlaybackCapabilities.detect(context, source),
            AudioPlaybackCapabilities.detect(context, source, audioIndex),
            bitrate,
        )

    internal fun create(
        video: Set<String>,
        audio: AudioPlaybackCapabilities,
        bitrate: Int,
    ): DeviceProfileDto =
        DeviceProfileDto(
            maxStreamingBitrate = bitrate,
            maxStaticBitrate = bitrate,
            directPlayProfiles =
                ContainerPlaybackCapabilities.profiles(video, audio.channelsByCodec.keys),
            // Permit HEVC copying when only the audio needs conversion. These codecs work in
            // TS/HLS.
            transcodingProfiles =
                listOf(
                    TranscodingProfile(
                        videoCodec =
                            listOf("h264", "hevc")
                                .filter { it in video }
                                .joinToString(",")
                                .ifEmpty { "h264" },
                        audioCodec =
                            listOf("aac", "ac3", "eac3", "mp3")
                                .filter { it in audio.channelsByCodec }
                                .joinToString(",")
                                .ifEmpty { "aac" },
                    ),
                    TranscodingProfile(
                        container = "mp4",
                        videoCodec =
                            listOf("h264", "hevc", "vp9", "av1")
                                .filter { it in video }
                                .joinToString(",")
                                .ifEmpty { "h264" },
                        audioCodec =
                            listOf(
                                    "aac",
                                    "ac3",
                                    "eac3",
                                    "mp3",
                                    "alac",
                                    "flac",
                                    "opus",
                                    "dts",
                                    "truehd",
                                )
                                .filter { it in audio.channelsByCodec }
                                .joinToString(",")
                                .ifEmpty { "aac" },
                    ),
                ),
            codecProfiles = audio.codecProfiles(),
            subtitleProfiles = SubtitleDelivery.profiles(),
        )
}
