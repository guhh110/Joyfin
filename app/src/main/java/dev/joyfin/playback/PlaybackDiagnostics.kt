@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import android.os.Build
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import dev.joyfin.BuildConfig
import dev.joyfin.data.ApiException
import dev.joyfin.data.MediaSource
import dev.joyfin.data.MediaStream
import dev.joyfin.data.PlaybackPlan
import dev.joyfin.data.audioStream
import dev.joyfin.playback.quality.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only allowlisted facts enter a report; URLs, exception messages and media names never do. */
enum class DiagnosticStage(val label: String) {
    METADATA("读取媒体信息"), NEGOTIATION("协商播放方式"), STREAM("加载与播放媒体"), SUBTITLE("加载字幕"),
}
enum class DiagnosticOutcome(val label: String) {
    RUNNING("进行中"), READY("成功"), FAILED("失败"), WARNING("可恢复错误"), FALLBACK("自动回退"), CANCELED("已取消"),
}
enum class DiagnosticPlayerState(val label: String) {
    PREPARING("准备播放"), BUFFERING("缓冲中"), PLAYING("播放中"), PAUSED("已暂停"), FAILED("播放失败"), STOPPED("已停止"),
}
data class DiagnosticFormat(
    val codec: String = "未知",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val channels: Int = 0,
    val sampleRate: Int = 0,
    val bitrate: Long = 0,
    val range: String? = null,
) {
    val description: String get() = listOfNotNull(codec,
        if (width > 0 && height > 0) "${width}×${height}" else null,
        frameRate.takeIf { it.isFinite() && it > 0 }?.let { "${String.format(Locale.ROOT, "%.2f", it)} fps" },
        channels.takeIf { it > 0 }?.let { "$it 声道" },
        sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
        bitrate.takeIf { it > 0 }?.let(::diagnosticBitrate), range).joinToString(" · ")
}
data class DiagnosticFailure(val category: String, val httpStatus: Int? = null, val playerCode: String? = null) {
    val description: String get() = listOfNotNull(category, httpStatus?.let { "HTTP $it" }, playerCode).joinToString(" · ")
}
data class DiagnosticEvent(val elapsedMs: Long, val stage: DiagnosticStage, val outcome: DiagnosticOutcome,
    val failure: DiagnosticFailure? = null)
data class PlaybackDiagnosticSnapshot(
    val id: Long = 0,
    val state: DiagnosticPlayerState = DiagnosticPlayerState.STOPPED,
    val stage: DiagnosticStage = DiagnosticStage.METADATA,
    val method: String = "尚未协商",
    val selection: String = "自动",
    val quality: Int = 0,
    val container: String = "未知",
    val sourceVideo: DiagnosticFormat? = null,
    val sourceAudio: DiagnosticFormat? = null,
    val sourceBitrate: Long = 0,
    val outputVideo: DiagnosticFormat? = null,
    val outputAudio: DiagnosticFormat? = null,
    val videoDecoder: String = "尚未初始化",
    val audioDecoder: String = "尚未初始化",
    val bandwidth: Long = 0,
    val positionMs: Long = 0,
    val bufferMs: Long = 0,
    val reasons: List<String> = emptyList(),
    val failure: DiagnosticFailure? = null,
    val events: List<DiagnosticEvent> = emptyList(),
    val decision: PlaybackDecision? = null,
    val displayCapabilities: String = "未知",
    val actualOutput: PlaybackOutputSnapshot? = null,
    val sourceSpatial: String = "未提供",
    val timing: PlaybackTimingSnapshot = PlaybackTimingSnapshot(),
)

/** Process-local last playback only. Tokens isolate canceled requests and previous players. */
object PlaybackDiagnostics {
    private val mutable = MutableStateFlow(PlaybackDiagnosticSnapshot())
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var target = ""
    private var pendingPlayer = false
    private var startedAt = 0L
    private var timing = PlaybackTimingTracker(0)
    private fun nowMs() = System.nanoTime() / 1_000_000

    @Synchronized fun begin(itemId: String, preflight: Boolean = false): Long {
        target = itemId
        pendingPlayer = preflight
        startedAt = System.nanoTime()
        timing = PlaybackTimingTracker(nowMs())
        val id = ++sequence
        mutable.value = PlaybackDiagnosticSnapshot(id = id, state = DiagnosticPlayerState.PREPARING)
        event(id, DiagnosticStage.METADATA, DiagnosticOutcome.RUNNING)
        return id
    }
    @Synchronized fun claim(itemId: String): Long {
        if (pendingPlayer && target == itemId) { pendingPlayer = false; return mutable.value.id }
        return begin(itemId)
    }
    @Synchronized fun clear() {
        sequence++
        target = ""; pendingPlayer = false
        mutable.value = PlaybackDiagnosticSnapshot()
        timing = PlaybackTimingTracker(nowMs())
    }
    @Synchronized private fun update(id: Long, transform: (PlaybackDiagnosticSnapshot) -> PlaybackDiagnosticSnapshot) {
        if (id != 0L && mutable.value.id == id) mutable.value = transform(mutable.value)
    }
    fun event(id: Long, stage: DiagnosticStage, outcome: DiagnosticOutcome, failure: DiagnosticFailure? = null) {
        update(id) { ui ->
            val last = ui.events.lastOrNull()
            val repeated = last?.stage == stage && last.outcome == outcome && last.failure == failure
            ui.copy(stage = stage, events = if (repeated) ui.events else (ui.events + DiagnosticEvent(
                ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0), stage, outcome, failure)).takeLast(30))
        }
    }
    fun preparing(id: Long, quality: Int, compatible: Boolean, audioOnly: Boolean, automaticFallback: Boolean = false) = update(id) {
        it.copy(state = DiagnosticPlayerState.PREPARING, quality = quality, failure = null,
            selection = selectionLabel(compatible, audioOnly, automaticFallback),
            method = "尚未协商", container = "未知", sourceVideo = null, sourceAudio = null,
            sourceBitrate = 0, reasons = emptyList(),
            outputVideo = null, outputAudio = null, bandwidth = 0, actualOutput = null, decision = null,
            displayCapabilities = "未知", sourceSpatial = "未提供",
            videoDecoder = "尚未初始化", audioDecoder = "尚未初始化")
    }
    fun selection(id: Long, compatible: Boolean, audioOnly: Boolean, automaticFallback: Boolean) = update(id) {
        it.copy(selection = selectionLabel(compatible, audioOnly, automaticFallback))
    }
    fun source(id: Long, source: MediaSource, audioIndex: Int?) = update(id) {
        val audio = source.audioStream(audioIndex)
        it.copy(container = safeContainer(source.container), sourceBitrate = source.bitrate.coerceAtLeast(0),
            sourceVideo = source.streams.firstOrNull { track -> track.type == "Video" }?.diagnosticFormat(),
            sourceAudio = audio?.diagnosticFormat(), sourceSpatial = spatialLabel(audio))
    }
    fun plan(id: Long, plan: PlaybackPlan) {
        source(id, plan.source, plan.audioIndex)
        update(id) { it.copy(method = when (plan.method) {
            "DirectPlay" -> "直接播放"; "DirectStream" -> "直接串流"; "Transcode" -> "转码播放"; else -> "未知播放方式"
        }, reasons = (it.decision?.reasons.orEmpty() + serverPlaybackReasons(plan.transcodeReasons)).distinct()) }
        event(id, DiagnosticStage.NEGOTIATION, DiagnosticOutcome.READY)
    }
    fun failure(id: Long, stage: DiagnosticStage, error: Throwable, fatal: Boolean = true) {
        val fact = diagnosticFailure(error)
        event(id, stage, if (fatal) DiagnosticOutcome.FAILED else DiagnosticOutcome.WARNING, fact)
        if (fatal) update(id) { it.copy(state = DiagnosticPlayerState.FAILED, failure = fact) }
    }
    fun canceled(id: Long) {
        update(id) {
            timing.stop(nowMs())
            it.copy(state = if (it.failure == null) DiagnosticPlayerState.STOPPED else it.state,
                timing = timing.snapshot(nowMs()))
        }
        val current = mutable.value
        if (current.id == id && current.events.lastOrNull()?.outcome == DiagnosticOutcome.RUNNING)
            event(id, current.stage, DiagnosticOutcome.CANCELED)
    }
    fun playback(id: Long, value: DiagnosticPlayerState, positionMs: Long, bufferMs: Long,
        video: Format?, audio: Format?) = update(id) {
        it.copy(state = if (it.failure != null) DiagnosticPlayerState.FAILED else value,
            positionMs = positionMs.coerceAtLeast(0) / 1000 * 1000, bufferMs = bufferMs.coerceAtLeast(0) / 1000 * 1000,
            outputVideo = video?.diagnosticFormat() ?: it.outputVideo,
            outputAudio = audio?.diagnosticFormat() ?: it.outputAudio, timing = timing.snapshot(nowMs()))
    }
    internal fun attempt(id: Long, generation: Long, reason: PlaybackLoadReason, positionMs: Long) = update(id) {
        timing.attempt(generation, nowMs(), positionMs, reason)
        it.copy(timing = timing.snapshot(nowMs()))
    }
    internal fun firstFrame(id: Long, generation: Long) = update(id) {
        timing.firstFrame(generation, nowMs())
        it.copy(timing = timing.snapshot(nowMs()))
    }
    internal fun bufferOperation(id: Long, generation: Long, kind: PlaybackBufferKind) = update(id) {
        timing.operation(generation, kind)
        it.copy(timing = timing.snapshot(nowMs()))
    }
    internal fun timingState(id: Long, generation: Long, state: TimingPlayerState, positionMs: Long,
        playWhenReady: Boolean) = update(id) {
        timing.state(generation, nowMs(), state, positionMs, playWhenReady)
        it.copy(timing = timing.snapshot(nowMs()))
    }
    fun decoder(id: Long, video: Boolean, name: String) = update(id) {
        val safe = name.takeIf { Regex("(?:c2\\.|OMX\\.|ffmpeg)[A-Za-z0-9._-]{0,100}", RegexOption.IGNORE_CASE).matches(it) }
            ?: "播放器解码器"
        if (video) it.copy(videoDecoder = safe) else it.copy(audioDecoder = safe)
    }
    fun bandwidth(id: Long, bitrate: Long) = update(id) { it.copy(bandwidth = bitrate.coerceAtLeast(0)) }
    fun negotiation(id: Long, value: PlaybackNegotiation) = update(id) {
        it.copy(decision = value.decision, displayCapabilities = value.capabilities.display.description,
            reasons = value.decision.reasons)
    }
    fun output(id: Long, value: PlaybackOutputSnapshot, reasons: List<String>) = update(id) {
        it.copy(actualOutput = value, reasons = (it.reasons + reasons).distinct())
    }
    internal fun addReasons(id: Long, reasons: List<String>) = update(id) {
        it.copy(reasons = (it.reasons + reasons).distinct())
    }
}

private fun selectionLabel(compatible: Boolean, audioOnly: Boolean, automaticFallback: Boolean): String = when {
    audioOnly -> "自动 · 仅音频回退"
    automaticFallback -> "自动 · 兼容回退"
    compatible -> "兼容转码"
    else -> "自动"
}

private fun diagnosticFailure(error: Throwable): DiagnosticFailure {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val status = causes.firstNotNullOfOrNull {
        when (it) { is ApiException -> it.status; is HttpDataSource.InvalidResponseCodeException -> it.responseCode; else -> null }
    }?.takeIf { it in 100..599 }
    val playback = causes.filterIsInstance<PlaybackException>().firstOrNull()
    val code = playback?.errorCodeName?.takeIf { Regex("[A-Z][A-Z0-9_]{0,100}").matches(it) }
    val category = when {
        status == 401 || status == 403 -> "访问受限或认证失效"
        status == 404 -> "请求的媒体或接口不存在"
        status != null -> "服务器请求失败"
        causes.any { it is SocketTimeoutException } -> "网络请求超时"
        causes.any { it is UnknownHostException } -> "服务器域名无法解析"
        playback?.errorCode?.let { it in 4000..4999 } == true -> "解码失败或格式不支持"
        causes.any { it is kotlinx.serialization.SerializationException } -> "服务器响应格式异常"
        causes.any { it is IOException } -> "网络或媒体读取失败"
        else -> "播放处理失败"
    }
    return DiagnosticFailure(category, status, code)
}

private fun MediaStream.diagnosticFormat() = DiagnosticFormat(
    safeCodec(if (type == "Audio") AudioPlaybackCapabilities.mimesFor(this).firstOrNull() ?: codec else codec),
    width.coerceAtLeast(0), height.coerceAtLeast(0),
    averageFrameRate.takeIf { it > 0 } ?: realFrameRate, channels.coerceAtLeast(0),
    sampleRate.takeIf { type == "Audio" }?.coerceAtLeast(0) ?: 0,
    range = if (type == "Video") VideoPlaybackCapabilities.rangeOf(this).label +
        (dvProfile?.takeIf { it in 1..10 }?.let { " · P$it" }.orEmpty()) else null)
private fun Format.diagnosticFormat() = DiagnosticFormat(safeCodec(sampleMimeType.orEmpty()),
    width.coerceAtLeast(0), height.coerceAtLeast(0), frameRate, channelCount.coerceAtLeast(0),
    sampleRate.coerceAtLeast(0), bitrate.toLong().coerceAtLeast(0),
    range = when {
        sampleMimeType == "video/dolby-vision" -> "Dolby Vision 轨道"
        colorInfo?.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
        colorInfo?.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "PQ / HDR（动态元数据未知）"
        else -> null
    })

private fun spatialLabel(stream: MediaStream?): String = when {
    stream?.audioSpatialFormat == "DolbyAtmos" || stream?.profile?.contains("Atmos", true) == true -> "Dolby Atmos 片源（接收端未验证）"
    stream?.audioSpatialFormat == "DTSX" || stream?.profile?.contains("DTS:X", true) == true -> "DTS:X 片源（接收端未验证）"
    else -> "未提供对象音效标识"
}
private fun safeCodec(value: String): String = when (value.lowercase(Locale.ROOT)) {
    "h264", "avc", "video/avc" -> "H.264"; "hevc", "h265", "video/hevc" -> "HEVC"
    "av1", "video/av01" -> "AV1"; "vp9", "video/x-vnd.on2.vp9" -> "VP9"
    "vp8", "video/x-vnd.on2.vp8" -> "VP8"; "mpeg2video", "video/mpeg2" -> "MPEG-2"
    "mpeg4", "video/mp4v-es" -> "MPEG-4"; "vc1", "video/wvc1" -> "VC-1"
    "aac", "audio/mp4a-latm" -> "AAC"; "ac3", "audio/ac3" -> "AC-3"
    "eac3", "audio/eac3" -> "E-AC-3"; "audio/eac3-joc" -> "E-AC-3 JOC"
    "dts", "dca", "audio/vnd.dts" -> "DTS"; "dtshd", "dts_hd", "audio/vnd.dts.hd" -> "DTS-HD"
    "truehd", "audio/true-hd" -> "TrueHD"; "flac", "audio/flac" -> "FLAC"
    "opus", "audio/opus" -> "Opus"; "vorbis", "audio/vorbis" -> "Vorbis"
    "mp3", "audio/mpeg" -> "MP3"; "pcm", "pcm_s16le", "pcm_s24le", "audio/raw" -> "PCM"
    else -> "其他或未知编码"
}
private fun safeContainer(value: String): String = value.lowercase(Locale.ROOT).takeIf {
    it in setOf("mkv", "matroska", "mp4", "m4v", "mov", "ts", "mpegts", "m2ts", "avi", "webm", "flv", "wmv", "mp3", "flac", "wav", "ogg",
        "iso", "bdmv", "bluray", "dvd", "video_ts")
}?.uppercase(Locale.ROOT) ?: "其他或未知容器"

fun diagnosticBitrate(value: Long): String = if (value > 0) "${String.format(Locale.ROOT, "%.2f", value / 1_000_000.0)} Mbps" else "未提供"
fun diagnosticTime(value: Long): String = String.format(Locale.ROOT, "%d:%02d", value.coerceAtLeast(0) / 60_000, value.coerceAtLeast(0) / 1000 % 60)
fun diagnosticElapsed(value: Long): String = String.format(Locale.ROOT, "%.3f s", value.coerceAtLeast(0) / 1000.0)

fun PlaybackDiagnosticSnapshot.report(): String = buildString {
    appendLine("Joyfin 播放诊断 v3")
    appendLine("应用版本：${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.SDK_INT}")
    appendLine("状态：${state.label}；阶段：${stage.label}")
    appendLine("播放方式：$method；策略：$selection")
    appendLine("码率设置：${if (quality == dev.joyfin.data.PlaybackQuality.ORIGINAL) "原画不限码率" else diagnosticBitrate(quality.toLong())}")
    appendLine("源容器：$container；源总码率：${diagnosticBitrate(sourceBitrate)}")
    appendLine("源视频：${sourceVideo?.description ?: "未提供"}")
    appendLine("源音频：${sourceAudio?.description ?: "未提供"}")
    appendLine("渲染器输入视频：${outputVideo?.description ?: "尚未取得播放格式"}")
    appendLine("渲染器输入音频：${outputAudio?.description ?: "尚未取得播放格式"}")
    appendLine("片源对象音效：$sourceSpatial")
    appendLine("显示能力：$displayCapabilities；电视实际 HDR/DV 模式：未知，需显示设备确认")
    decision?.let {
        appendLine("视频播放请求：${it.sourceRange.label} → ${it.targetRange.label} · ${it.videoPath.label}")
        appendLine("音频播放请求：${it.outputMode.label} · ${it.audioPath.label}")
        it.audioSupport?.let { support -> appendLine("音频能力探测：${support.sampleRate} Hz；编码输出 ${support.passthroughChannels} 声道；系统解码 ${support.platformChannels} 声道；FFmpeg ${support.softwareChannels} 声道") }
    }
    appendLine("AudioTrack 实际配置：${actualOutput?.description ?: "尚未取得"}")
    appendLine("实际输出路由：${actualOutput?.route ?: "未知"}；接收端对象音效：未验证")
    appendLine("视频解码器：$videoDecoder；音频解码器：$audioDecoder")
    appendLine("网络带宽估算：${diagnosticBitrate(bandwidth)}")
    appendLine("播放位置：${diagnosticTime(positionMs)}；已缓冲：${bufferMs / 1000} 秒")
    timing.reportLines().forEach { appendLine(it) }
    appendLine("转码原因：${reasons.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "服务端未提供"}")
    failure?.let { appendLine("当前错误：${it.description}") }
    appendLine("最近阶段记录（最多 30 条）：")
    events.forEach { appendLine("+${diagnosticElapsed(it.elapsedMs)} ${it.stage.label} · ${it.outcome.label}${it.failure?.let { error -> " · ${error.description}" }.orEmpty()}") }
    append("报告不含服务器地址、凭据、账号、媒体名称、文件路径或设备序列号。")
}
