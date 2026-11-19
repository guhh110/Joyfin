package dev.joyfin.ui.features.home.tv

import dev.joyfin.data.Media
import dev.joyfin.data.runtimeLabel
import java.util.Locale
import kotlin.math.ceil

internal fun tvHomeCardMetadata(item: Media): String {
    if (item.type !in setOf("Movie", "Episode", "Series")) return ""
    val episode = if (item.type == "Episode" && item.episode > 0) {
        if (item.season > 0) "S${item.season} E${item.episode}" else "第 ${item.episode} 集"
    } else null
    val duration = if (item.type in setOf("Movie", "Episode")) {
        if (!item.played && item.resumeTicks > 0 && item.ticks > item.resumeTicks)
            "剩余 ${ceil((item.ticks - item.resumeTicks) / 600_000_000.0).toLong()} 分钟"
        else runtimeLabel(item.ticks)
    } else null
    return listOfNotNull(
        episode,
        item.year.takeIf { it > 0 && item.type != "Episode" }?.toString(),
        item.rating?.takeIf { it.isFinite() && it > 0 && it <= 10 && item.type != "Episode" }
            ?.let { "${"%.1f".format(Locale.ROOT, it)} 分" },
        duration,
    ).joinToString(" · ")
}
