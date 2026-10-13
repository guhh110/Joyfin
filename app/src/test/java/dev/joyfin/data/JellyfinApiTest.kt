package dev.joyfin.data

import dev.joyfin.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JellyfinApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: JellyfinApi
    private val item = Media("movie-id", "Movie", "Movie")

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        api = JellyfinApi("device-id")
        api.session =
            Session(
                server.url("/jellyfin/").toString().trimEnd('/'),
                "test-token",
                "user-id",
                "Viewer",
            )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun playbackResponse(
        direct: Boolean = true,
        transcode: String? = null,
        streams: String = "[]",
    ) {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"PlaySessionId":"play-id","MediaSources":[{"Id":"source-id","SupportsDirectPlay":$direct,"TranscodingUrl":${if (transcode == null) "null" else "\"$transcode\""},"MediaStreams":$streams}]}"""
                )
        )
    }

    @Test
    fun semanticPreferencesResolveAfterDiscoveryWithoutForcingTextTranscode() = runBlocking {
        val streams =
            """[{"Type":"Audio","Index":12,"Language":"jpn"},{"Type":"Subtitle","Index":9,"Language":"eng","Codec":"ass"}]"""
        val preferences =
            PlaybackTrackPreferences()
                .selected(
                    "Audio",
                    1,
                    listOf(
                        AppJson.decodeFromString<MediaStream>(
                            """{"Type":"Audio","Index":1,"Language":"ja"}"""
                        )
                    ),
                )
                .selected(
                    "Subtitle",
                    2,
                    listOf(
                        AppJson.decodeFromString<MediaStream>(
                            """{"Type":"Subtitle","Index":2,"Language":"en","Codec":"ass"}"""
                        )
                    ),
                )
        playbackResponse(streams = streams)
        playbackResponse(streams = streams)
        val plan =
            api.playback(item, DeviceProfileDto(), 0, subtitle = null, preferences = preferences)
        assertEquals("false", server.takeRequest().requestUrl!!.queryParameter("IsPlayback"))
        val request = server.takeRequest()
        assertEquals("true", request.requestUrl!!.queryParameter("IsPlayback"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(12, body.getInt("AudioStreamIndex"))
        assertEquals(9, body.getInt("SubtitleStreamIndex"))
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertEquals(12, plan.audioIndex)
        assertEquals(9, plan.subtitleIndex)
        assertEquals("DirectPlay", plan.method)
    }

    @Test
    fun explicitIndicesOverridePreferencesAndOffSurvivesSourceDiscovery() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":9,"Language":"eng","Codec":"ass"}]"""
        val preferences = PlaybackTrackPreferences(subtitlesOff = true)
        playbackResponse(streams = streams)
        playbackResponse(streams = streams)
        val plan =
            api.playback(item, DeviceProfileDto(), 0, subtitle = null, preferences = preferences)
        server.takeRequest()
        assertEquals(
            -1,
            JSONObject(server.takeRequest().body.readUtf8()).getInt("SubtitleStreamIndex"),
        )
        assertEquals(-1, plan.subtitleIndex)
        playbackResponse(streams = streams)
        val explicit =
            api.playback(
                item.copy(sources = listOf(plan.source)),
                DeviceProfileDto(),
                0,
                subtitle = 9,
                preferences = preferences,
            )
        assertEquals(
            9,
            JSONObject(server.takeRequest().body.readUtf8()).getInt("SubtitleStreamIndex"),
        )
        assertEquals(9, explicit.subtitleIndex)
    }

    @Test
    fun unmatchedPreferenceFallsBackAndExplicitAudioWins() = runBlocking {
        val streams =
            """[{"Type":"Audio","Index":1,"Language":"eng"},{"Type":"Audio","Index":2,"Language":"jpn"},{"Type":"Subtitle","Index":8,"Language":"jpn","Codec":"ass"}]"""
        val preferences =
            PlaybackTrackPreferences()
                .selected(
                    "Audio",
                    2,
                    listOf(
                        AppJson.decodeFromString<MediaStream>(
                            """{"Type":"Audio","Index":2,"Language":"ja"}"""
                        )
                    ),
                )
                .selected(
                    "Subtitle",
                    3,
                    listOf(
                        AppJson.decodeFromString<MediaStream>(
                            """{"Type":"Subtitle","Index":3,"Language":"en","Codec":"ass"}"""
                        )
                    ),
                )
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":$streams}"""
                        )
                    )
            )
        playbackResponse(streams = streams)
        val plan =
            api.playback(
                media,
                DeviceProfileDto(),
                0,
                audio = 1,
                subtitle = null,
                preferences = preferences,
            )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body.getInt("AudioStreamIndex"))
        assertEquals(8, body.getInt("SubtitleStreamIndex"))
        assertEquals(1, plan.audioIndex)
    }

    @Test
    fun rememberedBitmapSubtitleStillRequiresBurnIn() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":5,"Language":"eng","Codec":"pgssub"}]"""
        val preferences =
            PlaybackTrackPreferences()
                .selected(
                    "Subtitle",
                    1,
                    listOf(
                        AppJson.decodeFromString<MediaStream>(
                            """{"Type":"Subtitle","Index":1,"Language":"en","Codec":"pgssub"}"""
                        )
                    ),
                )
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":$streams}"""
                        )
                    )
            )
        playbackResponse(direct = false, transcode = "/jellyfin/video.m3u8", streams = streams)
        val plan =
            api.playback(media, DeviceProfileDto(), 0, subtitle = null, preferences = preferences)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(5, body.getInt("SubtitleStreamIndex"))
        assertFalse(body.getBoolean("EnableDirectPlay"))
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertEquals("Transcode", plan.method)
    }

    @Test
    fun restoredBitmapSelectionIsNegotiatedAfterMissingSourceDiscovery() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":5,"Codec":"pgssub"}]"""
        playbackResponse(streams = streams)
        playbackResponse(direct = false, transcode = "/jellyfin/video.m3u8", streams = streams)
        val plan = api.playback(item, DeviceProfileDto(), 0, subtitle = 5)
        assertEquals("false", server.takeRequest().requestUrl!!.queryParameter("IsPlayback"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(5, body.getInt("SubtitleStreamIndex"))
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertEquals("Transcode", plan.method)
    }

    @Test
    fun firstAssSubtitleIsExternalAndDoesNotForceVideoTranscode() = runBlocking {
        val streams =
            """[{"Type":"Subtitle","Index":7,"Codec":"ass"},{"Type":"Subtitle","Index":2,"Codec":"subrip"}]"""
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":$streams}"""
                        )
                    )
            )
        playbackResponse(streams = streams)
        val plan = api.playback(media, DeviceProfileDto(), 0, subtitle = null)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(7, body.getInt("SubtitleStreamIndex"))
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertTrue(body.getBoolean("AllowVideoStreamCopy"))
        assertEquals("DirectPlay", plan.method)
        assertEquals(listOf(7, 2), plan.subtitles.map { it.index })
        assertEquals("text/x-ssa", plan.subtitles.first().mime)
        assertTrue(plan.subtitles.first().url.endsWith("/7/Stream.ass"))
    }

    @Test
    fun autoSelectionDiscoversMissingSourcesWithoutStartingUnusedPlayback() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":3,"Codec":"subrip"}]"""
        playbackResponse(streams = streams)
        playbackResponse(streams = streams)
        val plan = api.playback(item, DeviceProfileDto(), 0, subtitle = null)
        val discovery = server.takeRequest()
        assertEquals("false", discovery.requestUrl!!.queryParameter("IsPlayback"))
        assertFalse(JSONObject(discovery.body.readUtf8()).getBoolean("AutoOpenLiveStream"))
        val negotiated = server.takeRequest()
        assertEquals("true", negotiated.requestUrl!!.queryParameter("IsPlayback"))
        assertEquals(3, JSONObject(negotiated.body.readUtf8()).getInt("SubtitleStreamIndex"))
        assertEquals(3, plan.subtitleIndex)
    }

    @Test
    fun manualOffIsPreservedEvenWhenSubtitlesExist() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":3,"Codec":"subrip"}]"""
        playbackResponse(streams = streams)
        val plan = api.playback(item, DeviceProfileDto(), 0, subtitle = -1)
        assertEquals(
            -1,
            JSONObject(server.takeRequest().body.readUtf8()).getInt("SubtitleStreamIndex"),
        )
        assertEquals(-1, plan.subtitleIndex)
        assertEquals(listOf(3), plan.subtitles.map { it.index })
    }

    @Test
    fun externalTextIsStillAvailableWhenVideoNeedsTranscoding() = runBlocking {
        playbackResponse(
            direct = false,
            transcode = "/jellyfin/video.m3u8",
            streams =
                """[{"Type":"Subtitle","Index":4,"Codec":"ssa","DeliveryMethod":"External"}]""",
        )
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":[{"Type":"Subtitle","Index":4,"Codec":"ssa"}]}"""
                        )
                    )
            )
        val plan = api.playback(media, DeviceProfileDto(), 0, subtitle = 4)
        assertEquals("Transcode", plan.method)
        assertEquals("text/x-ssa", plan.subtitles.single().mime)
    }

    @Test
    fun bitmapSubtitleBurnInDoesNotForceAudioConversion() = runBlocking {
        val streams =
            """[{"Type":"Subtitle","Index":3,"Codec":"pgssub","DeliveryMethod":"Encode"}]"""
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":$streams}"""
                        )
                    )
            )
        playbackResponse(direct = false, transcode = "/jellyfin/video.m3u8", streams = streams)
        val plan = api.playback(media, DeviceProfileDto(), 0, subtitle = 3)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(body.getBoolean("EnableDirectPlay"))
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertTrue(body.getBoolean("AllowAudioStreamCopy"))
        assertTrue(plan.subtitles.isEmpty())
    }

    @Test
    fun connectionPreservesProxyBaseAndOmitsSavedToken() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ServerName":"Home"}"""))
        api.connect(server.url("/jellyfin/").toString())
        val request = server.takeRequest()
        assertEquals("/jellyfin/System/Info/Public", request.path)
        assertFalse(request.getHeader("Authorization")!!.contains("test-token"))
    }

    @Test
    fun loginUsesNameAndPasswordAndReturnsUser() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"AccessToken":"new-token","User":{"Id":"new-user","Name":"Viewer"}}""")
        )
        val session = api.login(server.url("/jellyfin").toString(), "Viewer", "secret")
        val request = server.takeRequest()
        assertEquals("/jellyfin/Users/AuthenticateByName", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Viewer", body.getString("Username"))
        assertEquals("secret", body.getString("Pw"))
        assertEquals("new-token", session.token)
        assertFalse(request.getHeader("Authorization")!!.contains("test-token"))
    }

    @Test
    fun listingCarriesUserParentAndPaging() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"Items":[{"Id":"id","Name":"Example","Type":"Movie","UserData":{"PlaybackPositionTicks":123000}}],"TotalRecordCount":100}"""
                )
        )
        val page = api.items("library", start = 40)
        val request = server.takeRequest()
        assertEquals("library", request.requestUrl!!.queryParameter("ParentId"))
        assertEquals("40", request.requestUrl!!.queryParameter("StartIndex"))
        assertEquals("user-id", request.requestUrl!!.queryParameter("UserId"))
        assertTrue(request.getHeader("Authorization")!!.contains("test-token"))
        assertEquals(100, page.total)
        assertEquals(123000L, page.items.first().resumeTicks)
    }

    @Test
    fun homeRegionsUseCurrentUserItemsAndViewsEndpoints() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
        api.resumeVideo()
        api.recentVideo()
        api.videoLibraries()
        val requests = (0..2).map { server.takeRequest() }
        assertEquals(
            listOf("/jellyfin/UserItems/Resume", "/jellyfin/Items/Latest", "/jellyfin/UserViews"),
            requests.map { it.requestUrl!!.encodedPath },
        )
        requests.forEach { assertEquals("user-id", it.requestUrl!!.queryParameter("UserId")) }
    }

    @Test
    fun latestInLibraryUsesScopedGroupedVideoQuery() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """[{"Id":"e1","Name":"One","Type":"Episode","SeriesId":"series","SeriesName":"Show"},{"Id":"e2","Name":"Two","Type":"Episode","SeriesId":"series"},{"Id":"movie","Name":"Movie","Type":"Movie"}]"""
                )
        )
        val items = api.latestInLibrary("children")
        val url = server.takeRequest().requestUrl!!
        assertEquals("children", url.queryParameter("ParentId"))
        assertEquals("true", url.queryParameter("GroupItems"))
        assertEquals("Movie,Episode,Video,MusicVideo", url.queryParameter("IncludeItemTypes"))
        assertEquals("20", url.queryParameter("Limit"))
        assertEquals(listOf("series", "movie"), items.map { it.id })
    }

    @Test
    fun videoLibrariesFilterNonVideoAndKeepServerOrder() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"Items":[{"Id":"anime","Name":"Anime","Type":"CollectionFolder","CollectionType":"tvshows"},{"Id":"music","Type":"CollectionFolder","CollectionType":"music"},{"Id":"children","Name":"Children","Type":"CollectionFolder","CollectionType":"mixed"}]}"""
                )
        )
        assertEquals(listOf("anime", "children"), api.videoLibraries().map { it.id })
    }

    @Test
    fun detailsUseCurrentItemsEndpointWithUserContext() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"Id":"movie-id","Name":"Movie","Type":"Movie"}""")
        )
        api.item("movie-id")
        val request = server.takeRequest()
        assertEquals("/jellyfin/Items/movie-id", request.requestUrl!!.encodedPath)
        assertEquals("user-id", request.requestUrl!!.queryParameter("UserId"))
    }

    @Test
    fun directPlaybackCarriesSourceAndResume() = runBlocking {
        playbackResponse()
        val plan = api.playback(item, DeviceProfileDto(), 320_000_000L)
        val request = server.takeRequest()
        assertEquals(
            "320000000",
            JSONObject(request.body.readUtf8()).getLong("StartTimeTicks").toString(),
        )
        assertEquals("DirectPlay", plan.method)
        assertTrue(plan.url.contains("/jellyfin/Videos/movie-id/stream"))
        assertTrue(plan.url.contains("MediaSourceId=source-id"))
        assertFalse(plan.url.contains("test-token"))
    }

    @Test
    fun compatiblePlaybackDisablesCopyAndKeepsFullHlsTimeline() = runBlocking {
        playbackResponse(false, "/jellyfin/Videos/movie-id/master.m3u8?StartTimeTicks=50000000")
        val plan = api.playback(item, DeviceProfileDto(), 50_000_000, compatible = true)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(body.getBoolean("EnableDirectPlay"))
        assertFalse(body.getBoolean("AllowVideoStreamCopy"))
        assertEquals("Transcode", plan.method)
        assertTrue(plan.url.endsWith("StartTimeTicks=0"))
        assertEquals(50_000_000L, plan.startTicks)
    }

    @Test
    fun selectedTextSubtitleUsesExtractionEndpoint() = runBlocking {
        playbackResponse(
            streams =
                """[{"Type":"Subtitle","Index":3,"Codec":"subrip","Language":"chi","DisplayTitle":"Chinese"}]"""
        )
        val media =
            item.copy(
                sources =
                    listOf(
                        AppJson.decodeFromString<MediaSource>(
                            """{"Id":"source-id","MediaStreams":[{"Type":"Subtitle","Index":3,"Codec":"subrip"}]}"""
                        )
                    )
            )
        val plan = api.playback(media, DeviceProfileDto(), 0, subtitle = 3)
        server.takeRequest()
        assertEquals(1, plan.subtitles.size)
        assertTrue(plan.subtitles.single().url.endsWith("/source-id/Subtitles/3/Stream.srt"))
        assertEquals("application/x-subrip", plan.subtitles.single().mime)
    }

    @Test
    fun missingTranscodeFailsClearly() = runBlocking {
        playbackResponse(false)
        try {
            api.playback(item, DeviceProfileDto(), 0)
            fail("Expected unavailable playback")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("转码"))
        }
    }

    @Test
    fun expiredTokenIsDistinctFromConnectivityError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            api.validateSession()
            fail("Expected expired session")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
    }

    @Test
    fun stopReportIncludesFinalPositionAndNoStartOnlyFields() = runBlocking {
        playbackResponse()
        val plan = api.playback(item, DeviceProfileDto(), 0)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))
        api.report(plan, "Stopped", 900_000_000, true)
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("/jellyfin/Sessions/Playing/Stopped", request.path)
        assertEquals(900_000_000L, body.getLong("PositionTicks"))
        assertEquals("play-id", body.getString("PlaySessionId"))
        assertFalse(body.has("IsPaused"))
    }

    @Test
    fun multichannelPlaybackRequestUsesProfileLimitAndKeepsDirectPlay() = runBlocking {
        playbackResponse(streams = """[{"Type":"Audio","Codec":"eac3","Channels":6}]""")
        val audio = dev.joyfin.playback.AudioPlaybackCapabilities(mapOf("eac3" to 6))
        val plan = api.playback(item, DeviceProfileDto(codecProfiles = audio.codecProfiles()), 0)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(6, body.getInt("MaxAudioChannels"))
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertEquals("DirectPlay", plan.method)
    }

    @Test
    fun cleanupTargetsOnlyItsPlaySessionAndSkipsBlankSession() = runBlocking {
        api.stopTranscoding("")
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(204))
        api.stopTranscoding("specific-session")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/jellyfin/Videos/ActiveEncodings", request.requestUrl!!.encodedPath)
        assertEquals("specific-session", request.requestUrl!!.queryParameter("PlaySessionId"))
        assertEquals("device-id", request.requestUrl!!.queryParameter("DeviceId"))
    }

    @Test
    fun crossOriginPlaybackUrlIsRejected() {
        try {
            api.playbackUrl("https://other.example/video")
            fail("Expected credential protection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不同来源"))
        }
    }

    @Test
    fun normalizationRejectsCredentialsQueriesUnsupportedProtocolsAndInvalidAddresses() {
        for (value in
            listOf(
                "",
                "ftp://host",
                "https://host:99999",
                "https://user:pass@host",
                "https://host/?api_key=secret",
                "https://host/#page",
            )) {
            try {
                JellyfinApi.normalizeServer(value)
                fail("Expected invalid address: $value")
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(
            "https://example.com/jellyfin",
            JellyfinApi.normalizeServer(" https://example.com/jellyfin/ "),
        )
    }

    @Test
    fun normalizationAddsHttpForLanAddressAndPreservesProxyPath() {
        assertEquals("http://192.168.1.8:8096", JellyfinApi.normalizeServer(" 192.168.1.8:8096/ "))
        assertEquals(
            "http://media.example.com/jellyfin",
            JellyfinApi.normalizeServer("media.example.com/jellyfin/"),
        )
        assertEquals(
            "https://media.example.com/jellyfin",
            JellyfinApi.normalizeServer("https://media.example.com/jellyfin/"),
        )
    }

    @Test
    fun cancellingConnectionDoesNotDeliverLateSuccess() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"ServerName":"Delayed"}""")
                .setHeadersDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        var completed = false
        val job = launch {
            api.connect(server.url("/jellyfin").toString())
            completed = true
        }
        val request =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)
            }
        assertNotNull(request)
        job.cancel()
        job.join()
        kotlinx.coroutines.delay(300)
        assertFalse(completed)
    }

    @Test
    fun immersiveMetadataKeepsInheritedImagesAndPeople() {
        val media =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"ep","Name":"Episode","Type":"Episode","SeriesId":"series","SeasonId":"season","SeriesName":"Show","ParentBackdropItemId":"series","ParentBackdropImageTags":["backdrop-tag"],"SeriesPrimaryImageTag":"poster-tag","CommunityRating":8.7,"OfficialRating":"TV-14","PremiereDate":"2025-10-17T00:00:00Z","Genres":["Drama"],"People":[{"Id":"actor","Name":"Actor","Role":"Lead","Type":"Actor","PrimaryImageTag":"face"}],"UserData":{"IsFavorite":true}}"""
                )
            )
        assertEquals("series", media.backdropId)
        assertEquals("backdrop-tag", media.backdropTag)
        assertEquals("series", media.imageId)
        assertEquals("poster-tag", media.imageTag)
        assertEquals("Lead", media.people.single().role)
        assertEquals("2025-10-17", media.premiereDate)
        assertEquals(8.7, media.rating!!, .001)
        assertTrue(media.favorite)
    }

    @Test
    fun missingMetadataNeverRendersLiteralNullOrFakeRating() {
        val media =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"id","Overview":null,"CommunityRating":null,"ImageTags":{"Primary":null},"Genres":[null],"OriginalLanguage":null}"""
                )
            )
        assertEquals("", media.overview)
        assertEquals("", media.language)
        assertEquals("", media.imageTag)
        assertNull(media.rating)
        assertTrue(media.genres.isEmpty())
    }

    @Test
    fun episodeStillUsesOwnIdAndPrimaryEvenWithInheritedBackdrop() {
        val episode =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"e2","Type":"Episode","SeriesId":"series","ImageTags":{"Primary":"still-2"},"ParentBackdropItemId":"series","ParentBackdropImageTags":["backdrop"],"SeriesPrimaryImageTag":"poster"}"""
                )
            )
        val url = api.episodeImage(episode)!!
        assertTrue(url.contains("/Items/e2/Images/Primary"))
        assertTrue(url.contains("Tag=still-2"))
        assertFalse(url.contains("test-token"))
        assertFalse(url.contains("Backdrop"))
    }

    @Test
    fun missingEpisodeStillNeverFallsBackToParentPosterOrBackdrop() {
        val episode =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"e1","Type":"Episode","SeriesId":"series","ParentPrimaryImageItemId":"series","ParentPrimaryImageTag":"poster","ParentBackdropItemId":"series","ParentBackdropImageTags":["backdrop"]}"""
                )
            )
        assertEquals("poster", episode.imageTag)
        assertEquals("", episode.episodeImageTag)
        assertNull(api.episodeImage(episode))
    }

    @Test
    fun individualEpisodeStillTagsRemainDistinct() {
        val first =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"e1","Type":"Episode","ImageTags":{"Primary":"still-1"}}"""
                )
            )
        val second =
            Media.from(
                AppJson.decodeFromString<ItemDto>(
                    """{"Id":"e2","Type":"Episode","ImageTags":{"Primary":"still-2"}}"""
                )
            )
        assertNotEquals(api.episodeImage(first), api.episodeImage(second))
        assertNull(api.episodeImage(Media("series", "Show", "Series", imageTag = "poster")))
    }

    @Test
    fun backdropAndActorImagesPreserveProxyAndNeverEmbedToken() {
        val media = Media("id", "Movie", "Movie", backdropId = "series", backdropTag = "tag")
        val url = api.backdrop(media)
        assertTrue(url.contains("/jellyfin/Items/series/Images/Backdrop/0"))
        assertTrue(url.contains("Tag=tag"))
        assertFalse(url.contains("test-token"))
        assertTrue(
            api.personImage(Person("person-id", "Actor", "", "Actor", "face"))!!.contains(
                "/jellyfin/Items/person-id/Images/Primary"
            )
        )
    }

    @Test
    fun actorQueryUsesPersonIdAndPaging() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"Id":"person-id","Name":"A/B","Type":"Person"}""")
        )
        api.person("A/B")
        val person = server.takeRequest()
        assertEquals("/jellyfin/Persons/A%2FB", person.requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":80}"""))
        api.personItems("person-id", 40)
        val request = server.takeRequest().requestUrl!!
        assertEquals("person-id", request.queryParameter("PersonIds"))
        assertEquals("40", request.queryParameter("StartIndex"))
        assertEquals("user-id", request.queryParameter("UserId"))
        assertEquals("true", request.queryParameter("Recursive"))
    }

    @Test
    fun malformedSuccessAndMissingResponseBodyFailAtTheNetworkBoundary() = runBlocking {
        server.enqueue(MockResponse().setBody("not-json"))
        try {
            api.connect(server.url("/jellyfin").toString())
            fail("Malformed JSON must fail")
        } catch (_: kotlinx.serialization.SerializationException) {}
        server.enqueue(MockResponse().setResponseCode(204))
        try {
            api.connect(server.url("/jellyfin").toString())
            fail("A connection must return server information")
        } catch (_: java.io.IOException) {}
    }

    @Test
    fun logoutAcceptsEmptySuccessAndKeepsJsonHeaders() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        api.logout()
        val request = server.takeRequest()
        assertEquals("/jellyfin/Sessions/Logout", request.requestUrl!!.encodedPath)
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun favoriteWritesCurrentUserContext() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.favorite("movie-id", true)
        val add = server.takeRequest()
        assertEquals("POST", add.method)
        assertEquals("user-id", add.requestUrl!!.queryParameter("UserId"))
        server.enqueue(MockResponse().setResponseCode(204))
        api.favorite("movie-id", false)
        val remove = server.takeRequest()
        assertEquals("DELETE", remove.method)
        assertEquals("/jellyfin/UserFavoriteItems/movie-id", remove.requestUrl!!.encodedPath)
    }
}
