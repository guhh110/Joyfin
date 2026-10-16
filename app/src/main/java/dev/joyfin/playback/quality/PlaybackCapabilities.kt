package dev.joyfin.playback.quality

import dev.joyfin.data.AudioOutputMode
import dev.joyfin.data.DeviceProfileDto
import dev.joyfin.data.PlaybackSourcePolicy
import dev.joyfin.playback.AudioPlaybackCapabilities
import dev.joyfin.playback.AudioCodecCapabilities

enum class VideoRange(val label: String) {
    UNKNOWN("未知"), SDR("SDR"), HDR10("HDR10"), HLG("HLG"), HDR10_PLUS("HDR10+"), DOLBY_VISION("Dolby Vision"),
}
enum class VideoPath(val label: String) {
    NATIVE("原格式播放"), BASE_LAYER("兼容基础层"), TRANSCODE("服务器转换视频"), UNKNOWN("待确认"),
}
enum class AudioPath(val label: String) {
    PASSTHROUGH("优先编码输出"), PLATFORM_PCM("系统解码为 PCM"), SOFTWARE_PCM("FFmpeg 解码为 PCM"),
    SERVER("服务器转换音频"), NONE("无音轨"),
}

data class DisplayPlaybackCapabilities(
    val known: Boolean = false,
    val hdrTypes: Set<VideoRange> = emptySet(),
) {
    val description: String get() = if (!known) "未知" else
        hdrTypes.joinToString(" / ") { it.label }.ifEmpty { "SDR" }
}

data class VideoPlaybackSupport(
    val codecs: Set<String>,
    val sourceRange: VideoRange = VideoRange.UNKNOWN,
    val targetRange: VideoRange = VideoRange.UNKNOWN,
    val path: VideoPath = VideoPath.UNKNOWN,
    val acceptedRanges: Set<String> = emptySet(),
    val reasons: List<String> = emptyList(),
)

data class PlaybackCapabilitySnapshot(
    val revision: Long,
    val display: DisplayPlaybackCapabilities,
    val video: VideoPlaybackSupport,
    val audio: AudioPlaybackCapabilities,
)

internal data class PlaybackRequirements(
    val mode: AudioOutputMode,
    val compatible: Boolean,
    val forcePcm: Boolean,
    val forceSoftware: Boolean,
    val forceAudio: Boolean,
    val forceVideo: Boolean,
    val speed: Float,
)

/** All strings are generated labels, never untrusted track names, paths or URLs. */
data class PlaybackDecision(
    val audioPath: AudioPath,
    val videoPath: VideoPath,
    val sourceRange: VideoRange,
    val targetRange: VideoRange,
    val pcmOnly: Boolean,
    val videoTranscode: Boolean,
    val audioTranscode: Boolean,
    val discSource: Boolean,
    val outputMode: AudioOutputMode,
    val reasons: List<String>,
    val audioSupport: AudioCodecCapabilities? = null,
    val pcmOutputChannels: Int = 0,
    val audioInputChannels: Int = 0,
    val excludeSourceAudio: Boolean = false,
    val sourceAudioMime: String? = null,
)

data class PlaybackNegotiation(
    val capabilities: PlaybackCapabilitySnapshot,
    val decision: PlaybackDecision,
    val profile: DeviceProfileDto,
) {
    val requestPolicy: PlaybackSourcePolicy get() = PlaybackSourcePolicy(
        profile = profile,
        videoTranscode = decision.videoTranscode,
        audioTranscode = decision.audioTranscode,
        discSource = decision.discSource,
        maxAudioChannels = if (decision.audioTranscode)
            profile.transcodingProfiles.mapNotNull { it.maxAudioChannels.toIntOrNull() }.maxOrNull()
        else null,
    )
}

/** Identity stays internal to the playback pipeline and is never included in copied reports. */
data class PlaybackOutputOwner(val generation: Long, val diagnosticId: Long)

data class PlaybackOutputSnapshot(
    val encoding: String = "未知",
    val pcm: Boolean? = null,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val route: String = "未知",
    val offload: Boolean = false,
    val audioMime: String? = null,
) {
    val description: String get() = listOfNotNull(
        encoding,
        sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
        channels.takeIf { it > 0 }?.let { "$it 声道" },
    ).joinToString(" · ")
}
