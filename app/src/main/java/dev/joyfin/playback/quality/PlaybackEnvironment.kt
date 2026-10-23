@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback.quality

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Format
import androidx.media3.common.C
import dev.joyfin.data.*
import dev.joyfin.playback.*
import dev.joyfin.playback.output.PlaybackAudioEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Facade used by PlaybackController: owns capabilities, preferences and bounded fallback state. */
internal class PlaybackEnvironment(private val context: Context, private val scope: CoroutineScope,
    private val onChange: () -> Unit, private val onNotice: (String?) -> Unit) {
    private val preferences = AudioOutputPreferences(context)
    private val fallbackPolicy = PlaybackFallbackPolicy()
    private var owner = PlaybackOutputOwner(0, 0)
    private var active: PlaybackNegotiation? = null
    private var activeSource: MediaSource? = null
    private var activeAudioIndex: Int? = null
    private var activeCompatible = false
    private var serverReasons: List<String> = emptyList()
    private var runtimeReasons: List<String> = emptyList()
    var notice: String? = null
        private set
    private data class ProbeKey(val streams: List<MediaStream>, val audio: Int?, val revision: Long)
    private var cachedProbe: Pair<ProbeKey, PlaybackCapabilitySnapshot>? = null
    val currentNegotiation: PlaybackNegotiation? get() = active
    private var preferenceJob: Job? = null
    private var changeJob: Job? = null
    private var running = false
    private var revision = 0L
    private var mode = preferences.mode
    private var speed = 1f
    private val monitor = PlaybackDeviceMonitor(context) { changed(PlaybackLoadReason.DEVICE) }
    var reloadReason: PlaybackLoadReason = PlaybackLoadReason.DEVICE
        private set
    val audioEngine = PlaybackAudioEngine(
        { eventOwner, output -> if (running && eventOwner == owner) {
            val reasons = actualOutputReasons(active?.decision, output)
            PlaybackDiagnostics.output(owner.diagnosticId, output, reasons)
            publishNotice(reasons)
        } },
        { eventOwner, device -> if (running && eventOwner == owner) monitor.routed(device) },
        { eventOwner -> if (running && eventOwner == owner) monitor.refresh() },
    )
    val audioMode: AudioOutputMode get() = mode
    val isFallback: Boolean get() = fallbackPolicy.active
    val forceAudioTranscode: Boolean get() = fallbackPolicy.forceAudio
    val needsReload: Boolean get() = active?.capabilities?.revision != revision

    fun begin(generation: Long, diagnosticId: Long) {
        owner = PlaybackOutputOwner(generation, diagnosticId)
        active = null
        activeSource = null
        activeAudioIndex = null
        audioEngine.begin(owner)
    }
    suspend fun negotiate(mediaId: String, source: MediaSource?, audioIndex: Int?, bitrate: Int, compatible: Boolean): PlaybackNegotiation {
        val selected = source?.audioStream(audioIndex)
        fallbackPolicy.context(mediaId, source?.id, selected?.index, monitor.revision)
        val version = revision
        val requirements = PlaybackRequirements(mode, compatible, fallbackPolicy.forcePcm,
            fallbackPolicy.forceSoftware, fallbackPolicy.forceAudio, fallbackPolicy.forceVideo, speed)
        val key = ProbeKey(source?.streams.orEmpty(), selected?.index, version)
        val capabilities = cachedProbe?.takeIf { it.first == key }?.second ?: withContext(Dispatchers.Default) {
            val display = monitor.displayCapabilities()
            PlaybackCapabilitySnapshot(version, display, VideoPlaybackCapabilities.assess(context, source, display),
                AudioCapabilityProbe.detect(context, source, selected?.index, monitor.currentRoute))
        }.also { cachedProbe = key to it }
        val decision = PlaybackDecisionPolicy.decide(source, selected?.index, capabilities, requirements)
        return DeviceProfile.negotiate(capabilities, decision, source, selected?.index, bitrate)
    }
    fun activate(value: PlaybackNegotiation, transcodeReasons: List<String>, source: MediaSource?, audioIndex: Int?, compatible: Boolean) {
        active = value
        activeSource = source
        activeAudioIndex = audioIndex
        activeCompatible = compatible
        serverReasons = serverPlaybackReasons(transcodeReasons)
        runtimeReasons = emptyList()
        audioEngine.activate(owner, value.decision)
        PlaybackDiagnostics.negotiation(owner.diagnosticId, value)
        publishNotice(emptyList())
    }
    private fun publishNotice(actualReasons: List<String>) {
        notice = (active?.decision?.reasons.orEmpty() + serverReasons + runtimeReasons + actualReasons).distinct()
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")
        onNotice(notice)
    }
    fun isCurrent(value: PlaybackNegotiation): Boolean = value.capabilities.revision == revision
    fun fallback(error: PlaybackException) = needsReload || fallbackPolicy.advance(error, active?.decision)
    fun failureMessage(error: PlaybackException) = fallbackPolicy.failureMessage(error, active?.decision)
    fun resetAttempts() = fallbackPolicy.reset()
    fun setAudioMode(value: AudioOutputMode) { preferences.mode = value }
    fun setSpeed(value: Float) {
        val changedPath = (speed == 1f) != (value == 1f)
        speed = value
        if (!changedPath) return
        val current = active
        val next = current?.let {
            PlaybackDecisionPolicy.decide(activeSource, activeAudioIndex, it.capabilities,
                PlaybackRequirements(mode, activeCompatible, fallbackPolicy.forcePcm, fallbackPolicy.forceSoftware,
                    fallbackPolicy.forceAudio, fallbackPolicy.forceVideo, value))
        }
        if (current != null && next != null && !needsReload &&
            PlaybackDecisionPolicy.canChangeSpeedInPlace(current.decision, next)) {
            // Already-decoded PCM can change speed without stopping or renegotiating the stream.
            val updated = current.copy(decision = next)
            active = updated
            audioEngine.activate(owner, next)
            PlaybackDiagnostics.negotiation(owner.diagnosticId, updated)
            publishNotice(emptyList())
        } else changed(PlaybackLoadReason.SPEED)
    }

    fun videoInput(format: Format) {
        if (!running) return
        val current = active ?: return
        val range = when {
            format.sampleMimeType == "video/dolby-vision" -> VideoRange.DOLBY_VISION
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> VideoRange.HLG
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> VideoRange.HDR10
            else -> VideoRange.UNKNOWN
        }
        if (needsVideoConversion(current.decision, current.capabilities.display, range) && fallbackPolicy.requireVideoConversion()) {
            // New parser evidence can reveal HDR that was absent from the server metadata.
            revision++
            reloadReason = PlaybackLoadReason.VIDEO_FORMAT
            scheduleChange()
        } else if (current.decision.videoTranscode && range !in setOf(VideoRange.UNKNOWN, VideoRange.SDR)) {
            val reason = "服务器转换后仍收到 HDR／DV 轨道，实际显示效果未验证"
            if (reason !in runtimeReasons) {
                runtimeReasons = runtimeReasons + reason
                PlaybackDiagnostics.addReasons(owner.diagnosticId, runtimeReasons)
                publishNotice(emptyList())
            }
        }
    }

    private fun changed(reason: PlaybackLoadReason) {
        if (!running) return
        reloadReason = reason
        revision++
        fallbackPolicy.reset()
        scheduleChange()
    }
    private fun scheduleChange() {
        changeJob?.cancel()
        changeJob = scope.launch {
            delay(300)
            onChange()
        }
    }
    fun start() {
        if (running) return
        running = true
        if (mode != preferences.mode) {
            mode = preferences.mode; revision++; fallbackPolicy.reset()
            reloadReason = PlaybackLoadReason.AUDIO_MODE
        }
        monitor.start()
        preferenceJob = scope.launch {
            preferences.changes.collectLatest { value ->
                if (mode != value) { mode = value; changed(PlaybackLoadReason.AUDIO_MODE) }
            }
        }
    }
    fun stop() {
        running = false
        preferenceJob?.cancel()
        changeJob?.cancel()
        monitor.stop()
        audioEngine.invalidate()
    }
}
