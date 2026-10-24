package dev.joyfin.playback.quality

/** Only known server codes become presentation text; never expose arbitrary response strings. */
internal fun serverPlaybackReasons(codes: List<String>): List<String> = codes.map {
    reasonLabels[it] ?: "服务端提供了其他转码原因"
}.distinct()

private val reasonLabels = mapOf(
    "ContainerNotSupported" to "这个视频的封装格式设备不支持",
    "VideoCodecNotSupported" to "视频编码设备无法直接解码",
    "AudioCodecNotSupported" to "音频编码设备无法直接解码",
    "SubtitleCodecNotSupported" to "字幕格式无法直接显示，服务器正在处理",
    "VideoResolutionNotSupported" to "分辨率超出设备能力",
    "VideoBitDepthNotSupported" to "视频位深设备不支持",
    "VideoProfileNotSupported" to "视频编码规格设备不支持",
    "VideoLevelNotSupported" to "视频编码级别设备不支持",
    "VideoFramerateNotSupported" to "视频帧率设备不支持",
    "AudioChannelsNotSupported" to "声道数超出设备能力",
    "AudioSampleRateNotSupported" to "音频采样率设备不支持",
    "ContainerBitrateExceedsLimit" to "码率超过你设置的上限",
    "VideoBitrateNotSupported" to "视频码率设备不支持",
    "AudioBitrateNotSupported" to "音频码率设备不支持",
    "DirectPlayError" to "直接播放失败，已自动回退",
)
