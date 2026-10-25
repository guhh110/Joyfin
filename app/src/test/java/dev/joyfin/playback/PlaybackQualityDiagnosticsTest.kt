@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.common.Format
import dev.joyfin.data.Media
import dev.joyfin.data.MediaSource
import dev.joyfin.data.MediaStream
import dev.joyfin.data.PlaybackPlan
import dev.joyfin.playback.quality.PlaybackOutputSnapshot
import dev.joyfin.playback.quality.QualityFixtures
import dev.joyfin.playback.quality.serverPlaybackReasons
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackQualityDiagnosticsTest {
    @After fun cleanup() { PlaybackDiagnostics.clear() }
    private fun negotiation() = QualityFixtures.let {
        DeviceProfile.negotiate(it.capabilities(), it.decision(), it.source(), null, Int.MAX_VALUE)
    }

    @Test fun timingCallbacksFromAnotherSessionCannotChangeTheCurrentReport() {
        val old = PlaybackDiagnostics.begin("old")
        PlaybackDiagnostics.attempt(old, 1, PlaybackLoadReason.INITIAL, 0)
        val current = PlaybackDiagnostics.begin("new")
        PlaybackDiagnostics.attempt(current, 2, PlaybackLoadReason.INITIAL, 0)
        PlaybackDiagnostics.firstFrame(old, 1)
        PlaybackDiagnostics.timingState(old, 1, TimingPlayerState.BUFFERING, 0, true)
        PlaybackDiagnostics.firstFrame(current, 1)
        val snapshot = PlaybackDiagnostics.state.value
        assertNull(snapshot.timing.firstFrameMs)
        assertTrue(snapshot.timing.buffers.isEmpty())
        assertEquals(1, snapshot.timing.attemptCount)
        assertTrue(snapshot.report().contains("首次出画：尚未出画"))
    }

    @Test fun newPlaybackOfSameItemRejectsAllOldDiagnosticCallbacks() {
        val old = PlaybackDiagnostics.begin("movie")
        val current = PlaybackDiagnostics.begin("movie")
        PlaybackDiagnostics.negotiation(old, negotiation())
        PlaybackDiagnostics.output(old, PlaybackOutputSnapshot(encoding = "DTS-HD"), listOf("old reason"))
        PlaybackDiagnostics.source(old, QualityFixtures.source(), 1)
        PlaybackDiagnostics.failure(old, DiagnosticStage.STREAM, IOException("old failure"))
        val state = PlaybackDiagnostics.state.value
        assertEquals(current, state.id)
        assertNull(state.actualOutput)
        assertNull(state.decision)
        assertNull(state.sourceAudio)
        assertNull(state.failure)
        assertTrue(state.reasons.isEmpty())
    }

    @Test fun rebuildingPlaybackClearsPreviouslyObservedOutputAndDecoderFacts() {
        val id = PlaybackDiagnostics.begin("movie")
        PlaybackDiagnostics.negotiation(id, negotiation())
        PlaybackDiagnostics.output(id, PlaybackOutputSnapshot(encoding = "DTS-HD", channels = 8), emptyList())
        PlaybackDiagnostics.decoder(id, false, "c2.vendor.audio")
        PlaybackDiagnostics.preparing(id, Int.MAX_VALUE, false, false, false)
        val state = PlaybackDiagnostics.state.value
        assertNull(state.actualOutput)
        assertNull(state.decision)
        assertEquals("未知", state.displayCapabilities)
        assertEquals("尚未初始化", state.audioDecoder)
    }

    @Test fun rendererInputAndActualAudioTrackHaveSeparateFormatsInReport() {
        val id = PlaybackDiagnostics.begin("movie")
        PlaybackDiagnostics.negotiation(id, negotiation())
        PlaybackDiagnostics.playback(id, DiagnosticPlayerState.PLAYING, 1_000, 5_000, null,
            Format.Builder().setSampleMimeType("audio/vnd.dts.hd").setChannelCount(8).setSampleRate(96_000).build())
        PlaybackDiagnostics.output(id, PlaybackOutputSnapshot(encoding = "PCM 16-bit", pcm = true,
            sampleRate = 48_000, channels = 2, route = "内置扬声器"), listOf("AudioTrack 输出声道少于片源声道"))
        val report = PlaybackDiagnostics.state.value.report()
        assertTrue(report.contains("渲染器输入音频：DTS-HD · 8 声道 · 96000 Hz"))
        assertTrue(report.contains("AudioTrack 实际配置：PCM 16-bit · 48000 Hz · 2 声道"))
        assertTrue(report.contains("电视实际 HDR/DV 模式：未知"))
        assertTrue(report.contains("接收端对象音效：未验证"))
    }

    @Test fun atmosSourceTagDoesNotBecomeReceiverConfirmation() {
        val id = PlaybackDiagnostics.begin("movie")
        val source = QualityFixtures.source(codec = "eac3").let { it.copy(streams = it.streams.map { stream ->
            if (stream.type == "Audio") stream.copy(profile = "E-AC-3 JOC", audioSpatialFormat = "DolbyAtmos") else stream
        }) }
        PlaybackDiagnostics.source(id, source, 1)
        val state = PlaybackDiagnostics.state.value
        assertEquals("E-AC-3 JOC", state.sourceAudio!!.codec)
        assertTrue(state.sourceSpatial.contains("接收端未验证"))
        assertTrue(state.report().contains("接收端对象音效：未验证"))
    }

    @Test fun copiedReportDoesNotIncludeUntrustedPathsCredentialsOrErrorMessages() {
        val secret = "sensitive_marker_924"
        val url = "https://example.test/$secret?api_key=$secret"
        val source = MediaSource(id = secret, name = secret, container = "iso", path = "/private/$secret.iso",
            streams = listOf(MediaStream(type = "Audio", index = 1, codec = "dts", profile = "DTS-HD MA $secret",
                displayTitle = secret, path = "/private/$secret.dts")))
        val media = Media(secret, secret, "Movie", sources = listOf(source))
        val id = PlaybackDiagnostics.begin(secret)
        PlaybackDiagnostics.plan(id, PlaybackPlan(media, source, secret, url, "Transcode", 0, 1, -1,
            Int.MAX_VALUE, emptyList(), listOf(url)))
        PlaybackDiagnostics.decoder(id, false, "/private/$secret")
        PlaybackDiagnostics.failure(id, DiagnosticStage.STREAM, IOException(url))
        val report = PlaybackDiagnostics.state.value.report()
        assertTrue(report.contains("源容器：ISO"))
        assertTrue(report.contains("DTS-HD"))
        assertFalse(report.contains(secret))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("/private/"))
        assertFalse(report.contains("api_key="))
    }

    @Test fun serverReasonsAreAllowlistedAndDeduplicated() {
        val reasons = serverPlaybackReasons(listOf("AudioCodecNotSupported", "AudioCodecNotSupported", "https://example.test/private"))
        assertEquals(listOf("音频编码设备无法直接解码", "服务端提供了其他转码原因"), reasons)
    }
}
