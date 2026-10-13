package dev.joyfin

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.joyfin.data.*
import dev.joyfin.playback.PlaybackController
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackPreferencesFlowTest {
    @Test
    fun restoresSeriesSettingsBeforeNegotiationAndDoesNotSaveFailedChanges() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val store = SessionStore(context)
        val previous = store.load()
        val requests = LinkedBlockingQueue<JSONObject>()
        val source =
            JSONObject(
                """{"Id":"source","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Audio","Index":8,"Language":"jpn","Codec":"aac"},{"Type":"Subtitle","Index":9,"Language":"eng","Codec":"subrip"}]}"""
            )
        val server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith("/PlaybackInfo")) {
                        requests.add(JSONObject(request.body.readUtf8()))
                        // Negotiation failure must never commit a pending playback preference.
                        return MockResponse().setResponseCode(500)
                    }
                    if (path.contains("/Items/")) {
                        val id = path.substringAfterLast('/')
                        return json(
                            JSONObject()
                                .put("Id", id)
                                .put("Type", "Episode")
                                .put("Name", id)
                                .put("SeriesId", if (id == "other") "other-series" else "series")
                                .put("MediaSources", org.json.JSONArray().put(source))
                                .toString()
                        )
                    }
                    return json("""{"Items":[]}""")
                }

                private fun json(body: String) =
                    MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        server.start()
        val session = Session(server.url("/").toString().trimEnd('/'), "mock-token", "user", "Test")
        val first = Media("first", "First", "Episode", seriesId = "series")
        val preferences = PlaybackPreferenceStore(context)
        val audio =
            AppJson.decodeFromString<MediaStream>(
                """{"Type":"Audio","Index":1,"Language":"jpn","Codec":"aac"}"""
            )
        val saved =
            PlaybackTrackPreferences(bitrate = 4_000_000, compatible = true)
                .selected("Audio", 1, listOf(audio))
                .selected("Subtitle", -1, emptyList())
        preferences.save(session, first, saved)
        store.save(session)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var controller: PlaybackController
        fun open(id: String) {
            inst.runOnMainSync {
                controller = PlaybackController(context, scope, { true }, {}, id, 0, null, null)
            }
        }
        fun request(): JSONObject = requireNotNull(requests.poll(15, TimeUnit.SECONDS))
        fun waitForFailure() {
            val deadline = SystemClock.uptimeMillis() + 15000
            while (controller.state.value.error == null) {
                check(SystemClock.uptimeMillis() < deadline) { "Expected negotiation failure" }
                SystemClock.sleep(50)
            }
        }
        fun assertSavedRequest(body: JSONObject) {
            assertFalse(body.getBoolean("EnableDirectPlay"))
            assertFalse(body.getBoolean("AllowVideoStreamCopy"))
            assertEquals(4_000_000, body.getInt("MaxStreamingBitrate"))
            assertEquals(8, body.getInt("AudioStreamIndex"))
            assertEquals(-1, body.getInt("SubtitleStreamIndex"))
        }
        try {
            open("first")
            assertSavedRequest(request())
            waitForFailure()
            inst.runOnMainSync { controller.actions.selectCompatibility(false) }
            assertTrue(request().getBoolean("EnableDirectPlay"))
            waitForFailure()
            assertEquals(saved, preferences.load(session, first))

            inst.runOnMainSync { controller.actions.selectEpisode(first.copy(id = "second")) }
            assertSavedRequest(request())
            waitForFailure()
            inst.runOnMainSync { controller.release() }
            scope.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            open("second")
            assertSavedRequest(request())
            waitForFailure()

            inst.runOnMainSync {
                controller.actions.selectEpisode(
                    first.copy(id = "other", seriesId = "other-series")
                )
            }
            val other = request()
            assertTrue(other.getBoolean("EnableDirectPlay"))
            assertEquals(
                dev.joyfin.data.PlaybackQuality.ORIGINAL,
                other.getInt("MaxStreamingBitrate"),
            )
        } finally {
            inst.runOnMainSync { controller.release() }
            scope.cancel()
            if (previous == null) store.clear() else store.save(previous)
            server.shutdown()
        }
    }
}
