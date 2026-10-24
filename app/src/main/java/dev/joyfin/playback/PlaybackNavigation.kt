package dev.joyfin.playback

import dev.joyfin.data.*
import kotlinx.coroutines.*

data class ChapterChoice(
    val positionMs: Long,
    val title: String,
    val index: Int = 0,
    val imageUrl: String? = null,
)
data class SkipRange(val startMs: Long, val endMs: Long, val label: String) {
    val key: String get() = "$startMs:$endMs:$label"
}
data class PlaybackNavigationState(
    val chapters: List<ChapterChoice> = emptyList(),
    val preferences: SkipPreferences = SkipPreferences(),
    val offer: SkipRange? = null,
    val undoPositionMs: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val canConfigure: Boolean = false,
    val timelineAvailable: Boolean = true,
    val serverMarks: Int = 0,
)

/** Only explicit server ranges are interpreted. Chapter titles are never guessed as intro/outro. */
internal object PlaybackNavigationPolicy {
    fun chapters(media: Media, durationMs: Long): List<ChapterChoice> = media.chapters
        .filter { it.ticks >= 0 && it.ticks / 10_000 < durationMs }
        .sortedBy { it.ticks }.distinctBy { it.ticks / 10_000 }
        .mapIndexed { index, chapter ->
            ChapterChoice(chapter.ticks / 10_000, chapter.name.ifBlank { "第 ${index + 1} 章" }, index)
        }

    fun ranges(
        segments: List<MediaSegment>,
        durationMs: Long,
        preferences: SkipPreferences,
        manual: Boolean,
        chapters: List<MediaChapter> = emptyList(),
    ): List<SkipRange> {
        if (durationMs <= 0) return emptyList()
        val server = segments.mapNotNull {
            val label = when (it.type) { "Intro" -> "片头"; "Outro" -> "片尾"; else -> return@mapNotNull null }
            val start = it.startTicks / 10_000
            val end = it.endTicks / 10_000
            if (it.startTicks < 0 || end <= start || end > durationMs) null else SkipRange(start, end, label)
        }.sortedBy { it.startMs }.distinct()
        val result = server.toMutableList()
        val prefs = preferences.validated()
        if (manual && server.none { it.label == "片头" } && prefs.introSeconds > 0 && prefs.introSeconds * 1000L < durationMs)
            result += SkipRange(0, prefs.introSeconds * 1000L, "片头")
        if (manual && server.none { it.label == "片尾" } && prefs.outroSeconds > 0 && prefs.outroSeconds * 1000L < durationMs)
            result += SkipRange(durationMs - prefs.outroSeconds * 1000L, durationMs, "片尾")
        // Fall back to explicitly named chapters only when the server has no matching segment
        // and the user has not configured a manual duration.
        if (server.none { it.label == "片头" } && !(manual && prefs.introSeconds > 0))
            chapterRange(chapters, durationMs, introNames, "片头")?.let { result += it }
        if (server.none { it.label == "片尾" } && !(manual && prefs.outroSeconds > 0))
            chapterRange(chapters, durationMs, outroNames, "片尾")?.let { result += it }
        // Conflicting ranges are unsafe to apply automatically or offer as a single skip.
        return result.filter { range -> result.none { other -> other != range && other.startMs < range.endMs && range.startMs < other.endMs } }
            .sortedBy { it.startMs }
    }

    private fun chapterRange(
        chapters: List<MediaChapter>,
        durationMs: Long,
        names: Set<String>,
        label: String,
    ): SkipRange? {
        val ordered = chapters.sortedBy { it.ticks }
        val index = ordered.indexOfFirst { chapter ->
            val name = chapter.name.trim().lowercase()
            names.any { name == it || name.startsWith("$it ") }
        }
        if (index < 0) return null
        val start = ordered[index].ticks / 10_000
        val end = ordered.getOrNull(index + 1)?.ticks?.div(10_000) ?: durationMs
        if (start < 0 || end <= start || end > durationMs) return null
        return SkipRange(start, end, label)
    }

    private val introNames = setOf("intro", "opening", "opening credits", "op", "open", "main titles", "片头", "片头曲")
    private val outroNames = setOf("outro", "ending", "end credits", "closing credits", "credits", "ed", "end", "片尾", "片尾曲")
}

/** Request ownership, persistence and skip gating; it cannot call or recreate the player. */
internal class PlaybackNavigation(
    private val scope: CoroutineScope,
    private val loadPreferences: (Media) -> SkipPreferences,
    private val savePreferences: (Media, SkipPreferences) -> Unit,
    private val segments: suspend (String) -> List<MediaSegment>,
    private val publish: (PlaybackNavigationState) -> Unit,
    private val expired: () -> Unit,
    private val chapterImage: (Media, Int, String) -> String? = { _, _, _ -> null },
) {
    private var ticket = 0L
    private var job: Job? = null
    private var media: Media? = null
    private var current = PlaybackNavigationState()
    private var server = emptyList<MediaSegment>()
    private val suppressed = mutableSetOf<String>()
    private var undoUntil = 0L
    private var undoRange: SkipRange? = null
    private var chapterDuration = -1L
    private fun update(value: PlaybackNavigationState) { current = value; publish(value) }

    fun clear() {
        ticket++; job?.cancel(); media = null; server = emptyList(); suppressed.clear(); undoRange = null
        chapterDuration = -1
        update(PlaybackNavigationState())
    }
    fun open(value: Media, sourceId: String) {
        clear()
        media = value
        val available = value.sources.size <= 1 || value.sources.firstOrNull()?.id == sourceId
        update(current.copy(preferences = loadPreferences(value),
            canConfigure = value.type == "Episode", timelineAvailable = available))
        if (available) retry()
    }
    fun retry() {
        val value = media ?: return
        if (!current.timelineAvailable) return
        val owner = ++ticket
        job?.cancel()
        update(current.copy(loading = true, error = null))
        job = scope.launch {
            try {
                val result = segments(value.id)
                ensureActive()
                if (owner != ticket) return@launch
                server = result
                update(current.copy(loading = false))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (owner == ticket) {
                    if (e is ApiException && e.status == 401) expired()
                    else update(current.copy(loading = false, error = "暂时无法读取片头片尾标记，可重试或使用本剧时长设置"))
                }
            }
        }
    }
    fun preferences(value: SkipPreferences) {
        val item = media ?: return
        if (!current.canConfigure) return
        savePreferences(item, value.validated())
        suppressed.clear()
        update(current.copy(preferences = value.validated(), offer = null))
    }
    fun tick(position: Long, duration: Long, automaticAllowed: Boolean, now: Long): Long? {
        val item = media ?: return null
        val chapters = if (!current.timelineAvailable) emptyList() else if (chapterDuration == duration) current.chapters
        else PlaybackNavigationPolicy.chapters(item, duration).map { chapter ->
            val tag = item.chapters.getOrNull(chapter.index)?.imageTag.orEmpty()
            chapter.copy(imageUrl = tag.takeIf { it.isNotBlank() }?.let { chapterImage(item, chapter.index, it) })
        }.also { chapterDuration = duration }
        val ranges = PlaybackNavigationPolicy.ranges(server, duration, current.preferences,
            current.canConfigure && !current.loading, item.chapters)
        val range = ranges.firstOrNull { position >= it.startMs && position < it.endMs && it.key !in suppressed }
        val undo = current.undoPositionMs?.takeIf { now <= undoUntil }
        update(current.copy(chapters = chapters, offer = range, undoPositionMs = undo,
            serverMarks = PlaybackNavigationPolicy.ranges(server, duration, SkipPreferences(), false).size))
        if (range != null && current.preferences.automatic && automaticAllowed) return skip(position, now)
        return null
    }
    fun skip(position: Long, now: Long): Long? {
        val range = current.offer ?: return null
        if (position !in range.startMs until range.endMs) return null
        suppressed += range.key
        undoRange = range
        undoUntil = now + 8000
        update(current.copy(offer = null, undoPositionMs = position))
        return range.endMs
    }
    fun dismiss() { current.offer?.let { suppressed += it.key }; update(current.copy(offer = null)) }
    fun undo(now: Long): Long? {
        val position = current.undoPositionMs?.takeIf { now <= undoUntil } ?: return null
        undoRange?.let { suppressed += it.key }
        update(current.copy(offer = null, undoPositionMs = null))
        return position
    }
}
