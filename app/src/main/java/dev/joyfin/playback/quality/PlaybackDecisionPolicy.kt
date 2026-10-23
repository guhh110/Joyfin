package dev.joyfin.playback.quality

import dev.joyfin.data.AudioOutputMode
import dev.joyfin.data.MediaSource
import dev.joyfin.data.audioStream
import dev.joyfin.playback.AudioPlaybackCapabilities

/** Pure policy: no Android APIs, persistence, player calls or network requests. */
internal object PlaybackDecisionPolicy {
    fun canChangeSpeedInPlace(current: PlaybackDecision, next: PlaybackDecision): Boolean =
        current.audioPath == next.audioPath && current.videoPath == next.videoPath &&
            current.audioTranscode == next.audioTranscode && current.videoTranscode == next.videoTranscode &&
            (current.audioPath == AudioPath.NONE || current.pcmOnly == next.pcmOnly)

    fun decide(source: MediaSource?, audioIndex: Int?, capabilities: PlaybackCapabilitySnapshot,
        requirements: PlaybackRequirements): PlaybackDecision {
        val (mode, compatible, forcePcm, forceSoftware, forceAudio, forceVideo, speed) = requirements
        val audio = source?.audioStream(audioIndex)
        val support = audio?.let { capabilities.audio.pathsByCodec[it.codec.lowercase()] }
        val channels = audio?.channels?.coerceAtLeast(1) ?: 1
        val pcmOnly = mode == AudioOutputMode.PCM || forcePcm || speed != 1f
        val audioPath = when {
            audio == null -> AudioPath.NONE
            forceAudio || compatible -> AudioPath.SERVER
            forceSoftware && (support?.softwareChannels ?: 0) >= channels -> AudioPath.SOFTWARE_PCM
            forceSoftware -> AudioPath.SERVER
            !pcmOnly && (support?.passthroughChannels ?: 0) >= channels -> AudioPath.PASSTHROUGH
            (support?.platformChannels ?: 0) >= channels -> AudioPath.PLATFORM_PCM
            (support?.softwareChannels ?: 0) >= channels -> AudioPath.SOFTWARE_PCM
            else -> AudioPath.SERVER
        }
        val video = capabilities.video
        val videoTranscode = compatible || forceVideo || video.path == VideoPath.TRANSCODE
        val disc = source?.container?.lowercase() in setOf("iso", "bluray", "dvd", "bdmv", "video_ts") ||
            source?.videoType?.lowercase() in setOf("iso", "bluray", "dvd") ||
            source?.isoType?.lowercase() in setOf("bluray", "dvd")
        val reasons = buildList {
            addAll(video.reasons)
            if (speed != 1f && audio != null) add("倍速播放使用 PCM 兼容输出")
            if (compatible) add("使用已选择的兼容转码模式")
            else if (forceVideo && video.path != VideoPath.TRANSCODE) add("视频播放失败，使用服务器兼容转换")
            when (audioPath) {
                AudioPath.PLATFORM_PCM, AudioPath.SOFTWARE_PCM -> {
                    add(if (pcmOnly) "使用 PCM 兼容输出" else "原编码输出不可用，使用本机 PCM 解码")
                    if (audio?.audioSpatialFormat in setOf("DolbyAtmos", "DTSX") ||
                        audio?.profile?.contains("Atmos", true) == true || audio?.profile?.contains("DTS:X", true) == true)
                        add("片源包含对象音效，普通 PCM 输出不作对象音效保留承诺")
                }
                AudioPath.SERVER -> add(if (videoTranscode) "服务器转换音轨" else "当前音轨需由服务器转换，优先保留原视频")
                else -> Unit
            }
            if (disc) add("原盘使用服务器主影片流，不提供客户端菜单导航")
        }
        return PlaybackDecision(
            audioPath = audioPath,
            videoPath = if (videoTranscode) VideoPath.TRANSCODE else video.path,
            sourceRange = video.sourceRange,
            targetRange = if (videoTranscode) VideoRange.SDR else video.targetRange,
            pcmOnly = pcmOnly || audioPath in setOf(AudioPath.PLATFORM_PCM, AudioPath.SOFTWARE_PCM),
            videoTranscode = videoTranscode,
            audioTranscode = audioPath == AudioPath.SERVER,
            discSource = disc,
            outputMode = mode,
            reasons = reasons.distinct(),
            audioSupport = support,
            pcmOutputChannels = capabilities.audio.pcmChannelsByRate[audio?.sampleRate] ?: 2,
            audioInputChannels = audio?.channels ?: 0,
            excludeSourceAudio = forceAudio,
            sourceAudioMime = audio?.let { AudioPlaybackCapabilities.mimesFor(it).firstOrNull() },
        )
    }

    fun audioForProfile(capabilities: AudioPlaybackCapabilities, decision: PlaybackDecision, source: MediaSource?, audioIndex: Int?): AudioPlaybackCapabilities {
        val selected = source?.audioStream(audioIndex)
        val denied = if (decision.excludeSourceAudio && selected != null) AudioPlaybackCapabilities.canonical(selected.codec) else null
        val channels = capabilities.pathsByCodec.mapValues { (codec, value) ->
            if (AudioPlaybackCapabilities.canonical(codec) == denied) 0 else value.channels(decision.pcmOnly)
        }.filterValues { it > 0 }
        return capabilities.copy(channelsByCodec = channels)
    }
}
