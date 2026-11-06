package dev.joyfin

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.joyfin.data.Session
import dev.joyfin.data.SessionStore
import dev.joyfin.data.SkipPreferences
import dev.joyfin.playback.PlaybackController
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackFlowTest {
    @Test
    fun automaticOutroStartsNextEpisodeWithAutoNextDisabledAndCountdownDismissed() =
        checkOutroTransition(automatic = true)

    @Test
    fun earlyManualOutroSkipStartsNextEpisodeWithAutoNextDisabled() =
        checkOutroTransition(automatic = false)

    @Test
    fun shortOutroOfferTakesPriorityOverTheNormalNextEpisodeCountdown() =
        checkOutroTransition(automatic = true, autoNext = true, outroDurationMs = 2000)

    @Test
    fun nextButtonMarksCurrentEpisodeOnlyAfterHalfwayAndRetriesFailedMark() {
        checkOutroTransition(automatic = false, nextHalfOffsetMs = -1)
        checkOutroTransition(automatic = false, nextHalfOffsetMs = 0)
        checkOutroTransition(automatic = false, nextHalfOffsetMs = 1, failFirstMark = true)
    }

    @Test
    fun selectingAnEpisodeAfterHalfwayDoesNotApplyTheNextButtonWatchedRule() =
        checkOutroTransition(automatic = false, nextHalfOffsetMs = 1, pickEpisode = true)

    @Test
    fun countdownWaitsWhilePausedAndUsesSpeedAndNewSeekPosition() =
        checkOutroTransition(automatic = false, autoNext = true, verifyCountdown = true)

    private fun checkOutroTransition(automatic: Boolean, autoNext: Boolean = false, outroDurationMs: Long = 5000,
        nextHalfOffsetMs: Long? = null, pickEpisode: Boolean = false, failFirstMark: Boolean = false,
        verifyCountdown: Boolean = false) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val store = SessionStore(context)
        val previousSession = store.load()
        val previousAutoNext = store.autoNext
        val previousProxy = java.net.ProxySelector.getDefault()
        java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI): List<java.net.Proxy> = listOf(java.net.Proxy.NO_PROXY)
            override fun connectFailed(uri: java.net.URI, address: java.net.SocketAddress, error: java.io.IOException) = Unit
        })
        val bytes = inst.context.assets.open("sample.mp4").use { it.readBytes() }
        val closed = java.util.concurrent.atomic.AtomicInteger()
        val events = ConcurrentLinkedQueue<String>()
        val markAttempts = java.util.concurrent.atomic.AtomicInteger()
        val holdStream = java.util.concurrent.atomic.AtomicBoolean()
        val streamGate = java.util.concurrent.CountDownLatch(1)
        val source = """{"Id":"source","SupportsDirectPlay":true,"Container":"mp4","MediaStreams":[{"Type":"Video","Index":0,"Codec":"h264","Width":320,"Height":180},{"Type":"Audio","Index":1,"Codec":"aac","Channels":2}]}"""
        fun episode(id: String) = """{"Id":"$id","Name":"$id","Type":"Episode","SeriesId":"series","ParentId":"season","RunTimeTicks":600000000,"MediaSources":[$source]}"""
        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.contains("/PlayedItems/") -> {
                        events.add("watched:${path.substringAfterLast('/')}")
                        val fail = markAttempts.incrementAndGet() == 1 && failFirstMark
                        MockResponse().setResponseCode(if (fail) 500 else 204)
                    }
                    path.endsWith("/Sessions/Playing/Stopped") -> {
                        events.add("stopped:${JSONObject(request.body.readUtf8()).optString("ItemId")}")
                        MockResponse().setResponseCode(204)
                    }
                    path.contains("/Sessions/Playing") -> MockResponse().setResponseCode(204)
                    path.endsWith("/Items/first") -> json(episode("first"))
                    path.endsWith("/Items/second") -> json(episode("second"))
                    path.endsWith("/Shows/series/Episodes") -> json("""{"Items":[${episode("first")},${episode("second")}]}""")
                    path.endsWith("/PlaybackInfo") -> json("""{"PlaySessionId":"${path.substringBeforeLast('/')}","MediaSources":[$source]}""")
                    path.endsWith("/stream") -> {
                        if (holdStream.get()) streamGate.await(15, java.util.concurrent.TimeUnit.SECONDS)
                        val offset = request.getHeader("Range")?.substringAfter("bytes=")
                            ?.substringBefore('-')?.toIntOrNull() ?: 0
                        if (offset >= bytes.size) return MockResponse().setResponseCode(416)
                        val response = MockResponse().setHeader("Content-Type", "video/mp4")
                            .setHeader("Accept-Ranges", "bytes")
                        if (offset > 0) response.setResponseCode(206)
                            .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                        response.setBody(Buffer().write(bytes, offset, bytes.size - offset))
                    }
                    else -> json("""{"Items":[]}""")
                }
            }
        }
        server.start()
        store.save(Session(server.url("/").toString().trimEnd('/'), "mock-token", "user", "Test"))
        assertNotNull("The playback fixture needs a readable session", store.load())
        store.autoNext = autoNext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var controller: PlaybackController
        var created = false
        try {
            inst.runOnMainSync {
                controller = PlaybackController(context, scope, { true }, { closed.incrementAndGet() },
                    "first", 0, null, null)
                created = true
                controller.player.trackSelectionParameters = controller.player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                controller.onStart()
            }
            try {
                await { controller.state.value.let { it.playing && it.canSeek && it.nextItem != null && !it.loading } }
            } catch (e: AssertionError) {
                throw AssertionError("Playback did not start: ${controller.state.value}; closed=${closed.get()}; requests=${server.requestCount}", e)
            }
            if (verifyCountdown) {
                inst.runOnMainSync {
                    controller.player.seekTo(controller.player.duration - 4800)
                    controller.player.play()
                }
                await { controller.state.value.nextSeconds > 0 }
                inst.runOnMainSync { controller.player.pause() }
                SystemClock.sleep(500)
                val paused = controller.state.value.nextSeconds
                SystemClock.sleep(5500)
                assertEquals("first", controller.state.value.itemId)
                assertEquals(paused, controller.state.value.nextSeconds)
                holdStream.set(true)
                inst.runOnMainSync {
                    controller.player.stop()
                    controller.player.prepare()
                    controller.player.play()
                }
                await { controller.state.value.loading }
                val buffering = controller.state.value.nextSeconds
                SystemClock.sleep(5500)
                assertEquals("first", controller.state.value.itemId)
                assertEquals(buffering, controller.state.value.nextSeconds)
                holdStream.set(false)
                streamGate.countDown()
                await { controller.state.value.let { !it.loading && it.playing } }
                inst.runOnMainSync { controller.player.pause() }
                inst.runOnMainSync { controller.actions.speed(2f) }
                await { controller.state.value.nextSeconds in 1..3 }
                inst.runOnMainSync { controller.actions.seek(30000) }
                await { controller.state.value.nextSeconds == 0 }
                inst.runOnMainSync {
                    controller.player.seekTo(controller.player.duration - 3500)
                    controller.player.play()
                }
                await { controller.state.value.nextSeconds > 0 }
                await { controller.state.value.let { it.itemId == "second" && it.playing } }
                assertEquals(0, closed.get())
                return
            }
            inst.runOnMainSync {
                if (nextHalfOffsetMs == null)
                    controller.actions.setSkipPreferences(SkipPreferences(automatic = automatic, outroDurationMs = outroDurationMs))
                controller.player.pause()
                val duration = controller.player.duration
                val offerPosition = nextHalfOffsetMs?.let { duration / 2 + it } ?: (duration - outroDurationMs - 2800)
                if (autoNext || nextHalfOffsetMs != null) controller.player.seekTo(offerPosition)
                // The normal seek action also dismisses the end-of-video countdown.
                else controller.actions.seek(offerPosition)
            }
            if (nextHalfOffsetMs == null) await { controller.state.value.navigation.offerSeconds == 3 }
            else await { controller.state.value.let { !it.loading && it.canSeek && !it.playing } }
            assertEquals("first", controller.state.value.itemId)
            assertEquals(0, controller.state.value.nextSeconds)
            assertEquals(0, closed.get())
            inst.runOnMainSync {
                if (nextHalfOffsetMs != null) assertEquals("Use the exact player position at the halfway boundary",
                    controller.player.duration / 2 + nextHalfOffsetMs, controller.player.currentPosition)
                when {
                    pickEpisode -> controller.actions.selectEpisode(requireNotNull(controller.state.value.nextItem))
                    nextHalfOffsetMs != null -> controller.actions.nextNow()
                    automatic -> controller.player.play()
                    else -> controller.actions.skipSegment()
                }
            }
            await {
                val ui = controller.state.value
                assertEquals("Outro switching must not show the normal five-second countdown", 0, ui.nextSeconds)
                ui.itemId == "second" && ui.playing && !ui.loading
            }
            assertEquals(0, closed.get())
            assertEquals(0, controller.state.value.nextSeconds)
            assertNull(controller.state.value.error)
            await { events.contains("stopped:first") }
            if (nextHalfOffsetMs != null && nextHalfOffsetMs > 0 && !pickEpisode) {
                val expected = if (failFirstMark) 2 else 1
                try { await { markAttempts.get() == expected } }
                catch (e: AssertionError) { throw AssertionError("Expected $expected marks, got ${markAttempts.get()}; events=$events", e) }
                assertEquals("stopped:first", events.first())
                assertTrue(events.filter { it.startsWith("watched:") }.all { it == "watched:first" })
            } else assertEquals(0, markAttempts.get())
        } finally {
            streamGate.countDown()
            inst.runOnMainSync { if (created) controller.release() }
            scope.cancel()
            store.autoNext = previousAutoNext
            if (previousSession == null) store.clear() else store.save(previousSession)
            server.shutdown()
            java.net.ProxySelector.setDefault(previousProxy)
        }
    }

    @Test
    fun actualDecodeResumeSubtitleRemoteAndHlsFallback() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val assets = inst.context.assets
        val reports = ConcurrentLinkedQueue<Pair<String, JSONObject>>()
        val playbackSequence = java.util.concurrent.atomic.AtomicInteger()
        val stoppedEncodings = ConcurrentLinkedQueue<String>()
        val source =
            """{"Id":"source","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Video","Index":0,"Codec":"h264","Width":320,"Height":180,"VideoRangeType":"SDR"},{"Type":"Audio","Index":1,"Codec":"aac","Channels":2,"DisplayTitle":"测试音轨"},{"Type":"Subtitle","Index":2,"Codec":"subrip","IsExternal":true,"Language":"chi","DisplayTitle":"测试字幕"}]}"""
        val server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl?.encodedPath.orEmpty()
                    if (path.contains("/Sessions/Playing")) {
                        reports.add(path to JSONObject(request.body.readUtf8()))
                        return MockResponse().setResponseCode(204)
                    }
                    if (path.endsWith("/ActiveEncodings")) {
                        stoppedEncodings.add(
                            request.requestUrl?.queryParameter("PlaySessionId").orEmpty()
                        )
                        return MockResponse().setResponseCode(204)
                    }
                    if (path.endsWith("/Items/movie"))
                        return json(
                            """{"Id":"movie","Name":"播放测试","Type":"Movie","RunTimeTicks":600000000,"MediaSources":[$source]}"""
                        )
                    if (path.endsWith("/PlaybackInfo")) {
                        val body = JSONObject(request.body.readUtf8())
                        val compatible = !body.optBoolean("EnableDirectPlay", true)
                        val media = JSONObject(source).put("SupportsDirectPlay", !compatible)
                        if (compatible)
                            media.put(
                                "TranscodingUrl",
                                "/jellyfin/Videos/movie/master.m3u8?StartTimeTicks=${body.optLong("StartTimeTicks")}",
                            )
                        return json(
                            JSONObject()
                                .put(
                                    "PlaySessionId",
                                    (if (compatible) "hls-session-" else "direct-session-") +
                                        playbackSequence.incrementAndGet(),
                                )
                                .put("MediaSources", org.json.JSONArray().put(media))
                                .toString()
                        )
                    }
                    if (path.endsWith("/Stream.srt"))
                        return MockResponse()
                            .setHeader("Content-Type", "application/x-subrip")
                            .setBody("1\n00:00:00,000 --> 00:00:59,000\n字幕测试\n")
                    val asset =
                        when {
                            path.endsWith("/stream") -> "sample.mp4"
                            path.endsWith("/master.m3u8") -> "master.m3u8"
                            Regex(".*/segment[0-9]{2}\\.ts").matches(path) ->
                                path.substringAfterLast('/')
                            else -> return MockResponse().setResponseCode(404)
                        }
                    val bytes = assets.open(asset).use { it.readBytes() }
                    val range =
                        request
                            .getHeader("Range")
                            ?.substringAfter("bytes=")
                            ?.substringBefore('-')
                            ?.toIntOrNull() ?: 0
                    val response =
                        MockResponse()
                            .setHeader("Accept-Ranges", "bytes")
                            .setHeader(
                                "Content-Type",
                                when (asset.substringAfterLast('.')) {
                                    "mp4" -> "video/mp4"
                                    "ts" -> "video/mp2t"
                                    else -> "application/vnd.apple.mpegurl"
                                },
                            )
                    if (range > 0)
                        response
                            .setResponseCode(206)
                            .setHeader(
                                "Content-Range",
                                "bytes $range-${bytes.lastIndex}/${bytes.size}",
                            )
                    return response.setBody(Buffer().write(bytes, range, bytes.size - range))
                }

                fun json(text: String) =
                    MockResponse().setHeader("Content-Type", "application/json").setBody(text)
            }
        server.start()
        val store = SessionStore(context)
        store.save(
            Session(server.url("/jellyfin").toString().trimEnd('/'), "mock-token", "user", "Player")
        )
        val intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra("itemId", "movie")
                .putExtra("startTicks", 50_000_000L)
        val scenario = ActivityScenario.launch<PlayerActivity>(intent)
        try {
            await {
                var ready = false
                scenario.onActivity {
                    ready = playerView(it.window.decorView)?.player?.isPlaying == true
                }
                ready
            }
            var position = 0L
            scenario.onActivity {
                position = playerView(it.window.decorView)!!.player!!.currentPosition
            }
            assertTrue("Resume must seek beyond 5 seconds", position >= 5000)
            await {
                reports.any {
                    it.first.endsWith("/Playing") &&
                        it.second.optLong("PositionTicks") >= 50_000_000L
                }
            }
            val device = UiDevice.getInstance(inst)
            device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
            await {
                var paused = false
                scenario.onActivity {
                    paused = playerView(it.window.decorView)?.player?.playWhenReady == false
                }
                paused
            }
            val startsBeforeQuality = reports.count { it.first.endsWith("/Playing") }
            device.wait(Until.findObject(By.text("原画")), 5000)!!.click()
            device.wait(Until.findObject(By.text("更稳定")), 5000)!!.click()
            await { reports.count { it.first.endsWith("/Playing") } > startsBeforeQuality }
            assertTrue(
                "Changing quality must preserve pause",
                reports.last { it.first.endsWith("/Playing") }.second.optBoolean("IsPaused"),
            )
            scenario.onActivity {
                assertFalse(playerView(it.window.decorView)!!.player!!.playWhenReady)
            }
            device.wait(Until.findObject(By.text("1.0×")), 5000)!!.click()
            device.wait(Until.findObject(By.text("1.5×")), 5000)!!.click()
            await {
                var changed = false
                scenario.onActivity {
                    changed =
                        playerView(it.window.decorView)!!.player!!.playbackParameters.speed == 1.5f
                }
                changed
            }
            scenario.onActivity {
                assertEquals(
                    1.5f,
                    playerView(it.window.decorView)!!.player!!.playbackParameters.speed,
                    .001f,
                )
                assertFalse(playerView(it.window.decorView)!!.player!!.playWhenReady)
            }
            device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
            await {
                var playing = false
                scenario.onActivity {
                    playing = playerView(it.window.decorView)?.player?.isPlaying == true
                }
                playing
            }
            device.wait(Until.findObject(By.text("测试字幕")), 5000)!!.click()
            device.wait(Until.findObject(By.text("测试字幕")), 5000)!!.click()
            await {
                var selected = false
                scenario.onActivity {
                    selected =
                        playerView(it.window.decorView)
                            ?.player
                            ?.currentTracks
                            ?.isTypeSelected(C.TRACK_TYPE_TEXT) == true
                }
                selected
            }
            await { reports.any { it.second.optInt("SubtitleStreamIndex", -1) == 2 } }
            device.wait(Until.findObject(By.desc("播放设置")), 5000)!!.click()
            device.wait(Until.findObject(By.text("兼容播放")), 5000)!!.click()
            await {
                reports.any {
                    it.first.endsWith("/Playing") &&
                        it.second.optString("PlayMethod") == "Transcode"
                }
            }
            device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
            await {
                var paused = false
                scenario.onActivity {
                    paused = playerView(it.window.decorView)!!.player!!.playWhenReady == false
                }
                paused
            }
            val stopsBeforeBackground = reports.count { it.first.endsWith("/Stopped") }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertEquals(0, playerView(it.window.decorView)!!.player!!.mediaItemCount)
            }
            await {
                stoppedEncodings.any { it.startsWith("hls-session-") } &&
                    reports.count { it.first.endsWith("/Stopped") } > stopsBeforeBackground
            }
            val startsBeforeForeground = reports.count { it.first.endsWith("/Playing") }
            scenario.moveToState(Lifecycle.State.RESUMED)
            await { reports.count { it.first.endsWith("/Playing") } > startsBeforeForeground }
            scenario.onActivity {
                val player = playerView(it.window.decorView)!!.player!!
                assertFalse("Returning to foreground preserves pause", player.playWhenReady)
                assertTrue(
                    "Returning to foreground preserves resume position",
                    player.currentPosition >= 5000,
                )
            }
            scenario.onActivity {
                position = playerView(it.window.decorView)!!.player!!.currentPosition
            }
            assertTrue("HLS fallback must retain playback position", position >= 5000)
            scenario.close()
            await {
                reports.any {
                    it.first.endsWith("/Stopped") &&
                        it.second.optString("PlaySessionId").startsWith("hls-session-") &&
                        it.second.optLong("PositionTicks") >= 50_000_000L
                }
            }
        } finally {
            scenario.close()
            server.shutdown()
            store.clear()
        }
    }

    private fun playerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) playerView(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!check()) {
            if (SystemClock.uptimeMillis() > end) throw AssertionError("Player condition timed out")
            SystemClock.sleep(100)
        }
    }
}
