package dev.joyfin.playback

import dev.joyfin.data.*
import dev.joyfin.playback.quality.*

object DeviceProfile {
    internal fun negotiate(capabilities: PlaybackCapabilitySnapshot, decision: PlaybackDecision,
        source: MediaSource?, audioIndex: Int?, bitrate: Int): PlaybackNegotiation {
        val audio = PlaybackDecisionPolicy.audioForProfile(capabilities.audio, decision, source, audioIndex)
        val base = create(capabilities.video.codecs, audio, bitrate)
        val video = source?.streams?.firstOrNull { it.type == "Video" }
        val conditions = video?.let {
            CodecProfile("Video", it.codec.lowercase(), listOf(ProfileCondition(
                condition = "EqualsAny", property = "VideoRangeType",
                value = (if (decision.videoTranscode) setOf("SDR") else capabilities.video.acceptedRanges)
                    .ifEmpty { setOf("Unknown", "SDR") }.joinToString("|"),
            )))
        }
        val pcmChannels = decision.pcmOutputChannels.coerceIn(2, 8)
        val encodedChannels = if (decision.pcmOnly) 0 else
            listOf("aac", "ac3", "eac3", "mp3").maxOf { capabilities.audio.pathsByCodec[it]?.passthroughChannels ?: 0 }
        val maxChannels = maxOf(pcmChannels, encodedChannels).coerceIn(2, 8)
        return PlaybackNegotiation(capabilities, decision, base.copy(
            codecProfiles = base.codecProfiles + listOfNotNull(conditions),
            transcodingProfiles = base.transcodingProfiles.map { transcode -> transcode.copy(
                maxAudioChannels = maxChannels.toString(),
                audioCodec = transcode.audioCodec.split(',').sortedByDescending {
                    if (decision.pcmOnly) 0 else capabilities.audio.pathsByCodec[it]?.passthroughChannels ?: 0
                }.joinToString(","),
            ) },
        ))
    }

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
