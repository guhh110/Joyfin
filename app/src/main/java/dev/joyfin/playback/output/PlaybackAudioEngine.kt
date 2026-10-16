@file:androidx.annotation.OptIn(markerClass = [
    androidx.media3.common.util.UnstableApi::class,
    androidx.media3.common.util.ExperimentalApi::class,
])
package dev.joyfin.playback.output

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRouting
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.*
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import dev.joyfin.playback.quality.*
import java.util.concurrent.atomic.AtomicLong

/** The only module that changes the native audio output policy or observes an AudioTrack. */
internal class PlaybackAudioEngine(
    private val onOutput: (PlaybackOutputOwner, PlaybackOutputSnapshot) -> Unit,
    private val onRoute: (PlaybackOutputOwner, AudioDeviceInfo?) -> Unit,
    private val onCapabilitiesChanged: (PlaybackOutputOwner) -> Unit,
) {
    private data class Execution(val owner: PlaybackOutputOwner, val pcmOnly: Boolean = false,
        val softwareDecode: Boolean = false, val baseLayer: Boolean = false)
    @Volatile private var execution = Execution(PlaybackOutputOwner(0, 0))
    private val main = Handler(Looper.getMainLooper())
    private val outputSequence = AtomicLong()

    fun begin(owner: PlaybackOutputOwner) { execution = execution.copy(owner = owner) }
    fun activate(owner: PlaybackOutputOwner, decision: PlaybackDecision) {
        execution = Execution(owner, decision.pcmOnly, decision.audioPath == AudioPath.SOFTWARE_PCM,
            decision.videoPath == VideoPath.BASE_LAYER)
    }
    fun invalidate() { execution = Execution(PlaybackOutputOwner(-1, -1)); outputSequence.incrementAndGet() }

    fun renderers(context: Context): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            val delegate = AudioTrackAudioOutputProvider.Builder(context).build()
            val capabilityListener = AudioOutputProvider.Listener {
                val owner = execution.owner
                main.post { if (owner == execution.owner) onCapabilitiesChanged(owner) }
            }
            val provider = object : AudioOutputProvider by delegate {
                private var observing = false
                private fun observeCapabilities() {
                    if (!observing) { delegate.addListener(capabilityListener); observing = true }
                }
                override fun release() {
                    if (observing) delegate.removeListener(capabilityListener)
                    delegate.release()
                }
                override fun getFormatSupport(config: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
                    observeCapabilities()
                    return if (execution.pcmOnly && config.format.sampleMimeType != "audio/raw") AudioOutputProvider.FormatSupport.UNSUPPORTED
                    else delegate.getFormatSupport(config)
                }

                override fun getOutputConfig(config: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
                    observeCapabilities()
                    if (execution.pcmOnly && config.format.sampleMimeType != "audio/raw")
                        throw AudioOutputProvider.ConfigurationException("PCM output requires decoded audio")
                    return delegate.getOutputConfig(config)
                }

                override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
                    val output = delegate.getAudioOutput(config)
                    val outputId = outputSequence.incrementAndGet()
                    val owner = execution.owner
                    val track = (output as? AudioTrackAudioOutput)?.audioTrack ?: return output
                    fun publish() {
                        if (owner != execution.owner || outputId != outputSequence.get()) return
                        val device = runCatching { track.routedDevice }.getOrNull()
                        onOutput(owner, PlaybackOutputSnapshot(
                            encoding = audioEncodingLabel(config.encoding), pcm = Util.isEncodingLinearPcm(config.encoding),
                            sampleRate = track.sampleRate, channels = track.channelCount,
                            route = audioRouteLabel(device?.type), offload = config.isOffload,
                            audioMime = audioEncodingMime(config.encoding),
                        ))
                        if (device != null) onRoute(owner, device)
                    }
                    val listener = AudioRouting.OnRoutingChangedListener { publish() }
                    track.addOnRoutingChangedListener(listener, main)
                    main.post { publish() }
                    return object : AudioOutput by output {
                        override fun play() { output.play(); main.post { publish() } }
                        override fun release() {
                            outputSequence.compareAndSet(outputId, outputId + 1)
                            track.removeOnRoutingChangedListener(listener)
                            output.release()
                        }
                    }
                }
            }
            return DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .setAudioOutputProvider(provider).build()
        }
    }.setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setMediaCodecSelector(MediaCodecSelector { mime, secure, tunneling ->
            if ((execution.baseLayer && mime == "video/dolby-vision") ||
                (execution.softwareDecode && mime.startsWith("audio/"))) emptyList()
            else MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
        })
}

internal fun audioRouteLabel(type: Int?): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "内置听筒"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
    AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙音频"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB 音频"
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> "数字音频输出"
    else -> "未知"
}

internal fun audioEncodingLabel(encoding: Int): String = when (encoding) {
    AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
    AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
    AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
    AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
    AudioFormat.ENCODING_AC3 -> "AC-3"
    AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
    AudioFormat.ENCODING_E_AC3_JOC -> "E-AC-3 JOC"
    AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD"
    AudioFormat.ENCODING_DTS -> "DTS"
    AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
    AudioFormat.ENCODING_AC4 -> "AC-4"
    AudioFormat.ENCODING_AAC_LC -> "AAC"
    AudioFormat.ENCODING_MP3 -> "MP3"
    else -> "其他编码"
}

/** Protocol values for output comparison; policy never compares translated display labels. */
private fun audioEncodingMime(encoding: Int): String? = when {
    Util.isEncodingLinearPcm(encoding) -> "audio/raw"
    else -> when (encoding) {
        AudioFormat.ENCODING_AC3 -> "audio/ac3"
        AudioFormat.ENCODING_E_AC3 -> "audio/eac3"
        AudioFormat.ENCODING_E_AC3_JOC -> "audio/eac3-joc"
        AudioFormat.ENCODING_DOLBY_TRUEHD -> "audio/true-hd"
        AudioFormat.ENCODING_DTS -> "audio/vnd.dts"
        AudioFormat.ENCODING_DTS_HD -> "audio/vnd.dts.hd"
        else -> null
    }
}
