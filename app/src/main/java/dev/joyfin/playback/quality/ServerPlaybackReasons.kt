package dev.joyfin.playback.quality

/** Only known server codes become presentation text; never expose arbitrary response strings. */
internal fun serverPlaybackReasons(codes: List<String>): List<String> = codes.map {
    reasonLabels[it] ?: "服务端提供了其他转码原因"
}.distinct()

private val reasonLabels = mapOf(
    "ContainerNotSupported" to "设备不支持源容器", "VideoCodecNotSupported" to "设备不支持视频编码",
    "AudioCodecNotSupported" to "设备不支持音频编码", "SubtitleCodecNotSupported" to "字幕需要转换或烧录",
    "VideoResolutionNotSupported" to "视频分辨率超出设备能力", "VideoBitDepthNotSupported" to "视频位深不受支持",
    "VideoProfileNotSupported" to "视频编码规格不受支持", "VideoLevelNotSupported" to "视频编码级别不受支持",
    "VideoFramerateNotSupported" to "视频帧率不受支持", "AudioChannelsNotSupported" to "声道数不受支持",
    "AudioSampleRateNotSupported" to "音频采样率不受支持", "ContainerBitrateExceedsLimit" to "媒体码率超过设置上限",
    "VideoBitrateNotSupported" to "视频码率不受支持", "AudioBitrateNotSupported" to "音频码率不受支持",
    "DirectPlayError" to "直接播放失败后回退",
)
