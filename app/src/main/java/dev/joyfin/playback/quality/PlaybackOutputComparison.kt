package dev.joyfin.playback.quality

/** Compare observed output with the selected input without claiming receiver-side Atmos/HDR. */
internal fun actualOutputReasons(decision: PlaybackDecision?, output: PlaybackOutputSnapshot): List<String> = buildList {
    if (decision == null) return@buildList
    if (output.pcm == true && decision.audioPath == AudioPath.PASSTHROUGH)
        add("实际已配置为 PCM 输出，未采用原编码输出")
    if (decision.sourceAudioMime == "audio/vnd.dts.hd" && output.audioMime == "audio/vnd.dts")
        add("DTS-HD 使用 DTS 核心编码输出")
    if (decision.sourceAudioMime == "audio/eac3-joc" && output.audioMime == "audio/eac3")
        add("当前配置为普通 E-AC-3 输出，对象音效未确认")
    if (output.pcm == true && output.channels > 0 && output.channels < decision.audioInputChannels)
        add("AudioTrack 输出声道少于片源声道")
    else if (output.pcm == true && decision.pcmOutputChannels in 1 until decision.audioInputChannels)
        add("PCM 直出能力探测低于片源声道，最终声道映射由系统处理")
}

internal fun needsVideoConversion(decision: PlaybackDecision, display: DisplayPlaybackCapabilities,
    observed: VideoRange): Boolean {
    if (decision.videoTranscode || decision.videoPath == VideoPath.BASE_LAYER) return false
    if (observed in setOf(VideoRange.UNKNOWN, VideoRange.SDR)) return false
    if (observed == VideoRange.HDR10 && VideoRange.HDR10_PLUS in display.hdrTypes) return false
    return observed !in display.hdrTypes
}
