package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.browse.HomeUiState
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
            compose.onNodeWithTag("hero:play").assertIsFocused()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("header:home").assertIsFocused()
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
            compose.onNodeWithTag("继续观看:resume2").assertIsFocused()
            val expanded = compose.onNodeWithTag("继续观看:resume2").getUnclippedBoundsInRoot()
            val poster = compose.onNodeWithTag("继续观看:resume").getUnclippedBoundsInRoot()
            assertTrue(expanded.right - expanded.left > (poster.right - poster.left) * 2)
            compose.onNodeWithTag("继续观看:resume2").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("最近加入:latest").assertIsDisplayed()
            compose.onNodeWithTag("最近加入:latest").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("继续观看:resume2").assertIsDisplayed().assertIsFocused().assertIsSelected()
            compose.onNodeWithTag("继续观看:resume2").performKeyInput { pressKey(Key.DirectionUp) }
            compose.runOnIdle {
                home = Home(emptyList(), emptyList(), emptyList(),
                    resumeLoad = RegionLoad(error = "offline"), latestLoad = RegionLoad(error = "offline"),
                    librariesLoad = RegionLoad(error = "offline"))
                discovery = TvHomeDiscoveryUiState(listOf(Media("series", "独立发现剧集", "Series")), loaded = true)
            }
            compose.onNodeWithTag("hero:play").assertTextContains("查看剧集").performKeyInput { pressKey(Key.Enter) }
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
