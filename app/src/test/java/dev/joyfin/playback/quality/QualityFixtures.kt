package dev.joyfin.playback.quality

import dev.joyfin.data.AudioOutputMode
import dev.joyfin.data.MediaSource
import dev.joyfin.data.MediaStream
import dev.joyfin.playback.AudioCodecCapabilities
import dev.joyfin.playback.AudioPlaybackCapabilities

internal object QualityFixtures {
    val allPaths = AudioCodecCapabilities(48_000, 8, 8, 8)
    val hdrVideo = VideoPlaybackSupport(setOf("h264", "hevc"), VideoRange.HDR10,
        VideoRange.HDR10, VideoPath.NATIVE, setOf("SDR", "Unknown", "HDR10"))

    fun source(codec: String = "dts", channels: Int = 6, sampleRate: Int = 48_000) = MediaSource(
        id = "main", container = "mkv", defaultAudioIndex = 1,
        streams = listOf(
            MediaStream(type = "Video", index = 0, codec = "hevc", videoRangeType = "HDR10"),
            MediaStream(type = "Audio", index = 1, codec = codec, channels = channels, sampleRate = sampleRate),
        ),
    )

    fun capabilities(
        paths: Map<String, AudioCodecCapabilities> = mapOf("dts" to allPaths),
        pcmChannels: Int = 8,
        video: VideoPlaybackSupport = hdrVideo,
    ) = PlaybackCapabilitySnapshot(1, DisplayPlaybackCapabilities(true, setOf(VideoRange.HDR10)), video,
        AudioPlaybackCapabilities(paths.mapValues { it.value.channels(false) }, paths,
            (paths.values.map { it.sampleRate } + 48_000).associateWith { pcmChannels }))

    fun requirements(
        mode: AudioOutputMode = AudioOutputMode.AUTO,
        compatible: Boolean = false,
        forcePcm: Boolean = false,
        forceSoftware: Boolean = false,
        forceAudio: Boolean = false,
        forceVideo: Boolean = false,
        speed: Float = 1f,
    ) = PlaybackRequirements(mode, compatible, forcePcm, forceSoftware, forceAudio, forceVideo, speed)

    fun decision(
        source: MediaSource = source(),
        capabilities: PlaybackCapabilitySnapshot = capabilities(),
        requirements: PlaybackRequirements = requirements(),
        audioIndex: Int? = null,
    ) = PlaybackDecisionPolicy.decide(source, audioIndex, capabilities, requirements)
}
