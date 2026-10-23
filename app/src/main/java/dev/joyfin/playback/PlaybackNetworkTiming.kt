package dev.joyfin.playback

import java.util.Locale

enum class HttpReadState(val label: String) {
    OPENING("打开中"), READING("读取中"), EOF("读到末尾"), CLOSED("已关闭"),
    FAILED("失败"), CANCELED("中止"),
}

enum class HttpRangeResult(val label: String) {
    NONE("未请求分段"), MATCHED("分段起点匹配"), IGNORED("分段请求返回整段 200"),
    MISMATCHED("分段起点不匹配"), UNKNOWN("分段响应待确认"),
}

enum class HttpReadPurpose(val label: String) {
    MEDIA("媒体"), SUBTITLE("独立字幕"), STREAM_RESOURCE("串流资源"), OTHER("其他"),
}

/** Compare configured identities only. The result is an enum; identities never enter a report. */
internal fun <T> httpReadPurpose(uri: T, media: T?, subtitles: Set<T>, streaming: Boolean): HttpReadPurpose = when {
    uri == media -> HttpReadPurpose.MEDIA
    uri in subtitles -> HttpReadPurpose.SUBTITLE
    streaming -> HttpReadPurpose.STREAM_RESOURCE
    else -> HttpReadPurpose.OTHER
}

/** Numeric, allowlisted facts only. No request, URI, header map or exception is retained. */
data class HttpReadTiming(
    val sequence: Int,
    val generation: Long,
    val elapsedMs: Long,
    val position: Long,
    val requestedLength: Long,
    val purpose: HttpReadPurpose = HttpReadPurpose.OTHER,
    val state: HttpReadState = HttpReadState.OPENING,
    val httpStatus: Int? = null,
    val range: HttpRangeResult = HttpRangeResult.UNKNOWN,
    val durationMs: Long = 0,
    val openMs: Long? = null,
    val firstByteMs: Long? = null,
    val bytes: Long = 0,
    val readMs: Long = 0,
    val maxReadMs: Long = 0,
    val pendingReadMs: Long = 0,
    val longestReadGapMs: Long = 0,
    val failure: DiagnosticFailure? = null,
)

data class BandwidthTiming(val generation: Long, val elapsedMs: Long, val loadMs: Int, val bytes: Long, val estimate: Long)
data class PlaybackNetworkSnapshot(
    val requests: List<HttpReadTiming> = emptyList(),
    val samples: List<BandwidthTiming> = emptyList(),
)

/** One request, updated on the loader thread and sampled at most once per UI sampling tick. */
internal class HttpReadMeter(private val initial: HttpReadTiming, private val startMs: Long) {
    private var value = initial
    private var readStart: Long? = null
    private var lastReadEnd: Long? = null
    private var endMs: Long? = null

    private val active: Boolean get() = value.state == HttpReadState.OPENING || value.state == HttpReadState.READING

    @Synchronized fun opened(nowMs: Long, status: Int, range: HttpRangeResult) {
        if (!active) return
        value = value.copy(state = HttpReadState.READING, openMs = elapsed(nowMs),
            httpStatus = status.takeIf { it in 100..599 }, range = range)
        lastReadEnd = nowMs
    }

    @Synchronized fun readStarted(nowMs: Long) {
        if (!active) return
        val gap = lastReadEnd?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
        value = value.copy(longestReadGapMs = maxOf(value.longestReadGapMs, gap))
        readStart = nowMs
    }

    @Synchronized fun readFinished(nowMs: Long, bytes: Int) {
        if (!active) return
        val duration = readStart?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
        value = value.copy(bytes = value.bytes + bytes.coerceAtLeast(0), readMs = value.readMs + duration,
            maxReadMs = maxOf(value.maxReadMs, duration),
            firstByteMs = value.firstByteMs ?: elapsed(nowMs).takeIf { bytes > 0 })
        readStart = null; lastReadEnd = nowMs
        if (bytes == -1) finish(nowMs, HttpReadState.EOF)
    }

    @Synchronized fun fail(nowMs: Long, failure: DiagnosticFailure) {
        if (!active) return
        val duration = readStart?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
        value = value.copy(readMs = value.readMs + duration, maxReadMs = maxOf(value.maxReadMs, duration),
            openMs = value.openMs ?: elapsed(nowMs), failure = failure, httpStatus = failure.httpStatus ?: value.httpStatus)
        readStart = null
        finish(nowMs, HttpReadState.FAILED)
    }

    @Synchronized fun finish(nowMs: Long, state: HttpReadState) {
        if (!active) return
        // Freeze the request before its owner is replaced. Late callbacks become harmless.
        val snapshot = snapshot(nowMs)
        value = snapshot.copy(state = state, readMs = snapshot.readMs + snapshot.pendingReadMs,
            maxReadMs = maxOf(snapshot.maxReadMs, snapshot.pendingReadMs), pendingReadMs = 0)
        readStart = null; endMs = nowMs
    }

    @Synchronized fun snapshot(nowMs: Long): HttpReadTiming {
        val pending = readStart?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
        val gap = if (active && readStart == null) lastReadEnd?.let { (nowMs - it).coerceAtLeast(0) } ?: 0 else 0
        return value.copy(durationMs = elapsed(endMs ?: nowMs), pendingReadMs = pending,
            longestReadGapMs = maxOf(value.longestReadGapMs, gap))
    }

    private fun elapsed(nowMs: Long) = (nowMs - startMs).coerceAtLeast(0)
}

/** Bounded in-memory history, bound to the playback generation, independent of Media3 and Android. */
internal class PlaybackNetworkTracker(private val originMs: Long) {
    private var generation = -1L
    private var sequence = 0
    private var requests = emptyList<HttpReadMeter>()
    private var samples = emptyList<BandwidthTiming>()

    @Synchronized fun begin(owner: Long, nowMs: Long) { stop(nowMs); generation = owner }
    @Synchronized fun stop(nowMs: Long) {
        requests.forEach { it.finish(nowMs, HttpReadState.CANCELED) }
        generation = -1
    }
    @Synchronized fun open(owner: Long, nowMs: Long, position: Long, length: Long,
        purpose: HttpReadPurpose = HttpReadPurpose.OTHER): HttpReadMeter? {
        if (generation != owner) return null
        val meter = HttpReadMeter(HttpReadTiming(++sequence, owner, (nowMs - originMs).coerceAtLeast(0),
            position.coerceAtLeast(0), length.takeIf { it >= 0 } ?: -1, purpose), nowMs)
        requests = (requests + meter).takeLast(20)
        return meter
    }
    @Synchronized fun bandwidth(nowMs: Long, loadMs: Int, bytes: Long, estimate: Long) {
        if (generation < 0 || loadMs <= 0 || bytes <= 0) return
        samples = (samples + BandwidthTiming(generation, (nowMs - originMs).coerceAtLeast(0), loadMs, bytes,
            estimate.coerceAtLeast(0))).takeLast(10)
    }
    @Synchronized fun snapshot(nowMs: Long) = PlaybackNetworkSnapshot(requests.map { it.snapshot(nowMs) }, samples)
}

internal fun httpRangeResult(position: Long, length: Long, status: Int, contentRange: String?): HttpRangeResult {
    if (position == 0L && length < 0) return HttpRangeResult.NONE
    if (status == 200) return HttpRangeResult.IGNORED
    if (status != 206 || contentRange == null || contentRange.length > 128) return HttpRangeResult.UNKNOWN
    val match = Regex("bytes ([0-9]+)-([0-9]+)/(?:[0-9]+|\\*)", RegexOption.IGNORE_CASE)
        .matchEntire(contentRange.trim()) ?: return HttpRangeResult.UNKNOWN
    val start = match.groupValues[1].toLongOrNull() ?: return HttpRangeResult.UNKNOWN
    val end = match.groupValues[2].toLongOrNull() ?: return HttpRangeResult.UNKNOWN
    if (end < start) return HttpRangeResult.UNKNOWN
    return if (start == position) HttpRangeResult.MATCHED else HttpRangeResult.MISMATCHED
}

private fun diagnosticBytes(bytes: Long) = String.format(Locale.ROOT, "%.2f MiB", bytes / 1048576.0)

fun PlaybackNetworkSnapshot.reportLines(): List<String> = buildList {
    add("HTTP 读取记录（含媒体、字幕及清单；最近 20 次打开）：")
    if (requests.isEmpty()) add("尚无 HTTP 读取记录")
    requests.forEach { request ->
        add("+${diagnosticElapsed(request.elapsedMs)} HTTP #${request.sequence} · ${request.purpose.label} · 加载标识 ${request.generation} · 字节起点 ${request.position} · 请求长度 ${request.requestedLength.takeIf { it >= 0 }?.toString() ?: "至末尾"} · ${request.state.label}")
        add("响应 ${request.httpStatus?.toString() ?: "未知"} · ${request.range.label} · 打开 ${request.openMs?.let(::diagnosticElapsed) ?: "等待中"} · 首个读入字节 ${request.firstByteMs?.let(::diagnosticElapsed) ?: "尚未读到"} · 已读 ${diagnosticBytes(request.bytes)}")
        add("总历时 ${diagnosticElapsed(request.durationMs)} · 累计 read ${diagnosticElapsed(request.readMs)} · 最长单次 read ${diagnosticElapsed(request.maxReadMs)} · 正在 read ${diagnosticElapsed(request.pendingReadMs)} · 最长读取间隔 ${diagnosticElapsed(request.longestReadGapMs)}")
        request.failure?.let { add("读取错误：${it.description}") }
    }
    add("带宽原始样本（最近 10 条）：")
    if (samples.isEmpty()) add("尚无有效传输样本；带宽估算可能来自初始值或先前传输")
    samples.forEach {
        add("+${diagnosticElapsed(it.elapsedMs)} 加载标识 ${it.generation} · 样本 ${diagnosticBytes(it.bytes)} / ${diagnosticElapsed(it.loadMs.toLong())} · 估算 ${diagnosticBitrate(it.estimate)}")
    }
    add("打开耗时含连接、响应及可能的客户端跳过；首字节指本次打开至首次 read 返回数据，非服务器 TTFB。读取间隔含解析、暂停和播放器主动限流；累计 read 耗时不是持续网速。")
}
