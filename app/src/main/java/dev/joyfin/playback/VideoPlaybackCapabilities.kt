@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import dev.joyfin.data.MediaSource
import dev.joyfin.data.MediaStream
import java.util.Locale

/** Use Media3 decoder selection and profile/level, resolution and frame-rate checks. */
object VideoPlaybackCapabilities {
    private val mimeTypes =
        linkedMapOf(
            "h264" to "video/avc",
            "hevc" to "video/hevc",
            "vp9" to "video/x-vnd.on2.vp9",
            "av1" to "video/av01",
            "mpeg2video" to "video/mpeg2",
            "mpeg4" to "video/mp4v-es",
            "h263" to "video/3gpp",
            "vp8" to "video/x-vnd.on2.vp8",
        )

    fun detect(context: Context, source: MediaSource?): Set<String> {
        val video = source?.streams.orEmpty().firstOrNull { it.type == "Video" }
        return mimeTypes
            .filter { (codec, mime) ->
                val stream = video?.takeIf { it.codec.equals(codec, true) }
                val format =
                    Format.Builder()
                        .setSampleMimeType(
                            if (stream?.dvProfile != null) "video/dolby-vision" else mime
                        )
                        .setCodecs(stream?.let(::codecString))
                        .setWidth(stream?.width?.takeIf { it > 0 } ?: Format.NO_VALUE)
                        .setHeight(stream?.height?.takeIf { it > 0 } ?: Format.NO_VALUE)
                        .setFrameRate(
                            stream
                                ?.let {
                                    it.realFrameRate.takeIf { n -> n > 0 } ?: it.averageFrameRate
                                }
                                ?.takeIf { it > 0 } ?: Format.NO_VALUE.toFloat()
                        )
                        .setColorInfo(stream?.let(::colorInfo))
                        .build()
                runCatching {
                        MediaCodecUtil.getDecoderInfosSoftMatch(
                                MediaCodecSelector.DEFAULT,
                                format,
                                false,
                                false,
                            )
                            .any { decoder -> decoder.isFormatSupported(context, format) }
                    }
                    .getOrDefault(false)
            }
            .keys
    }

    /** Missing levels use the lowest level; actual resolution/rate is checked independently. */
    internal fun codecString(stream: MediaStream): String? {
        val profile = stream.profile.lowercase()
        val level = stream.level?.takeIf { it >= 0 }?.toInt()
        stream.dvProfile?.let { dv ->
            val prefix =
                when (stream.codec.lowercase()) {
                    "h264" -> "dvav"
                    "av1" -> "dav1"
                    else -> "dvhe"
                }
            return "%s.%02d.%02d".format(Locale.ROOT, prefix, dv, stream.dvLevel ?: 1)
        }
        return when (stream.codec.lowercase()) {
            "h264" -> {
                val idc =
                    when {
                        profile.contains("4:4:4") -> 244
                        profile.contains("4:2:2") -> 122
                        profile.contains("10") || stream.bitDepth > 8 -> 110
                        profile.contains("baseline") -> 66
                        profile.contains("main") -> 77
                        profile.contains("extended") -> 88
                        profile.contains("high") -> 100
                        else -> return null
                    }
                "avc1.%02x00%02x".format(Locale.ROOT, idc, level ?: 10)
            }
            "hevc" ->
                "hvc1.${if (stream.bitDepth > 8 || profile.contains("10")) 2 else 1}.6.L${level ?: 30}.B0"
            "vp9" ->
                "vp09.%02d.%02d.%02d"
                    .format(
                        Locale.ROOT,
                        if (profile.contains("3")) 3
                        else if (stream.bitDepth > 8 || profile.contains("2")) 2 else 0,
                        level ?: 10,
                        stream.bitDepth,
                    )
            "av1" -> "av01.0.%02dM.%02d".format(Locale.ROOT, level ?: 0, stream.bitDepth)
            else -> null
        }
    }

    private fun colorInfo(stream: MediaStream): ColorInfo? {
        val range = "${stream.videoRange} ${stream.videoRangeType}".uppercase()
        val transfer =
            when {
                range.contains("HLG") -> C.COLOR_TRANSFER_HLG
                range.contains("HDR10") || range.contains("DOVI") -> C.COLOR_TRANSFER_ST2084
                else -> return null
            }
        return ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(transfer)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .build()
    }
}
