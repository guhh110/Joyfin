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
    @Test
    fun audioDecoderFallbackPreservesVideoCopyInsteadOfForcingFullCompatibilityTranscode() =
        runBlocking {
            playbackResponse(direct = false, transcode = "/jellyfin/video.m3u8")
            val plan = api.playback(item, DeviceProfileDto(), 0, audioTranscode = true)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertFalse(body.getBoolean("EnableDirectPlay"))
            assertFalse(body.getBoolean("AllowAudioStreamCopy"))
            assertTrue(body.getBoolean("AllowVideoStreamCopy"))
            assertEquals("Transcode", plan.method)
        }

    @Test
    fun convertedTextUsesDeliveryUrlInsteadOfRequestingTheUnsupportedOriginalCodec() = runBlocking {
        val subtitle =
            MediaStream(type = "Subtitle", index = 5, codec = "sami", textSubtitle = true)
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"PlaySessionId":"text","MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","Index":5,"Codec":"sami","IsTextSubtitleStream":true,"DeliveryMethod":"External","DeliveryUrl":"/jellyfin/converted.srt"}]}]}"""
                )
        )
        val plan =
            api.playback(
                item.copy(
                    sources =
                        listOf(
                            MediaSource(
                                id = "source-id",
                                container = "mkv",
                                streams = listOf(subtitle),
                            )
                        )
                ),
                DeviceProfileDto(),
                0,
                subtitle = 5,
            )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertEquals("application/x-subrip", plan.subtitles.single().mime)
        assertTrue(plan.subtitles.single().url.contains("/jellyfin/converted.srt"))
    }

    @Test
    fun embeddedPgsKeepsDirectPlayAndDoesNotCreateAnExternalSupRequest() = runBlocking {
        val source =
            MediaSource(
                id = "source-id",
                container = "mkv",
                streams = listOf(MediaStream(type = "Subtitle", index = 5, codec = "pgssub")),
            )
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"PlaySessionId":"pgs","MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","Index":5,"Codec":"pgssub","DeliveryMethod":"Embed"}]}]}"""
                )
        )
        val plan =
            api.playback(item.copy(sources = listOf(source)), DeviceProfileDto(), 0, subtitle = 5)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertTrue(body.getBoolean("AllowVideoStreamCopy"))
        assertEquals("DirectPlay", plan.method)
        assertEquals(5, plan.subtitleIndex)
        assertTrue(plan.subtitles.isEmpty())
    }

    @Test
    fun discoveredSourceRebuildsProfileBeforeFinalPlaybackNegotiation() = runBlocking {
        val response =
            """{"PlaySessionId":"pgs","MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","Index":5,"Codec":"pgssub"},{"Type":"Audio","Index":3,"Codec":"dts","Channels":6}]}]}"""
        repeat(2) { server.enqueue(MockResponse().setBody(response)) }
        val plan =
            api.playback(
                item,
                DeviceProfileDto(),
                0,
                audio = 3,
                subtitle = 5,
                profileForSource = { source, audio ->
                    assertEquals("mkv", source.container)
                    assertEquals(3, audio)
                    DeviceProfileDto(name = "Discovered DTS profile")
                },
            )
        assertFalse(
            JSONObject(server.takeRequest().body.readUtf8()).getBoolean("AutoOpenLiveStream")
        )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(
            "Discovered DTS profile",
            body.getJSONObject("DeviceProfile").getString("Name"),
        )
        assertTrue(body.getBoolean("EnableDirectPlay"))
        assertEquals("DirectPlay", plan.method)
    }

    @Test
    fun playbackKeepsServerTranscodeReasonsWithoutRetainingTheUrlAsDiagnosticText() = runBlocking {
        playbackResponse(
            direct = false,
            transcode =
                "/jellyfin/video.m3u8?TranscodeReasons=AudioCodecNotSupported%2CAudioChannelsNotSupported",
        )
        val plan = api.playback(item, DeviceProfileDto(), 0, subtitle = -1)
        assertEquals(
            listOf("AudioCodecNotSupported", "AudioChannelsNotSupported"),
            plan.transcodeReasons,
        )
    }

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
    fun unknownContainerUsesOnlyTheSelectedExternalSubtitleWithoutForcingVideoTranscode() = runBlocking {
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
        assertEquals(listOf(7), plan.subtitles.map { it.index })
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
        assertTrue(plan.subtitles.isEmpty())
    }

    @Test fun mkvEmbeddedTextNeverCreatesASeparateExtractionSourceEvenIfServerAdvertisesExternalDelivery() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":7,"Codec":"ass","DeliveryMethod":"External","DeliveryUrl":"/jellyfin/slow-extraction.ass"},{"Type":"Subtitle","Index":2,"Codec":"subrip","DeliveryMethod":"External"}]"""
        val source = AppJson.decodeFromString<MediaSource>("""{"Id":"source-id","Container":"mkv","MediaStreams":$streams}""")
        val media = item.copy(sources = listOf(source))
        repeat(2) { server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":$streams}]}""")) }
        val ass = api.playback(media, DeviceProfileDto(), 0, subtitle = 7)
        val srt = api.playback(media, DeviceProfileDto(), 0, subtitle = 2)
        for (plan in listOf(ass, srt)) {
            assertEquals("DirectPlay", plan.method)
            assertTrue(plan.subtitles.isEmpty())
            assertEquals(listOf(7, 2), plan.streams.filter { it.type == "Subtitle" }.map { it.index })
            assertTrue(JSONObject(server.takeRequest().body.readUtf8()).getBoolean("AllowVideoStreamCopy"))
        }
        assertEquals(7, ass.subtitleIndex)
        assertEquals(2, srt.subtitleIndex)
        assertEquals(2, server.requestCount)
    }

    @Test fun realExternalSubtitleIsLoadedOnlyWhenSelectedAndKeepsItsDeliveryUrl() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":7,"Codec":"ass"},{"Type":"Subtitle","Index":2,"Codec":"subrip","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/jellyfin/external.srt"},{"Type":"Subtitle","Index":5,"Codec":"ass","IsExternal":true,"DeliveryMethod":"External","DeliveryUrl":"/jellyfin/unused.ass"}]"""
        val source = AppJson.decodeFromString<MediaSource>("""{"Id":"source-id","Container":"mkv","MediaStreams":$streams}""")
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":true,"MediaStreams":$streams}]}"""))
        val plan = api.playback(item.copy(sources = listOf(source)), DeviceProfileDto(), 0, subtitle = 2)
        assertEquals("DirectPlay", plan.method)
        assertEquals(listOf(2), plan.subtitles.map { it.index })
        assertTrue(plan.subtitles.single().url.endsWith("/jellyfin/external.srt"))
        assertEquals(3, plan.streams.count { it.type == "Subtitle" })
    }

    @Test fun mkvSourceWithVideoTranscodingStillLoadsTheChosenTextSubtitleExternally() = runBlocking {
        val streams = """[{"Type":"Subtitle","Index":7,"Codec":"ass","DeliveryMethod":"External","DeliveryUrl":"/jellyfin/transcoded.ass"},{"Type":"Subtitle","Index":2,"Codec":"subrip"}]"""
        val source = AppJson.decodeFromString<MediaSource>("""{"Id":"source-id","Container":"mkv","MediaStreams":$streams}""")
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"source-id","Container":"mkv","SupportsDirectPlay":false,"TranscodingUrl":"/jellyfin/stream.m3u8","MediaStreams":$streams}]}"""))
        val plan = api.playback(item.copy(sources = listOf(source)), DeviceProfileDto(), 0, subtitle = 7)
        assertEquals("Transcode", plan.method)
        assertEquals(listOf(7), plan.subtitles.map { it.index })
        assertTrue(plan.subtitles.single().url.endsWith("/jellyfin/transcoded.ass"))
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
        assertNull(requests[0].requestUrl!!.queryParameter("SortBy"))
        assertEquals("true", requests[1].requestUrl!!.queryParameter("GroupItems"))
    }

    @Test
    fun latestInLibraryRequestsNewestWorksWithoutEpisodeGrouping() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"Items":[{"Id":"series","Name":"Z New Show","Type":"Series","DateCreated":"2026-10-22T10:00:00Z","Overview":"Full series metadata","ChildCount":24},{"Id":"movie","Name":"A Older Movie","Type":"Movie","DateCreated":"2026-10-20T10:00:00Z"}],"TotalRecordCount":0}"""
                )
        )
        val items = api.latestInLibrary("children")
        val url = server.takeRequest().requestUrl!!
        assertEquals("/jellyfin/Items", url.encodedPath)
        assertEquals("user-id", url.queryParameter("UserId"))
        assertEquals("children", url.queryParameter("ParentId"))
        assertEquals("true", url.queryParameter("Recursive"))
        assertEquals("Movie,Series,Video,MusicVideo", url.queryParameter("IncludeItemTypes"))
        assertEquals("DateCreated,SortName", url.queryParameter("SortBy"))
        assertEquals("Descending", url.queryParameter("SortOrder"))
        assertEquals("false", url.queryParameter("EnableTotalRecordCount"))
        assertNull(url.queryParameter("GroupItems"))
        assertEquals("20", url.queryParameter("Limit"))
        assertEquals(listOf("series", "movie"), items.map { it.id })
        assertEquals("Series", items.first().type)
        assertEquals("Full series metadata", items.first().overview)
        assertEquals(24, items.first().childCount)
    }

    @Test fun nextEpisodeTransfersOnlyTheCurrentAndFollowingEpisodeAndCanCrossSeasons() = runBlocking {
        val current = Media("s1e24", "Finale", "Episode", seriesId = "series", season = 1, episode = 24)
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"s1e24","Type":"Episode","SeriesId":"series","ParentIndexNumber":1,"IndexNumber":24},{"Id":"s2e1","Type":"Episode","SeriesId":"series","ParentIndexNumber":2,"IndexNumber":1}]}"""))
        val next = api.nextEpisode(current)
        assertEquals("s2e1", next?.id)
        assertEquals(2, next?.season)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/jellyfin/Shows/series/Episodes", url.encodedPath)
        assertEquals("s1e24", url.queryParameter("StartItemId"))
        assertEquals("2", url.queryParameter("Limit"))
        assertEquals("ParentId", url.queryParameter("Fields"))
        assertEquals("false", url.queryParameter("EnableImages"))
        assertEquals("false", url.queryParameter("EnableUserData"))
        assertEquals("false", url.queryParameter("IsMissing"))
        assertNull(url.queryParameter("SeasonId"))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"s2e1","Type":"Episode","SeriesId":"series"}]}"""))
        assertNull(api.nextEpisode(requireNotNull(next)))
    }

    @Test fun continuationDoesNotGuessWhenTheCurrentEpisodeIsMissingOrTheFollowingItemIsForeign() = runBlocking {
        val current = Media("current", "Episode", "Episode", seriesId = "series")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"other","Type":"Episode"},{"Id":"second","Type":"Episode"}]}"""))
        assertNull(api.nextEpisode(current))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"current","Type":"Episode"},{"Id":"foreign","Type":"Episode","SeriesId":"elsewhere"}]}"""))
        assertNull(api.nextEpisode(current))
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

    @Test
    fun setPlayedAndRemoveResumeUseUserContext() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        api.setPlayed("item-1", true)
        val markPlayed = server.takeRequest()
        assertEquals("POST", markPlayed.method)
        assertEquals("/jellyfin/Users/user-id/PlayedItems/item-1", markPlayed.requestUrl!!.encodedPath)

        server.enqueue(MockResponse().setResponseCode(200))
        api.setPlayed("item-1", false)
        val markUnplayed = server.takeRequest()
        assertEquals("DELETE", markUnplayed.method)
        assertEquals("/jellyfin/Users/user-id/PlayedItems/item-1", markUnplayed.requestUrl!!.encodedPath)

        server.enqueue(MockResponse().setResponseCode(204))
        api.removeResume("item-1")
        val remove = server.takeRequest()
        assertEquals("DELETE", remove.method)
        assertEquals("/jellyfin/Users/user-id/PlayingItems/item-1", remove.requestUrl!!.encodedPath)
    }
}
