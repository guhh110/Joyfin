package dev.joyfin.playback

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackNetworkTimingTest {
    @After fun cleanup() = PlaybackDiagnostics.clear()

    @Test fun openingFirstByteAndReadWaitAreAccountedSeparatelyFromIdleOrParsingGaps() {
        val tracker = PlaybackNetworkTracker(1000)
        tracker.begin(1, 1000)
        val meter = requireNotNull(tracker.open(1, 1100, 0, -1))
        meter.opened(1400, 200, HttpRangeResult.NONE)
        meter.readStarted(1500)
        meter.readFinished(1600, 1024)
        meter.readStarted(5600) // 4 seconds outside read, e.g. parser work or paused loading.
        meter.readFinished(13600, 2048) // A separate eight-second read wait.
        val value = tracker.snapshot(13600).requests.single()
        assertEquals(100L, value.elapsedMs)
        assertEquals(300L, value.openMs)
        assertEquals(500L, value.firstByteMs)
        assertEquals(3072L, value.bytes)
        assertEquals(8100L, value.readMs)
        assertEquals(8000L, value.maxReadMs)
        assertEquals(4000L, value.longestReadGapMs)
        assertEquals(0L, value.pendingReadMs)
    }

    @Test fun aBlockedReadIsVisibleBeforeItReturnsAndDoesNotBecomeAnIdleGap() {
        val tracker = PlaybackNetworkTracker(0)
        tracker.begin(1, 0)
        val meter = requireNotNull(tracker.open(1, 0, 1024, 2048))
        meter.opened(100, 206, HttpRangeResult.MATCHED)
        meter.readStarted(120)
        val pending = tracker.snapshot(8120).requests.single()
        assertEquals(8000L, pending.pendingReadMs)
        assertEquals(20L, pending.longestReadGapMs)
        assertEquals(0L, pending.readMs)
        assertNull(pending.firstByteMs)
        meter.readFinished(8500, 2048)
        assertEquals(8380L, tracker.snapshot(8500).requests.single().readMs)
    }

    @Test fun rangeValidationNeverTreatsAnIgnoredOrMalformedResponseAsConfirmed() {
        assertEquals(HttpRangeResult.NONE, httpRangeResult(0, -1, 200, null))
        assertEquals(HttpRangeResult.IGNORED, httpRangeResult(500, -1, 200, null))
        assertEquals(HttpRangeResult.IGNORED, httpRangeResult(0, 200, 200, null))
        assertEquals(HttpRangeResult.MATCHED, httpRangeResult(500, -1, 206, "bytes 500-999/1000"))
        assertEquals(HttpRangeResult.MATCHED, httpRangeResult(0, 200, 206, "Bytes 0-199/*"))
        assertEquals(HttpRangeResult.MISMATCHED, httpRangeResult(500, -1, 206, "bytes 0-999/1000"))
        for (range in listOf(null, "bytes 999-500/1000", "bytes 999999999999999999999999-1/*", "secret://token"))
            assertEquals(HttpRangeResult.UNKNOWN, httpRangeResult(500, -1, 206, range))
    }

    @Test fun eofCloseFailureAndCancellationFreezeStatsAgainstLateCallbacks() {
        val tracker = PlaybackNetworkTracker(0)
        tracker.begin(1, 0)
        val meter = requireNotNull(tracker.open(1, 0, 0, -1))
        meter.opened(1, 200, HttpRangeResult.NONE)
        meter.readStarted(2); meter.readFinished(3, 1024)
        meter.readStarted(4); meter.readFinished(5, -1)
        meter.finish(6, HttpReadState.CLOSED)
        val finished = tracker.snapshot(100).requests.single()
        assertEquals(HttpReadState.EOF, finished.state)
        assertEquals(5L, finished.durationMs)
        meter.readFinished(200, 9999)
        assertEquals(finished, tracker.snapshot(1000).requests.single())

        val failure = requireNotNull(tracker.open(1, 100, 20, -1))
        failure.fail(500, DiagnosticFailure("网络请求超时"))
        failure.finish(600, HttpReadState.CLOSED)
        assertEquals(HttpReadState.FAILED, tracker.snapshot(1000).requests.last().state)
        assertEquals(400L, tracker.snapshot(1000).requests.last().openMs)
    }

    @Test fun aNewAttemptCancelsOldOpenAndReadMetersAndRejectsTheirSubsequentResults() {
        val tracker = PlaybackNetworkTracker(0)
        tracker.begin(1, 0)
        val meter = requireNotNull(tracker.open(1, 10, 0, -1))
        meter.opened(20, 200, HttpRangeResult.NONE)
        meter.readStarted(30)
        tracker.begin(2, 1030)
        val frozen = tracker.snapshot(1030).requests.single()
        assertEquals(HttpReadState.CANCELED, frozen.state)
        assertEquals(1000L, frozen.readMs)
        meter.readFinished(2030, 10000)
        meter.fail(2040, DiagnosticFailure("old"))
        assertNull(tracker.open(1, 2040, 0, -1))
        assertEquals(frozen, tracker.snapshot(3000).requests.single())
        assertNotNull(tracker.open(2, 3000, 4096, -1))
    }

    @Test fun historiesStayBoundedAndOnlyValidBandwidthSamplesAreRetained() {
        val tracker = PlaybackNetworkTracker(0)
        tracker.begin(1, 0)
        repeat(30) { tracker.open(1, it.toLong(), it * 1000L, -1)?.finish(it.toLong(), HttpReadState.CLOSED) }
        tracker.bandwidth(30, 0, 100, 100)
        tracker.bandwidth(30, 100, 0, 100)
        assertTrue(tracker.snapshot(30).samples.isEmpty())
        repeat(15) { tracker.bandwidth(40 + it.toLong(), 1000, it + 1L, 1_000_000) }
        val snapshot = tracker.snapshot(60)
        assertEquals(20, snapshot.requests.size)
        assertEquals(11, snapshot.requests.first().sequence)
        assertEquals(30, snapshot.requests.last().sequence)
        assertEquals(10, snapshot.samples.size)
        assertEquals(6L, snapshot.samples.first().bytes)
    }

    @Test fun diagnosticStoreRejectsTransfersAndBandwidthFromOtherItemsAndVersions() {
        val old = PlaybackDiagnostics.begin("old")
        PlaybackDiagnostics.attempt(old, 1, PlaybackLoadReason.INITIAL, 0)
        val oldMeter = requireNotNull(PlaybackDiagnostics.httpStarted(old, 1, 0, -1))
        val current = PlaybackDiagnostics.begin("new")
        PlaybackDiagnostics.attempt(current, 2, PlaybackLoadReason.INITIAL, 0)
        oldMeter.opened(100, 200, HttpRangeResult.NONE)
        PlaybackDiagnostics.httpChanged(old, 1)
        assertNull(PlaybackDiagnostics.httpStarted(old, 1, 0, -1))
        assertNull(PlaybackDiagnostics.httpStarted(current, 1, 0, -1))
        PlaybackDiagnostics.bandwidth(current, 1, 12345, 1000, 1000)
        assertEquals(0L, PlaybackDiagnostics.state.value.bandwidth)
        assertTrue(PlaybackDiagnostics.state.value.network.requests.isEmpty())
        val meter = requireNotNull(PlaybackDiagnostics.httpStarted(current, 2, 1024, -1))
        meter.opened(System.nanoTime() / 1_000_000, 206, HttpRangeResult.MATCHED)
        PlaybackDiagnostics.httpChanged(current, 2)
        PlaybackDiagnostics.bandwidth(current, 2, 800_000, 1000, 100_000)
        assertEquals(1024L, PlaybackDiagnostics.state.value.network.requests.single().position)
        assertEquals(100_000L, PlaybackDiagnostics.state.value.network.samples.single().bytes)
    }

    @Test fun copiedReportContainsOnlyNumericHttpFactsAndAllowlistedErrors() {
        val secret = "private-server-token-file-name"
        val id = PlaybackDiagnostics.begin(secret)
        PlaybackDiagnostics.attempt(id, 1, PlaybackLoadReason.INITIAL, 0)
        val meter = requireNotNull(PlaybackDiagnostics.httpStarted(id, 1, 4096, -1))
        val range = httpRangeResult(4096, -1, 206, "https://$secret/path?api_key=$secret")
        meter.opened(System.nanoTime() / 1_000_000, 206, range)
        meter.fail(System.nanoTime() / 1_000_000, diagnosticFailure(IOException("https://$secret/path")))
        PlaybackDiagnostics.httpChanged(id, 1)
        val report = PlaybackDiagnostics.state.value.report()
        assertTrue(report.startsWith("Joyfin 播放诊断 v5"))
        assertTrue(report.contains("字节起点 4096"))
        assertTrue(report.contains("网络或媒体读取失败"))
        assertTrue(report.contains("兼容与协商说明"))
        assertFalse(report.contains(secret))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("api_key"))
        assertFalse(report.contains("转码原因："))
    }

    @Test fun configuredRequestIdentitiesProduceOnlyPurposeEnumsInTheReport() {
        val media = "https://private.invalid/private-video?token=secret"
        val subtitle = "https://private.invalid/private-subtitle?token=secret"
        val subtitles = setOf(subtitle)
        assertEquals(HttpReadPurpose.MEDIA, httpReadPurpose(media, media, subtitles, false))
        assertEquals(HttpReadPurpose.SUBTITLE, httpReadPurpose(subtitle, media, subtitles, false))
        assertEquals(HttpReadPurpose.STREAM_RESOURCE, httpReadPurpose("segment", media, subtitles, true))
        assertEquals(HttpReadPurpose.OTHER, httpReadPurpose("font", media, subtitles, false))
        val tracker = PlaybackNetworkTracker(0)
        tracker.begin(1, 0)
        tracker.open(1, 0, 0, -1, httpReadPurpose(subtitle, media, subtitles, false))
        val report = tracker.snapshot(100).reportLines().joinToString("\n")
        assertTrue(report.contains("独立字幕"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("secret"))
    }
}
