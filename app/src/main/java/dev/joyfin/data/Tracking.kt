package dev.joyfin.data

enum class TrackingStatus(val label: String) {
    WATCHING("观看中"), WAITING("待看"), CAUGHT_UP("已追平"), UNAVAILABLE("暂无可播放剧集"),
}

data class TrackingProgress(
    val status: TrackingStatus,
    val target: Media? = null,
    val unplayed: Int = 0,
    val lastPlayedAt: Long? = null,
)

data class TrackingEntry(
    val series: Media,
    val progress: TrackingProgress? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Follow recent activity, without turning a replay recommendation into a new episode. */
fun trackingProgress(episodes: List<Media>): TrackingProgress {
    val available = episodes.filter { !it.virtual && it.type == "Episode" }.distinctBy { it.id }
    val order = compareBy<Media> { it.season }.thenBy { it.episode }.thenBy { it.id }
    val numbered = available.filter { it.season > 0 }
    val ordered = numbered.ifEmpty { available }.sortedWith(order)
    if (ordered.isEmpty()) return TrackingProgress(TrackingStatus.UNAVAILABLE)
    fun Media.finished() = played || (ticks > 0 && resumeTicks >= ticks)
    val unplayed = ordered.count { !it.finished() }
    val latest = ordered.filter { it.finished() || it.resumeTicks > 0 || it.lastPlayedAt != null }
        .maxWithOrNull(compareBy<Media> { it.lastPlayedAt ?: Long.MIN_VALUE }.then(order))
    if (latest == null) return TrackingProgress(TrackingStatus.WAITING, ordered.first(), unplayed)
    val next = if (!latest.finished()) latest else ordered.firstOrNull {
        order.compare(it, latest) > 0 && !it.finished()
    }
    return TrackingProgress(
        if (next == null) TrackingStatus.CAUGHT_UP else TrackingStatus.WATCHING,
        next, unplayed, latest.lastPlayedAt,
    )
}

val trackingOrder: Comparator<TrackingEntry> = compareBy<TrackingEntry> {
    it.progress?.status?.ordinal ?: if ((it.series.unplayedCount ?: 1) > 0) 1 else 2
}.thenByDescending { it.progress?.lastPlayedAt ?: it.series.lastPlayedAt ?: Long.MIN_VALUE }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.series.name }.thenBy { it.series.id }

fun TrackingEntry.playLabel(): String = progress?.target?.let {
    if (progress.status == TrackingStatus.WAITING) "开始观看" else "继续 S${it.season} E${it.episode}"
} ?: "查看剧集"

fun TrackingEntry.statusLabel(): String {
    val value = progress ?: return if (error != null) "进度读取失败" else "正在读取观看进度"
    return when (value.status) {
        TrackingStatus.CAUGHT_UP -> if (value.unplayed > 0) "已追平 · 此前还有 ${value.unplayed} 集未看" else "已追平，等待更新"
        TrackingStatus.UNAVAILABLE -> value.status.label
        else -> "${playLabel()} · 还有 ${value.unplayed} 集未看"
    }
}
