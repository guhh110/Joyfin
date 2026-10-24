package dev.joyfin.playback.quality

/** Player notices use plain language; the diagnostics sheet keeps the technical detail. */
internal fun friendlyPlaybackReasons(reasons: List<String>): List<String> =
    reasons.map(::friendlyReason).distinct()

private fun friendlyReason(reason: String): String {
    friendly[reason]?.let { return it }
    if (reason.startsWith("当前显示或解码链路不支持")) {
        return reason
            .replace("当前显示或解码链路不支持", "这台设备不支持")
            .replace("，请求转换为 SDR", "，服务器正在转换为 SDR")
    }
    if (reason.startsWith("片源") && reason.endsWith("基础层")) {
        return reason.replace("回退为", "将使用").replace("基础层", "基础层播放")
    }
    return reason
}

private val friendly = mapOf(
    "原编码输出不可用，使用本机 PCM 解码" to "这个音轨设备不能直接解码，已改用本机解码",
    "使用 PCM 兼容输出" to "已切换为兼容音频输出",
    "倍速播放使用 PCM 兼容输出" to "倍速播放需要本机解码音频",
    "当前音轨需由服务器转换，优先保留原视频" to "音轨需要服务器转换，视频保持原画",
    "服务器转换音轨" to "服务器正在转换音轨",
    "使用已选择的兼容转码模式" to "已按兼容模式转码",
    "视频播放失败，使用服务器兼容转换" to "视频直接播放失败，服务器正在兼容转码",
    "片源包含对象音效，普通 PCM 输出不作对象音效保留承诺" to "片源带杜比全景声/DTS:X，当前输出不保证保留对象音效",
    "原盘使用服务器主影片流，不提供客户端菜单导航" to "原盘由服务器播放主影片，客户端不提供菜单",
    "当前设备不支持源视频规格" to "设备不支持这个视频规格，服务器正在转码",
    "片源 Dolby Vision 配置异常，交由服务器转换" to "这个 Dolby Vision 片源配置异常，服务器正在转码",
    "片源 HDR 规格不明确，交由服务器转换" to "这个 HDR 片源规格不明确，服务器正在转码",
    "显示能力未知，请求服务器提供 SDR 视频" to "暂时无法确认屏幕能力，服务器将提供 SDR 视频",
)
