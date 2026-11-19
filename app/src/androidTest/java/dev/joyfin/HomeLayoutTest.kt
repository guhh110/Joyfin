package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.browse.HomeUiState
import dev.joyfin.browse.ResumeActionState
import dev.joyfin.data.Home
import dev.joyfin.data.Media
import dev.joyfin.data.LibraryRailState
import dev.joyfin.data.RegionLoad
import dev.joyfin.browse.TvHomeDiscoveryUiState
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.home.HomeRoute
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun assertFocusOutline(tag: String, visible: Boolean) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val edge = pixels[pixels.width / 2, 0]
        assertEquals("Focus outline on $tag", visible, edge.red > .85f && edge.green > .85f && edge.blue > .85f)
    }

    private fun awaitFocused(tag: String): SemanticsNodeInteraction {
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Expected focus on $tag. ${compose.onAllNodes(isFocused()).printToString()}", timeout)
        }
        return compose.onNodeWithTag(tag).assertIsFocused()
    }

    @Test
    fun tvSectionHeaderMoreKeepsOneFocusOutlineAndRestoresTheExpandedCard() {
        val latest = listOf(Media("first", "第一部影片", "Movie"), Media("second", "第二部影片", "Movie"))
        val library = Media("library", "电影库", "CollectionFolder")
        val emptyLibrary = Media("empty", "空媒体库", "CollectionFolder")
        var home by mutableStateOf(Home(emptyList(), latest, listOf(library, emptyLibrary), rails = listOf(
            LibraryRailState(library, listOf(Media("film", "库中影片", "Movie")), loading = false, error = "offline"),
            LibraryRailState(emptyLibrary, emptyList(), loading = false))))
        val navigated = mutableListOf<String>()
        var retries = 0
        val actions = BrowseActions(
            back = {}, home = {}, open = {}, openPerson = {}, selectEpisode = {},
            retryLibrary = { retries++ }, selectSeason = {}, library = { id, _ -> navigated += id.orEmpty() },
            search = {}, submitSearch = {}, loadMore = {}, settings = {}, favorite = {}, play = { _, _, _ -> },
            retry = {}, logout = {}, setAutoNext = {}, allMedia = { navigated += "all" },
        )
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(true, 960)) {
                            val inputMode = LocalInputModeManager.current
                            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                            RememberScreenFocus("hero:play") {
                                HomeRoute(HomeUiState(content = home), actions, images,
                                    discovery = TvHomeDiscoveryUiState(latest, loaded = true))
                            }
                        }
                    }
                }
            }
            awaitFocused("hero:play").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("hero:candidate:first").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("最近加入:first")
            val headerMore = compose.onNodeWithTag("home:latest:more").getUnclippedBoundsInRoot()
            compose.onNodeWithTag("最近加入:first").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("最近加入:second").assertIsSelected()
            assertFocusOutline("最近加入:second", true)
            val expanded = compose.onNodeWithTag("最近加入:second").getUnclippedBoundsInRoot()

            compose.onNodeWithTag("最近加入:second").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("最近加入:second").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:latest:more")
            compose.onNodeWithTag("最近加入:second").assertIsNotFocused().assertIsSelected()
            assertEquals(expanded, compose.onNodeWithTag("最近加入:second").getUnclippedBoundsInRoot())
            assertFocusOutline("最近加入:second", false)
            assertFocusOutline("home:latest:more", true)
            val more = compose.onNodeWithTag("home:latest:more").getUnclippedBoundsInRoot()
            val title = compose.onNodeWithText("最近加入").getUnclippedBoundsInRoot()
            val header = compose.onNodeWithTag("home:section-header:latest").getUnclippedBoundsInRoot()
            assertEquals(headerMore, more)
            assertEquals(header.right, more.right)
            assertEquals((title.top + title.bottom) / 2, (more.top + more.bottom) / 2)
            assertTrue(more.left > title.right)
            assertTrue(more.bottom < expanded.top)
            assertTrue(more.bottom - more.top < (expanded.bottom - expanded.top) / 3)

            compose.onNodeWithTag("home:latest:more").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("最近加入:second")
            assertFocusOutline("最近加入:second", true)
            assertFocusOutline("home:latest:more", false)
            compose.onNodeWithTag("最近加入:second").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:latest:more").performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionDown) }
            awaitFocused("最近加入:second").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("library:library:film").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("home:library:library:retry")
            assertFocusOutline("library:library:film", false)
            assertFocusOutline("home:library:library:retry", true)
            compose.onNodeWithTag("home:library:library:retry")
                .performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionRight) }
            awaitFocused("home:library:library:retry").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:library:library:more").performKeyInput { pressKey(Key.Enter) }
            assertFocusOutline("home:library:library:retry", false)
            assertFocusOutline("home:library:library:more", true)
            compose.onNodeWithTag("home:library:library:more").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("library:library:film").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:library:library:more").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("最近加入:second").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:latest:more").performKeyInput { pressKey(Key.PageDown) }
            awaitFocused("library:library:film").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:library:library:more")
            compose.runOnIdle {
                assertEquals(1, retries)
                assertEquals(listOf("all", "library"), navigated)
            }
            compose.onNodeWithTag("home:library:library:more").performKeyInput { pressKey(Key.PageDown) }
            awaitFocused("home:library:empty:more")
            compose.onAllNodesWithTag("home:library:empty:more").assertCountEquals(1)
            compose.onNodeWithText("暂无内容").assertIsDisplayed()
            compose.onNodeWithTag("home:library:empty:more").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("library:library:film").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("home:library:empty:more")
            compose.runOnIdle {
                home = home.copy(rails = home.rails.map { if (it.library.id == emptyLibrary.id) it.copy(error = "offline") else it })
            }
            compose.onNodeWithTag("home:library:empty:more").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocused("home:library:empty:retry").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:library:empty:more")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvResumeGroupsEpisodesAndRestoresFocusAcrossRefreshAndQuickActions() {
        val older = Media("older", "第一集", "Episode", seriesId = "series", seriesName = "同一部剧",
            season = 1, episode = 1, ticks = 100, resumeTicks = 10, lastPlayedAt = 100)
        val current = older.copy(id = "current", name = "第二集", episode = 2,
            resumeTicks = 40, lastPlayedAt = 200, imageTag = "current-image")
        val other = older.copy(id = "other", seriesId = "other-series", seriesName = "另一部剧", lastPlayedAt = 150)
        val movie = Media("movie", "独立电影", "Movie", ticks = 100, resumeTicks = 20, lastPlayedAt = 500)
        val newest = current.copy(id = "newest", episode = 3, lastPlayedAt = 700)
        val refreshed = newest.copy(id = "refreshed", episode = 4, lastPlayedAt = 800)
        var home by mutableStateOf(Home(listOf(older, movie, current, other),
            listOf(Media("latest", "最近加入", "Movie")), emptyList()))
        var request by mutableStateOf(ResumeActionState())
        val opened = mutableListOf<Media>()
        val removed = mutableListOf<String>()
        val marked = mutableListOf<String>()
        fun complete(media: Media, markPlayed: Boolean) {
            if (markPlayed) marked += media.id else removed += media.id
            home = home.copy(resume = home.resume.filterNot { it.id == media.id })
            request = ResumeActionState(media.id, markPlayed = markPlayed, completed = true)
        }
        val actions = BrowseActions(
            back = {}, home = {}, open = { opened += it }, openPerson = {}, selectEpisode = {},
            retryLibrary = {}, selectSeason = {}, library = { _, _ -> }, search = {}, submitSearch = {},
            loadMore = {}, settings = {}, favorite = {}, play = { _, _, _ -> }, retry = {}, logout = {},
            setAutoNext = {}, removeResumeItem = { complete(it, false) },
            markResumeItemPlayed = { complete(it, true) }, cancelResumeAction = { request = ResumeActionState() },
        )
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(true, 960)) {
                            val inputMode = LocalInputModeManager.current
                            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                            RememberScreenFocus("hero:play") {
                                HomeRoute(HomeUiState(content = home, resumeAction = request), actions, images,
                                    discovery = TvHomeDiscoveryUiState(home.latest, loaded = true))
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("hero:play").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:candidate:movie").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("继续观看:movie").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("继续观看:current").assertIsFocused().assertIsSelected()
                .performKeyInput { pressKey(Key.Enter) }
            compose.onAllNodesWithTag("继续观看:current").assertCountEquals(1)
            compose.onNodeWithTag("继续观看:older").assertDoesNotExist()
            compose.runOnIdle { assertSame(current, opened.single()) }

            compose.runOnIdle { home = home.copy(resume = home.resume + newest) }
            compose.onNodeWithTag("继续观看:newest").assertIsFocused().assertIsSelected()
                .performKeyInput { pressKey(Key.Menu) }
            awaitFocused("resume-action:replay")
            compose.runOnIdle { home = home.copy(resume = home.resume + refreshed) }
            compose.onNodeWithTag("sheet:close").performClick()
            awaitFocused("继续观看:refreshed").assertIsSelected()
            compose.onNodeWithTag("继续观看:newest").assertDoesNotExist()

            compose.onNodeWithTag("继续观看:refreshed").performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithTag("resume-action:remove").performClick()
            compose.onNodeWithTag("继续观看:newest").assertIsFocused().assertIsSelected()
            compose.runOnIdle {
                assertEquals(listOf(refreshed.id), removed)
                assertTrue(home.resume.containsAll(listOf(older, current, newest)))
            }
            compose.onNodeWithTag("继续观看:newest").performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithTag("resume-action:played").performClick()
            compose.onNodeWithTag("继续观看:current").assertIsFocused().assertIsSelected()
            compose.runOnIdle {
                assertEquals(listOf(newest.id), marked)
                assertTrue(home.resume.containsAll(listOf(older, current)))
                home = home.copy(resume = home.resume.filterNot { it.seriesId == "series" })
            }
            compose.onNodeWithTag("继续观看:other").assertIsFocused().assertIsSelected()
                .performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithTag("resume-action:remove").performClick()
            compose.onNodeWithTag("继续观看:movie").assertIsFocused().assertIsSelected()
                .performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithTag("resume-action:remove").performClick()
            awaitFocused("header:home")
            compose.onNodeWithTag("home:section:resume").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(listOf(refreshed.id, other.id, movie.id), removed)
                assertTrue(home.resume.isEmpty())
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvFeaturedCopyStaysCompactAcrossMoviesEpisodesAndMissingMetadata() {
        val episode = Media("episode", "不应显示的完整集名", "Episode", seriesId = "continuing-series",
            seriesName = "续看剧集", season = 5, episode = 140, ticks = 1_800_000_000,
            resumeTicks = 600_000_000, lastPlayedAt = 20, year = 2026, rating = 8.8,
            genres = listOf("不应显示的类型"), overview = "只保留一行的作品简介。".repeat(40))
        val resumedMovie = Media("resumed", "续看电影", "Movie", ticks = 1_800_000_000,
            resumeTicks = 600_000_000, lastPlayedAt = 10, rating = 9.1)
        val movie = Media("movie", "新电影", "Movie", year = 2025)
        val series = Media("series", "待选剧集", "Series")
        val home = Home(listOf(episode, resumedMovie), listOf(movie, series), emptyList())
        val played = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val actions = BrowseActions(
            back = {}, home = {}, open = { opened += it.id }, openPerson = {}, selectEpisode = {},
            retryLibrary = {}, selectSeason = {}, library = { _, _ -> }, search = {}, submitSearch = {},
            loadMore = {}, settings = {}, favorite = {}, play = { media, _, _ -> played += media.id },
            retry = {}, logout = {}, setAutoNext = {},
        )
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(true, 960)) {
                            val inputMode = LocalInputModeManager.current
                            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                            RememberScreenFocus("hero:play") {
                                HomeRoute(HomeUiState(content = home), actions, images,
                                    discovery = TvHomeDiscoveryUiState(home.latest, loaded = true))
                            }
                        }
                    }
                }
            }
            awaitFocused("hero:play").assertTextContains("继续 S5E140")
            compose.onNodeWithTag("home:app-logo").assertIsDisplayed().assertContentDescriptionEquals("Joyfin")
            compose.onNodeWithText("私人放映厅").assertDoesNotExist()
            compose.onNodeWithText("JOYFIN / CINERAMA").assertDoesNotExist()
            compose.onNodeWithText("左右选片 · 确认观看 · 下键浏览栏目").assertDoesNotExist()
            for (label in listOf("继续观看", "接着看下一集", "最近加入")) {
                compose.onAllNodes(hasAnyAncestor(hasTestTag("home:section:featured")) and hasText(label),
                    useUnmergedTree = true).assertCountEquals(0)
            }
            compose.onNodeWithTag("hero:metadata").assertTextEquals("2026 · 剧集 · 8.8 分")
            compose.onNodeWithTag("hero:progress").assertTextEquals("")
            compose.onNodeWithText("S5 · E140").assertDoesNotExist()
            compose.onNodeWithTag("hero:detail").assertTextContains("详情")
            compose.onAllNodes(hasAnyAncestor(hasTestTag("home:hero-content")) and
                (hasText(episode.name) or hasText(episode.genres.single())), useUnmergedTree = true)
                .assertCountEquals(0)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("hero:overview").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            assertEquals(1, layouts.single().lineCount)
            assertTrue(layouts.single().isLineEllipsized(0))
            compose.onNodeWithTag("hero:play").performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionDown) }
            awaitFocused("hero:candidate:continuing-series")
                .assertContentDescriptionEquals("续看剧集，继续观看")
            compose.onAllNodes(hasAnyAncestor(hasTestTag("hero:candidate:continuing-series")) and
                SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true).assertCountEquals(1)
            compose.mainClock.advanceTimeBy(1_000)
            val buttonBounds = compose.onNodeWithTag("hero:play").getUnclippedBoundsInRoot()
            fun assertButtonPosition() {
                val bounds = compose.onNodeWithTag("hero:play").getUnclippedBoundsInRoot()
                assertEquals(buttonBounds.left, bounds.left)
                assertEquals(buttonBounds.top, bounds.top)
                assertEquals(buttonBounds.bottom, bounds.bottom)
            }
            compose.onNodeWithTag("hero:candidate:continuing-series").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("hero:candidate:movie")
            compose.onNodeWithTag("hero:play").assertTextContains("播放")
            compose.onNodeWithTag("hero:metadata").assertTextEquals("2025 · 电影")
            compose.onNodeWithTag("hero:overview").assertTextEquals("")
            compose.onNodeWithTag("hero:progress").assertTextEquals("")
            compose.mainClock.advanceTimeBy(1_000)
            assertButtonPosition()
            compose.onNodeWithTag("hero:candidate:movie").performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionRight) }
            awaitFocused("hero:candidate:resumed")
            compose.onNodeWithTag("hero:metadata").assertTextEquals("电影 · 9.1 分")
            compose.onNodeWithTag("hero:progress").assertTextEquals("剩余 2 分钟")
            compose.onNodeWithTag("hero:play").assertTextContains("继续")
            compose.mainClock.advanceTimeBy(1_000)
            assertButtonPosition()
            compose.onNodeWithTag("hero:candidate:resumed").performKeyInput { pressKey(Key.DirectionRight) }
            awaitFocused("hero:candidate:series")
            compose.onNodeWithTag("hero:play").assertTextContains("剧集")
            compose.onNodeWithTag("hero:metadata").assertTextEquals("剧集")
            compose.onNodeWithTag("hero:progress").assertTextEquals("")
            compose.mainClock.advanceTimeBy(1_000)
            assertButtonPosition()
            compose.onNodeWithTag("hero:candidate:series").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("hero:play").performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle {
                assertEquals(listOf(episode.id, movie.id), played)
                assertEquals(listOf(series.id), opened)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvUsesFullPagesAndRestoresTheExpandedCardWhenChangingSections() {
        var home by mutableStateOf(
            Home(
                resume = listOf(Media("resume", "继续的影片", "Movie", ticks = 100, resumeTicks = 10),
                    Media("resume2", "另一部影片", "Movie", ticks = 100, resumeTicks = 20)),
                latest = listOf(Media("latest", "最近的影片", "Movie")),
                libraries = emptyList(),
            ))
        var discovery by mutableStateOf(TvHomeDiscoveryUiState(home.latest, loaded = true))
        val played = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val navigated = mutableListOf<String>()
        val actions =
            BrowseActions(
                back = {},
                home = {},
                open = { opened += it.id },
                openPerson = {},
                selectEpisode = {},
                retryLibrary = {},
                selectSeason = {},
                library = { _, _ -> navigated += "library" },
                search = { navigated += "search" },
                submitSearch = {},
                loadMore = {},
                settings = { navigated += "settings" },
                favorite = {},
                play = { media, _, _ -> played += media.id },
                retry = {},
                logout = {},
                setAutoNext = {},
                tracking = { navigated += "tracking" },
            )
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            val inputMode = LocalInputModeManager.current
                            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                            RememberScreenFocus("hero:play") {
                                HomeRoute(HomeUiState(content = home), actions, images,
                                    discovery = discovery)
                            }
                        }
                    }
                }
            }
            val viewport = compose.onNodeWithTag("home:scroll").getUnclippedBoundsInRoot()
            val hero = compose.onNodeWithTag("home:section:featured").getUnclippedBoundsInRoot()
            assertEquals(viewport.bottom - viewport.top, hero.bottom - hero.top)
            compose.onAllNodesWithTag("cinema:header").assertCountEquals(1)
            compose.onNodeWithTag("header:home").assertIsSelected()
            compose.onNodeWithTag("header:back").assertDoesNotExist()
            compose.onNodeWithTag("hero:play").assertIsFocused()
            compose.onNodeWithTag("hero:detail").assertIsDisplayed()
            for (target in listOf("hero:play", "header:search", "hero:play")) {
                compose
                    .onNodeWithTag(target)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.RequestFocus)
                compose.mainClock.advanceTimeBy(1_000)
                assertEquals(hero, compose.onNodeWithTag("home:section:featured").getUnclippedBoundsInRoot())
                val bounds = compose.onNodeWithTag(target).getUnclippedBoundsInRoot()
                assertTrue(bounds.top >= hero.top && bounds.bottom <= hero.bottom)
            }
            compose.onNodeWithTag("hero:play").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:candidate:resume").assertIsFocused().assertIsSelected()
            compose.onNodeWithTag("hero:candidate:resume").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("hero:candidate:latest").assertIsFocused().assertIsSelected()
                .performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(listOf("latest"), played) }
            compose.mainClock.advanceTimeBy(11_000)
            compose.onNodeWithTag("hero:candidate:latest").assertIsSelected().assertIsFocused()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitFocused("hero:play")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitFocused("header:home")
                .performKeyInput { pressKey(Key.DirectionRight) }
            for (target in listOf("tracking", "library", "search", "account")) {
                compose.onNodeWithTag("header:$target").assertIsFocused()
                    .performKeyInput { pressKey(Key.Enter) }
                if (target != "account") compose.onNodeWithTag("header:$target")
                    .performKeyInput { pressKey(Key.DirectionRight) }
            }
            compose.runOnIdle { assertEquals(listOf("tracking", "library", "search", "settings"), navigated) }
            compose.onNodeWithTag("header:account")
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:play").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:candidate:latest").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("hero:play").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("hero:detail").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(listOf("latest"), opened) }
            compose.onNodeWithTag("hero:detail").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:candidate:latest").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("继续观看:resume").assertIsDisplayed().assertIsFocused().assertIsSelected()
            compose.onNodeWithTag("继续观看:resume").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("继续观看:resume2").assertIsDisplayed().assertIsFocused().assertIsSelected()
            compose.runOnIdle {
                val libraries = (1..5).map { Media("library$it", "媒体库$it", "CollectionFolder") }
                home = home.copy(libraries = libraries,
                    rails = libraries.map { LibraryRailState(it, listOf(Media("${it.id}-movie", "影片", "Movie")), loading = false) })
            }
            compose.onNodeWithTag("继续观看:resume2").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("最近加入:latest").performKeyInput { pressKey(Key.DirectionDown) }
            for (index in 1..4) {
                compose.onNodeWithTag("library:library$index:library$index-movie")
                    .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            }
            compose.onNodeWithTag("library:library5:library5-movie").assertIsFocused()
            compose.runOnIdle { home = home.copy(libraries = emptyList(), rails = emptyList()) }
            compose.onNodeWithTag("最近加入:latest").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:latest:more").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("继续观看:resume2").assertIsFocused()
            val expanded = compose.onNodeWithTag("继续观看:resume2").getUnclippedBoundsInRoot()
            val poster = compose.onNodeWithTag("继续观看:resume").getUnclippedBoundsInRoot()
            assertTrue(expanded.right - expanded.left > (poster.right - poster.left) * 2)
            compose.onNodeWithTag("继续观看:resume2").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("最近加入:latest").assertIsDisplayed()
            compose.onNodeWithTag("最近加入:latest").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocused("home:latest:more").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("继续观看:resume2").assertIsDisplayed().assertIsFocused().assertIsSelected()
            compose.onNodeWithTag("继续观看:resume2").performKeyInput { pressKey(Key.DirectionUp) }
            compose.runOnIdle {
                home = Home(emptyList(), emptyList(), emptyList(),
                    resumeLoad = RegionLoad(error = "offline"), latestLoad = RegionLoad(error = "offline"),
                    librariesLoad = RegionLoad(error = "offline"))
                discovery = TvHomeDiscoveryUiState(listOf(Media("series", "独立发现剧集", "Series")), loaded = true)
            }
            compose.onNodeWithTag("hero:play").assertTextContains("剧集").performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals("series", opened.last()) }
            compose.onNodeWithText("暂时无法加载精选").assertDoesNotExist()
            compose.runOnIdle { discovery = discovery.copy(loading = true, error = "offline") }
            compose.onNodeWithTag("hero:candidate:series").assertIsSelected()
            compose.onNodeWithTag("hero:play").assertIsFocused().assertIsEnabled()
            compose.runOnIdle {
                home = Home(emptyList(), emptyList(), emptyList(),
                    resumeLoad = RegionLoad(loaded = true), latestLoad = RegionLoad(loaded = true),
                    librariesLoad = RegionLoad(loaded = true))
                discovery = TvHomeDiscoveryUiState(loaded = true)
            }
            compose.onNodeWithText("媒体库还是空的").assertIsDisplayed()
            compose.onNodeWithTag("hero:play").assertTextContains("浏览媒体库")
            compose.runOnIdle {
                home = home.copy(resumeLoad = RegionLoad(error = "offline"))
                discovery = TvHomeDiscoveryUiState(error = "offline")
            }
            compose.onNodeWithText("暂时无法加载精选").assertIsDisplayed()
            compose.onNodeWithTag("hero:play").assertTextContains("重试").assertIsEnabled().assertIsFocused()
            compose.onNodeWithTag("hero:detail").assertTextContains("浏览媒体库")
            compose.onNodeWithTag("hero:play").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("hero:retry:续看").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("hero:retry:最近加入").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("hero:play").assertIsFocused()
        } finally {
            scenario.close()
        }
    }
}
