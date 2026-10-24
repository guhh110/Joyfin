package dev.joyfin.data

import java.util.Locale

/** Runtime label for pills: seconds under a minute, otherwise whole minutes. */
fun runtimeLabel(ticks: Long): String? {
    if (ticks <= 0) return null
    val seconds = ticks / 10_000_000
    return if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分钟"
}

/** Adaptive binary size so small files never render as "0.0 GiB". */
fun fileSizeLabel(bytes: Long): String? {
    if (bytes <= 0) return null
    val mib = bytes / 1_048_576.0
    return if (mib < 1024) String.format(Locale.ROOT, "%.1f MiB", mib)
    else String.format(Locale.ROOT, "%.1f GiB", mib / 1024)
}
