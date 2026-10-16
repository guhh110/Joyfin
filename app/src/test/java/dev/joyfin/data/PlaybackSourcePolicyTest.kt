package dev.joyfin.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The wire boundary is tested with local HTTP responses, never a real Jellyfin server. */
class PlaybackSourcePolicyTest {
    private lateinit var server: MockWebServer
    private lateinit var api: JellyfinApi
    private val source = MediaSource(id = "main", container = "mkv", supportsDirectPlay = true,
        defaultAudioIndex = 1, streams = listOf(MediaStream(type = "Audio", index = 1, codec = "dts", channels = 6)))
    private val movie = Media("movie", "Movie", "Movie", sources = listOf(source))
    private val profile = DeviceProfileDto(name = "Selected route")

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = JellyfinApi("test-device").apply {
            session = Session(server.url("/jellyfin").toString(), "test-token", "viewer", "Viewer")
        }
    }
    @After fun cleanup() { server.shutdown() }
    private fun enqueue(vararg sources: MediaSource, error: String? = null) {
        server.enqueue(MockResponse().setBody(AppJson.encodeToString(PlaybackResponse(
            errorCode = error, sources = sources.toList(), sessionId = "play-session"))))
    }
    private fun request(): JSONObject = JSONObject(requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)).body.readUtf8())

    @Test fun audioOnlyConversionPreservesVideoTrackPositionAndChosenVersion() = runBlocking {
        enqueue(source.copy(transcodingUrl = "/jellyfin/main.m3u8"))
        val plan = api.playback(movie, DeviceProfileDto(), 123_000_000, bitrate = 9_000_000,
            audio = 1, subtitle = -1, sourcePolicy = { selected, index ->
                assertEquals("main", selected.id)
                assertEquals(1, index)
                PlaybackSourcePolicy(profile, audioTranscode = true, maxAudioChannels = 6)
            })
        val body = request()
        assertFalse(body.getBoolean("EnableDirectPlay"))
        assertFalse(body.getBoolean("EnableDirectStream"))
        assertTrue(body.getBoolean("AllowVideoStreamCopy"))
        assertFalse(body.getBoolean("AllowAudioStreamCopy"))
        assertEquals(6, body.getInt("MaxAudioChannels"))
        assertEquals(123_000_000L, body.getLong("StartTimeTicks"))
        assertEquals(9_000_000, body.getInt("MaxStreamingBitrate"))
        assertEquals("main", body.getString("MediaSourceId"))
        assertEquals("Selected route", body.getJSONObject("DeviceProfile").getString("Name"))
        assertEquals("Transcode", plan.method)
        assertEquals(1, plan.audioIndex)
        assertEquals("0", plan.url.toHttpUrl().queryParameter("StartTimeTicks"))
    }

    @Test fun hdrConversionDisablesVideoCopyWhileKeepingSupportedAudioCopy() = runBlocking {
        enqueue(source.copy(transcodingUrl = "/jellyfin/main.m3u8"))
        api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, videoTranscode = true) })
        val body = request()
        assertFalse(body.getBoolean("EnableDirectPlay"))
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertTrue(body.getBoolean("AllowAudioStreamCopy"))
    }

    @Test fun explicitCompatibleModeStillOverridesAutomaticCopyPermissions() = runBlocking {
        enqueue(source.copy(transcodingUrl = "/jellyfin/main.m3u8"))
        api.playback(movie, profile, 0, compatible = true, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile) })
        val body = request()
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertFalse(body.getBoolean("AllowAudioStreamCopy"))
    }

    @Test fun discMayUseServerMainFilmStreamEvenIfDirectFileSupportIsClaimed() = runBlocking {
        enqueue(source.copy(container = "iso", supportsDirectStream = true, directStreamUrl = "/jellyfin/main.m2ts"))
        val plan = api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, discSource = true) })
        assertFalse(request().getBoolean("EnableDirectPlay"))
        assertEquals("DirectStream", plan.method)
        assertEquals("/jellyfin/main.m2ts", plan.url.toHttpUrl().encodedPath)
        assertFalse(plan.url.contains("Static=true"))
    }

    @Test fun discWithoutUsableMainStreamDoesNotFallBackToStaticIsoFile() = runBlocking {
        enqueue(source.copy(container = "iso"))
        val failure = assertThrows(IOException::class.java) { runBlocking {
            api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, discSource = true) })
        } }
        assertTrue(failure.message!!.contains("原盘主影片流"))
        assertEquals(1, server.requestCount)
    }

    @Test fun rawDiscAndPlaylistUrlsAreRejectedEvenIfReturnedAsTranscodeUrls() = runBlocking {
        for (extension in listOf("ISO", "bdmv", "mpls", "ifo")) {
            enqueue(source.copy(transcodingUrl = "/jellyfin/raw.$extension?token=test"))
            val failure = assertThrows(IOException::class.java) { runBlocking {
                api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, discSource = true) })
            } }
            assertTrue(failure.message!!.contains("MKV Remux"))
        }
    }

    @Test fun discServerErrorHasActionableMainStreamFeedback() = runBlocking {
        enqueue(source, error = "NoCompatibleStream")
        val failure = assertThrows(IOException::class.java) { runBlocking {
            api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, discSource = true) })
        } }
        assertTrue(failure.message!!.contains("原盘主影片流"))
    }

    @Test fun unknownSourceIsDiscoveredBeforeApplyingItsSpecificPolicy() = runBlocking {
        enqueue(source)
        enqueue(source.copy(transcodingUrl = "/jellyfin/main.m3u8"))
        var calls = 0
        val plan = api.playback(movie.copy(sources = emptyList()), profile, 0, audio = 1,
            sourcePolicy = { selected, index ->
                calls++
                assertEquals("main", selected.id)
                assertEquals(1, index)
                PlaybackSourcePolicy(profile, audioTranscode = true, maxAudioChannels = 2)
            })
        assertFalse(request().getBoolean("AutoOpenLiveStream"))
        val final = request()
        assertEquals("main", final.getString("MediaSourceId"))
        assertEquals(2, final.getInt("MaxAudioChannels"))
        assertFalse(final.getBoolean("AllowAudioStreamCopy"))
        assertEquals(1, calls)
        assertEquals("Transcode", plan.method)
    }

    @Test fun serverReturningDifferentVersionDoesNotSilentlyApplyIt() = runBlocking {
        enqueue(source)
        val alternate = source.copy(id = "alternate")
        assertThrows(IOException::class.java) { runBlocking {
            api.playback(movie.copy(sources = listOf(source, alternate)), profile, 0, sourceId = "alternate",
                sourcePolicy = { selected, _ ->
                    assertEquals("alternate", selected.id)
                    PlaybackSourcePolicy(profile)
                })
        } }
        assertEquals("alternate", request().getString("MediaSourceId"))
    }

    @Test fun cancelDuringCapabilityNegotiationCannotSendALatePlaybackRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            api.playback(movie, profile, 0, sourcePolicy = { _, _ ->
                entered.complete(Unit)
                awaitCancellation()
            })
        }
        withTimeout(3_000) { entered.await() }
        job.cancelAndJoin()
        assertEquals(0, server.requestCount)
    }

    @Test fun authenticationFailureIsNotRewrittenAsAnUnsupportedDiscError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val failure = assertThrows(ApiException::class.java) { runBlocking {
            api.playback(movie, profile, 0, sourcePolicy = { _, _ -> PlaybackSourcePolicy(profile, discSource = true) })
        } }
        assertEquals(401, failure.status)
        assertEquals(1, server.requestCount)
    }
}
