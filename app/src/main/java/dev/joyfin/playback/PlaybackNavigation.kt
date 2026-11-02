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
    val markFeedback: String? = null,
)

/** Player marks override automatic ranges; chapter fallback only recognizes explicit intro/outro names. */
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
        val prefs = preferences.validated()
        val markedIntro = manual && prefs.introEndMs?.let { it < durationMs } == true
        val markedOutro = manual && prefs.outroDurationMs?.let { it < durationMs } == true
        val server = segments.mapNotNull {
            val label = when (it.type) { "Intro" -> "片头"; "Outro" -> "片尾"; else -> return@mapNotNull null }
            if (label == "片头" && markedIntro || label == "片尾" && markedOutro) return@mapNotNull null
            val start = it.startTicks / 10_000
            val end = it.endTicks / 10_000
            if (it.startTicks < 0 || end <= start || end > durationMs) null else SkipRange(start, end, label)
        }.sortedBy { it.startMs }.distinct()
        val result = server.toMutableList()
        if (manual && server.none { it.label == "片头" } && prefs.introDurationMs > 0 && prefs.introDurationMs < durationMs)
            result += SkipRange(0, prefs.introDurationMs, "片头")
        if (manual && server.none { it.label == "片尾" } && prefs.effectiveOutroDurationMs > 0 && prefs.effectiveOutroDurationMs < durationMs)
            result += SkipRange(durationMs - prefs.effectiveOutroDurationMs, durationMs, "片尾")
        // Fall back to explicitly named chapters only when the server has no matching segment
        // and the user has not configured a manual duration.
        if (server.none { it.label == "片头" } && !(manual && prefs.introDurationMs > 0))
            chapterRange(chapters, durationMs, introNames, "片头")?.let { result += it }
        if (server.none { it.label == "片尾" } && !(manual && prefs.effectiveOutroDurationMs > 0))
            chapterRange(chapters, durationMs, outroNames, "片尾")?.let { result += it }
        // Keep a deliberate player mark when it conflicts with lower-priority server/chapter data.
        // Conflicts between two manual marks, or between automatic ranges, remain unsafe to skip.
        fun marked(range: SkipRange) = if (range.label == "片头") markedIntro else markedOutro
        return result.filter { range ->
            result.none { other ->
                other != range && other.startMs < range.endMs && range.startMs < other.endMs &&
                    (!marked(range) || marked(other))
            }
        }.sortedBy { it.startMs }
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
    private val automaticSuppressed = mutableSetOf<String>()
    private var feedbackUntil = 0L
    private var undoUntil = 0L
    private var undoRange: SkipRange? = null
    private var chapterDuration = -1L
    private fun update(value: PlaybackNavigationState) { current = value; publish(value) }

    fun clear() {
        ticket++; job?.cancel(); media = null; server = emptyList(); suppressed.clear(); undoRange = null
        automaticSuppressed.clear()
        feedbackUntil = 0
        chapterDuration = -1
        update(PlaybackNavigationState())
    }
    fun open(value: Media, sourceId: String) {
        clear()
        media = value
        val available = value.sources.size <= 1 || value.sources.firstOrNull()?.id == sourceId
        update(current.copy(preferences = loadPreferences(value),
            canConfigure = value.type == "Episode" || value.type == "Movie", timelineAvailable = available))
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
                    else update(current.copy(loading = false, error = "暂时无法读取片头片尾标记，可重试或在控制栏手动标记"))
                }
            }
        }
    }
    fun preferences(value: SkipPreferences) {
        val item = media ?: return
        if (!current.canConfigure) return
        savePreferences(item, value.validated())
        suppressed.clear()
        automaticSuppressed.clear()
        undoRange = null
        update(current.copy(preferences = value.validated(), offer = null, undoPositionMs = null, markFeedback = null))
    }
    fun markIntroEnd(position: Long, duration: Long, now: Long) = mark(position, duration, now, intro = true)
    fun markOutroStart(position: Long, duration: Long, now: Long) = mark(position, duration, now, intro = false)

    private fun mark(position: Long, duration: Long, now: Long, intro: Boolean) {
        if (!current.canConfigure) return
        fun feedback(text: String) {
            feedbackUntil = now + 2500
            update(current.copy(markFeedback = text))
        }
        if (duration <= 0 || position <= 0 || position >= duration) {
            feedback("请先定位到视频开头与结尾之间的有效播放位置")
            return
        }
        val prefs = current.preferences
        val next = if (intro) prefs.copy(introSeconds = 0, introEndMs = position)
            else prefs.copy(outroSeconds = 0, outroDurationMs = duration - position)
        // A saved intro and outro must leave some main content between them.
        if (next.introDurationMs >= duration - next.effectiveOutroDurationMs) {
            feedback(if (intro) "片头结束不能晚于片尾开始，请先清除原片尾标记"
                else "片尾开始不能早于片头结束，请先清除原片头标记")
            return
        }
        preferences(next)
        // Marking only records the point. Keep the skip offer, but do not auto-skip this new range.
        automaticSuppressed += if (intro) "片头" else "片尾"
        feedback(if (intro) "已保存片头结束点" else "已保存片尾开始点")
    }
    fun tick(position: Long, duration: Long, automaticAllowed: Boolean, now: Long): Long? {
        val item = media ?: return null
        val chapters = if (!current.timelineAvailable) emptyList() else if (chapterDuration == duration) current.chapters
        else PlaybackNavigationPolicy.chapters(item, duration).map { chapter ->
            val tag = item.chapters.getOrNull(chapter.index)?.imageTag.orEmpty()
            chapter.copy(imageUrl = tag.takeIf { it.isNotBlank() }?.let { chapterImage(item, chapter.index, it) })
        }.also { chapterDuration = duration }
        // Explicit marks work immediately; old duration fallbacks still wait for server metadata.
        val preferences = if (current.loading) current.preferences.copy(introSeconds = 0, outroSeconds = 0)
            else current.preferences
        val ranges = PlaybackNavigationPolicy.ranges(server, duration, preferences,
            current.canConfigure, item.chapters.takeIf { current.timelineAvailable }.orEmpty())
        val range = ranges.firstOrNull { position >= it.startMs && position < it.endMs && it.key !in suppressed }
        val undo = current.undoPositionMs?.takeIf { now <= undoUntil }
        update(current.copy(chapters = chapters, offer = range, undoPositionMs = undo,
            markFeedback = current.markFeedback?.takeIf { now <= feedbackUntil },
            serverMarks = PlaybackNavigationPolicy.ranges(server, duration, SkipPreferences(), false).size))
        if (range != null && current.preferences.automatic && automaticAllowed && range.label !in automaticSuppressed)
            return skip(position, now)
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
