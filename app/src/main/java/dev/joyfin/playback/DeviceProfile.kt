package dev.joyfin.playback

import android.content.Context
import android.media.MediaCodecList
import dev.joyfin.data.*

object DeviceProfile {
    private val directContainers =
        listOf("mp4", "m4v", "mkv", "webm", "mov", "ts", "mpegts", "m2ts")

    /** 构建向 Jellyfin 协商的真实设备 Profile */
    fun build(
        context: Context,
        source: MediaSource? = null,
        bitrate: Int = 80_000_000,
    ): DeviceProfileDto {
        val decoderInfos =
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        val video = source?.streams.orEmpty().firstOrNull { it.type == "Video" }

        val codecs =
            linkedMapOf(
                    "h264" to "video/avc",
                    "hevc" to "video/hevc",
                    "vp9" to "video/x-vnd.on2.vp9",
                    "av1" to "video/av01",
                    "mpeg2video" to "video/mpeg2",
                )
                .filter { (codec, mime) ->
                    decoderInfos.any { info ->
                        if (info.supportedTypes.none { it.equals(mime, true) }) false
                        else
                            runCatching {
                                    val caps = info.getCapabilitiesForType(mime).videoCapabilities
                                    video == null ||
                                        video.codec != codec ||
                                        caps == null ||
                                        video.width <= 0 ||
                                        video.height <= 0 ||
                                        caps.isSizeSupported(video.width, video.height)
                                }
                                .getOrDefault(false)
                    }
                }
                .keys
                .toMutableSet()

        if (video?.codec == "h264" && video.bitDepth > 8) {
            codecs.remove("h264")
        }

        val audio = AudioPlaybackCapabilities.detect(context, source)
        val codecProfiles = audio.codecProfiles().toMutableList()
        if (bitrate <= 8_000_000) {
            codecProfiles.add(
                CodecProfile(
                    "Video",
                    codecs.joinToString(","),
                    listOf(
                        ProfileCondition(
                            property = "Height",
                            value = if (bitrate <= 4_000_000) "720" else "1080",
                            required = true,
                        )
                    ),
                )
            )
        }
        return DeviceProfileDto(
            maxStreamingBitrate = bitrate,
            maxStaticBitrate = bitrate,
            directPlayProfiles =
                listOf(
                    DirectPlayProfile(
                        container = directContainers.joinToString(","),
                        videoCodec = codecs.joinToString(","),
                        audioCodec = audio.codecs,
                    )
                ),
            transcodingProfiles = listOf(TranscodingProfile()),
            codecProfiles = codecProfiles,
            subtitleProfiles =
                SubtitleDelivery.externalCodecs.map { SubtitleProfile(it, "External") } +
                    SubtitleDelivery.bitmapCodecs.map { SubtitleProfile(it, "Encode") },
        )
    }
}
