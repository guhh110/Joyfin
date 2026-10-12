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
