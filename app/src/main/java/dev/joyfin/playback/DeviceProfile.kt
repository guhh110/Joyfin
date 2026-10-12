package dev.joyfin.playback

import android.content.Context
import android.media.MediaCodecList
import dev.joyfin.data.objects
import org.json.JSONArray
import org.json.JSONObject

object DeviceProfile {
    private val directContainers = listOf(
        "mp4", "m4v", "mkv", "webm", "mov", "ts", "mpegts", "m2ts"
    )

    /** 构建向 Jellyfin 协商的真实设备 Profile */
    fun build(context: Context, source: JSONObject? = null, bitrate: Int = 80_000_000): JSONObject {
        val decoderInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        val video = source?.optJSONArray("MediaStreams").objects().firstOrNull { it.optString("Type") == "Video" }

        val codecs = linkedMapOf(
            "h264" to "video/avc",
            "hevc" to "video/hevc",
            "vp9" to "video/x-vnd.on2.vp9",
            "av1" to "video/av01",
            "mpeg2video" to "video/mpeg2"
        ).filter { (codec, mime) ->
            decoderInfos.any { info ->
                if (info.supportedTypes.none { it.equals(mime, true) }) false else runCatching {
                    val caps = info.getCapabilitiesForType(mime).videoCapabilities
                    val applies = video?.optString("Codec") == codec
                    !applies || caps == null || (video?.optInt("Width") ?: 0 <= 0 || video?.optInt("Height") ?: 0 <= 0 || caps.isSizeSupported(video?.optInt("Width") ?: 0, video?.optInt("Height") ?: 0))
                }.getOrDefault(false)
            }
        }.keys.toMutableSet()

        if (video?.optString("Codec") == "h264" && video.optInt("BitDepth", 8) > 8) {
            codecs.remove("h264")
        }

        val audio = AudioPlaybackCapabilities.detect(context, source)
        val profile = JSONObject()
            .put("Name", "Joyfin Android Cinema")
            .put("MaxStreamingBitrate", bitrate)
            .put("MaxStaticBitrate", bitrate)

        profile.put("DirectPlayProfiles", JSONArray().put(
            JSONObject()
                .put("Type", "Video")
                .put("Container", directContainers.joinToString(","))
                .put("VideoCodec", codecs.joinToString(","))
                .put("AudioCodec", audio.codecs)
        ))

        profile.put("TranscodingProfiles", JSONArray().put(
            JSONObject()
                .put("Type", "Video")
                .put("Container", "ts")
                .put("Protocol", "hls")
                .put("Context", "Streaming")
                .put("VideoCodec", "h264")
                .put("AudioCodec", "aac")
                .put("MaxAudioChannels", "6")
                .put("MinSegments", 2)
                .put("SegmentLength", 4)
                .put("EnableSubtitlesInManifest", false)
                .put("BreakOnNonKeyFrames", false)
        ))

        val codecProfiles = audio.codecProfiles()

        if (bitrate <= 8_000_000) {
            codecProfiles.put(
                JSONObject().put("Type", "Video")
                    .put("Codec", codecs.joinToString(","))
                    .put("Conditions", JSONArray().put(
                        JSONObject().put("Condition", "LessThanEqual")
                            .put("Property", "Height")
                            .put("Value", if (bitrate <= 4_000_000) "720" else "1080")
                            .put("IsRequired", true)
                    ))
            )
        }
        profile.put("CodecProfiles", codecProfiles)

        val subtitles = JSONArray()
        dev.joyfin.data.SubtitleDelivery.externalCodecs.forEach {
            subtitles.put(JSONObject().put("Format", it).put("Method", "External"))
        }
        dev.joyfin.data.SubtitleDelivery.bitmapCodecs.forEach {
            subtitles.put(JSONObject().put("Format", it).put("Method", "Encode"))
        }
        return profile.put("SubtitleProfiles", subtitles)
    }
}
