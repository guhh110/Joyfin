@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.joyfin.data.Session
import dev.joyfin.data.SessionStore
import dev.joyfin.playback.PlaybackController
import dev.joyfin.playback.SubtitleTrackId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a disposable device: uses a mock login and generated video fixture. */
@RunWith(AndroidJUnit4::class)
class SubtitleSwitchTest {
    @Test
    fun textSwitchPreservesPauseAndSessionWithoutAdvancingEpisode() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val assets = inst.context.assets
        val negotiationCount = AtomicInteger()
        val stoppedCount = AtomicInteger()
        val closeCount = AtomicInteger()
        val source =
            """{"Id":"source","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Video","Index":0,"Codec":"h264","Width":320,"Height":180},{"Type":"Audio","Index":1,"Codec":"aac","Channels":2},{"Type":"Subtitle","Index":2,"Codec":"ass","DisplayTitle":"ASS test","Language":"eng"},{"Type":"Subtitle","Index":3,"Codec":"subrip","DisplayTitle":"SRT test","Language":"eng"}]}"""
        val episode =
            """{"Id":"episode","Name":"One","Type":"Episode","SeriesId":"series","SeasonId":"season","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":600000000,"MediaSources":[$source]}"""
        val server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith("/PlaybackInfo")) {
                        negotiationCount.incrementAndGet()
                        return json("""{"PlaySessionId":"same-session","MediaSources":[$source]}""")
                    }
                    if (path.endsWith("/Items/episode")) return json(episode)
                    if (path.endsWith("/Seasons"))
                        return json(
                            """{"Items":[{"Id":"season","Type":"Season","IndexNumber":1}]}""",
                        )
                    if (path.endsWith("/Episodes"))
                        return json(
                            """{"Items":[$episode,{"Id":"next","Name":"Two","Type":"Episode","SeriesId":"series","SeasonId":"season","IndexNumber":2}]}""",
                        )
                    if (path.endsWith("/Stream.ass"))
                        return MockResponse()
                            .setBody(
                                assets.open("styled.ass").bufferedReader().use { it.readText() },
                            )
                    if (path.endsWith("/Stream.srt"))
                        return MockResponse()
                            .setBody("1\n00:00:00,000 --> 00:00:59,000\nSRT test\n")
                    if (path.endsWith("/stream")) {
                        val bytes = assets.open("sample.mp4").use { it.readBytes() }
                        val offset =
                            request
                                .getHeader("Range")
                                ?.substringAfter("bytes=")
                                ?.substringBefore('-')
                                ?.toIntOrNull() ?: 0
                        return MockResponse()
                            .setHeader("Content-Type", "video/mp4")
                            .setHeader("Accept-Ranges", "bytes")
                            .apply {
                                if (offset > 0)
                                    setResponseCode(206)
                                        .setHeader(
                                            "Content-Range",
                                            "bytes $offset-${bytes.lastIndex}/${bytes.size}",
                                        )
                            }
                            .setBody(Buffer().write(bytes, offset, bytes.size - offset))
                    }
                    if (path.endsWith("/Stopped")) stoppedCount.incrementAndGet()
                    return MockResponse().setResponseCode(204)
                }

                private fun json(body: String) =
                    MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        server.start()
        val store = SessionStore(context)
        store.save(Session(server.url("/jellyfin").toString().trimEnd('/'), "mock", "user", "Test"))
        val previousAutoNext = store.autoNext
        store.autoNext = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var controller: PlaybackController
        inst.runOnMainSync {
            controller =
                PlaybackController(
                    context,
                    scope,
                    { true },
                    { closeCount.incrementAndGet() },
                    "episode",
                    50_000_000,
                    null,
                    null,
                )
            controller.bindSubtitles(SubtitleView(context))
            controller.onStart()
        }
        try {
            await {
                var ready = false
                inst.runOnMainSync {
                    ready =
                        controller.player.playbackState == Player.STATE_READY &&
                            controller.state.value.nextItem?.id == "next"
                }
                ready
            }
            inst.runOnMainSync { controller.player.pause() }
            val negotiations = negotiationCount.get()
            val stops = stoppedCount.get()
            for (index in listOf(3, -1, 2)) {
                inst.runOnMainSync { controller.actions.selectSubtitle(index) }
                await {
                    var selected = false
                    inst.runOnMainSync {
                        selected =
                            controller.state.value.subtitleIndex == index &&
                                (if (index < 0)
                                    !controller.player.currentTracks.isTypeSelected(
                                        C.TRACK_TYPE_TEXT,
                                    )
                                else
                                    controller.player.currentTracks.groups.any { group ->
                                        (0 until group.length).any {
                                            group.isTrackSelected(it) &&
                                                SubtitleTrackId.matches(
                                                    group.getTrackFormat(it).id,
                                                    index,
                                                )
                                        }
                                    })
                    }
                    selected
                }
                inst.runOnMainSync {
                    assertFalse(controller.player.playWhenReady)
                    assertEquals("episode", controller.state.value.itemId)
                    assertEquals(0, controller.state.value.nextSeconds)
                    assertTrue(controller.player.currentPosition in 5000..6500)
                    assertNull(controller.player.playerError)
                }
            }
            assertEquals(
                "Text selection must not renegotiate video",
                negotiations,
                negotiationCount.get(),
            )
            assertEquals("Text selection must not stop the session", stops, stoppedCount.get())
            assertEquals(0, closeCount.get())
        } finally {
            inst.runOnMainSync {
                controller.onStop()
                controller.release()
            }
            scope.cancel()
            store.autoNext = previousAutoNext
            store.clear()
            server.shutdown()
        }
    }

    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (!check()) {
            if (SystemClock.uptimeMillis() > end)
                throw AssertionError("Subtitle selection timed out")
            SystemClock.sleep(100)
        }
    }
}
