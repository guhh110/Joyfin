package dev.joyfin.playback.quality

import dev.joyfin.data.MediaStream
import java.util.Locale

/** Video decisions consume decoder evidence; no display, codec or player APIs run here. */
internal object VideoPlaybackPolicy {
    fun assess(
        stream: MediaStream?,
        display: DisplayPlaybackCapabilities,
        codecs: Set<String>,
        nativeHdrSupported: (MediaStream) -> Boolean,
        baseLayerSupported: (MediaStream) -> Boolean,
    ): VideoPlaybackSupport {
        if (stream == null) return VideoPlaybackSupport(codecs)
        val range = rangeOf(stream)
        val wireRange = stream.videoRangeType.takeIf { it in wireRanges }
        val familyRanges = when (range) {
            VideoRange.HDR10 -> setOf("HDR10")
            VideoRange.HDR10_PLUS -> setOf("HDR10Plus")
            VideoRange.HLG -> setOf("HLG")
            VideoRange.DOLBY_VISION -> wireRanges.filter { it.startsWith("DOVI") }.toSet()
            else -> emptySet()
        }
        fun result(path: VideoPath, target: VideoRange, accepted: Boolean, reason: String? = null): VideoPlaybackSupport {
            val sourceRanges = if (accepted) wireRange?.let { setOf(it) } ?: familyRanges else emptySet()
            return VideoPlaybackSupport(codecs, range, target, path, setOf("SDR", "Unknown") + sourceRanges,
                listOfNotNull(reason))
        }
        if (stream.videoRangeType == "DOVIInvalid")
            return result(VideoPath.TRANSCODE, VideoRange.SDR, false, "片源 Dolby Vision 配置异常，交由服务器转换")
        if (range == VideoRange.UNKNOWN && stream.videoRange.equals("HDR", true))
            return result(VideoPath.TRANSCODE, VideoRange.SDR, false, "片源 HDR 规格不明确，交由服务器转换")
        if (range == VideoRange.SDR || range == VideoRange.UNKNOWN) {
            return if (stream.codec.lowercase() in codecs) result(VideoPath.NATIVE, range, true)
            else result(VideoPath.TRANSCODE, VideoRange.SDR, false, "当前设备不支持源视频规格")
        }
        if (range in display.hdrTypes && nativeHdrSupported(stream))
            return result(VideoPath.NATIVE, range, true,
                if (stream.dvProfile == 7) "Dolby Vision P7 增强层处理未经验证" else null)
        val baseRange = when {
            range == VideoRange.HDR10_PLUS -> VideoRange.HDR10
            range == VideoRange.DOLBY_VISION -> when {
                stream.dvBlSignalCompatibilityId == 1 || stream.dvProfile == 7 ->
                    if ((stream.hdr10PlusPresentFlag == true || stream.videoRangeType.endsWith("HDR10Plus")) &&
                        VideoRange.HDR10_PLUS in display.hdrTypes) VideoRange.HDR10_PLUS else VideoRange.HDR10
                stream.dvBlSignalCompatibilityId == 4 -> VideoRange.HLG
                stream.dvBlSignalCompatibilityId == 2 -> VideoRange.SDR
                else -> null
            }
            else -> null
        }
        if (baseRange != null && (baseRange == VideoRange.SDR || baseRange in display.hdrTypes)) {
            val base = stream.copy(dvProfile = null, dvLevel = null, videoRangeType = baseRange.name,
                hdr10PlusPresentFlag = baseRange == VideoRange.HDR10_PLUS,
                videoRange = if (baseRange == VideoRange.SDR) "SDR" else "HDR")
            if (baseLayerSupported(base))
                return result(VideoPath.BASE_LAYER, baseRange, true, "${range.label} 回退为 ${baseRange.label} 基础层")
        }
        return result(VideoPath.TRANSCODE, VideoRange.SDR, false,
            if (!display.known) "显示能力未知，请求服务器提供 SDR 视频" else "当前显示或解码链路不支持 ${range.label}，请求转换为 SDR")
    }

    fun rangeOf(stream: MediaStream): VideoRange {
        val range = stream.videoRangeType.uppercase(Locale.ROOT)
        return when {
            stream.dvProfile != null || range.startsWith("DOVI") -> VideoRange.DOLBY_VISION
            stream.hdr10PlusPresentFlag == true || range == "HDR10PLUS" -> VideoRange.HDR10_PLUS
            range == "HLG" || stream.colorTransfer.equals("arib-std-b67", true) -> VideoRange.HLG
            range == "HDR10" || stream.colorTransfer.equals("smpte2084", true) -> VideoRange.HDR10
            range == "SDR" || stream.videoRange.equals("SDR", true) -> VideoRange.SDR
            else -> VideoRange.UNKNOWN
        }
    }

    private val wireRanges = setOf("Unknown", "SDR", "HDR10", "HLG", "HDR10Plus", "DOVI", "DOVIWithHDR10",
        "DOVIWithHLG", "DOVIWithSDR", "DOVIWithEL", "DOVIWithHDR10Plus", "DOVIWithELHDR10Plus")

}
