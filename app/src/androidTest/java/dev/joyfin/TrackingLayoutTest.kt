package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.joyfin.browse.*
import dev.joyfin.data.*
import dev.joyfin.ui.components.MobileBrowseNavigation
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.home.HomeRoute
import dev.joyfin.ui.features.tracking.TrackingRoute
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrackingLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
    private val target = Media("e5", "新的旅程", "Episode", season = 1, episode = 5, seriesId = "show", resumeTicks = 30)
    private val watching = TrackingEntry(Media("show", "正在追的剧", "Series"),
        TrackingProgress(TrackingStatus.WATCHING, target, 3))
    private val caught = TrackingEntry(Media("caught", "追平的剧", "Series"),
        TrackingProgress(TrackingStatus.CAUGHT_UP, unplayed = 2))

    private fun mount(tv: Boolean = false, large: Boolean = false, content: @Composable () -> Unit): ActivityScenario<MainActivity> {
        val activity = ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity { it.setContent {
            BoundlessTheme {
                val density = LocalDensity.current
                val inputMode = LocalInputModeManager.current
                SideEffect { if (tv) inputMode.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(tv, if (tv) 960 else 360),
                    LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                    Box(if (tv) Modifier else Modifier.width(360.dp).fillMaxHeight()) {
                        RememberScreenFocus(if (tv) "header:tracking" else "tracking:refresh", content)
                    }
                }
            }
        } }
        return activity
    }

    @Test fun mobileNavigationHasFiveDestinationsAndTrackingOpens() {
        var opened = false
        val scenario = mount { MobileBrowseNavigation(actions().copy(tracking = { opened = true }), "home") }
        try {
            listOf("首页", "追剧", "媒体库", "字幕", "搜索").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onNodeWithText("追剧").performClick()
            assertTrue(opened)
        } finally { scenario.close() }
    }

    @Test fun mobileFiltersAndPlaybackUseTheEpisodeAndRemovalExplainsFavorites() {
        var played: Media? = null
        var removed: String? = null
        val scenario = mount(large = true) {
            TrackingRoute(TrackingUiState(listOf(watching, caught), loaded = true),
                actions().copy(play = { item, _, _ -> played = item }, removeTracking = { removed = it }), images)
        }
        try {
            screenshot("mobile")
            compose.onNodeWithTag("tracking:play:show").performScrollTo().performClick()
            assertEquals("e5", played?.id)
            compose.onNodeWithTag("tracking:remove:show").performScrollTo().performClick()
            compose.onNodeWithText("取消追剧？这也会取消该剧在 Jellyfin 中的收藏。").assertIsDisplayed()
            compose.onNodeWithText("确认").performClick()
            assertEquals("show", removed)
            compose.onNodeWithText("已追平", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("tracking:play:caught").performScrollTo().assertTextContains("查看剧集")
            compose.onNodeWithTag("tracking:detail:show").assertDoesNotExist()
        } finally { scenario.close() }
    }

    @Test fun tvKeepsCaughtUpSeriesAndOnlyOffersDetails() {
        var opened: Media? = null
        val scenario = mount(tv = true) {
            TrackingRoute(TrackingUiState(listOf(caught), loaded = true),
                actions().copy(open = { opened = it }), images)
        }
        try {
            compose.onNodeWithTag("header:tracking").assertIsFocused()
            screenshot("tv")
            compose.onNodeWithText("已追平 · 旧集未看").assertIsDisplayed()
            compose.onNodeWithTag("tracking:play:caught").performScrollTo().performClick()
            assertEquals("caught", opened?.id)
        } finally { scenario.close() }
    }

    @Test fun tvShowsFiveAlignedCardsWithOneProgressLineAndOneToolbar() {
        val entries = (1..5).map { index -> watching.copy(series = watching.series.copy(id = "show-$index", name = "追剧 $index")) }
        val scenario = mount(tv = true) {
            TrackingRoute(TrackingUiState(entries, loaded = true), actions(), images)
        }
        try {
            val first = compose.onNodeWithTag("tracking:play:show-1").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithTag("tracking:play:show-5").fetchSemanticsNode().boundsInRoot
            assertEquals(first.top, last.top, 1f)
            assertEquals(first.bottom, last.bottom, 1f)
            assertTrue(last.left > first.right)
            (1..5).forEach { compose.onNodeWithTag("tracking:play:show-$it").assertIsDisplayed() }
            compose.onAllNodesWithText("继续 S1 E5", useUnmergedTree = true).assertCountEquals(5)
            compose.onAllNodesWithText("还有 3 集未看", substring = true).assertCountEquals(0)
            compose.onAllNodesWithText("取消追剧").assertCountEquals(0)
            val tabs = listOf("all", "WAITING", "WATCHING", "CAUGHT_UP").map {
                compose.onNodeWithTag("tracking:filter:$it").fetchSemanticsNode().boundsInRoot
            }
            assertTrue(tabs.all { kotlin.math.abs(it.center.y - tabs.first().center.y) < 1f })
            compose.onNodeWithText("收藏整部剧即可加入追剧，与当前 Jellyfin 账号同步。").assertDoesNotExist()
        } finally { scenario.close() }
    }

    @Test fun tvCardPlaysTheTargetAndMenuKeepsDetailsAndConfirmedRemoval() {
        var played: Media? = null
        var opened: Media? = null
        var removed: String? = null
        val scenario = mount(tv = true) {
            TrackingRoute(TrackingUiState(listOf(watching), loaded = true),
                actions().copy(play = { item, _, _ -> played = item }, open = { opened = it },
                    removeTracking = { removed = it }), images)
        }
        try {
            val card = compose.onNodeWithTag("tracking:play:show")
            card.performClick()
            assertEquals("e5", played?.id)
            assertNull(opened)
            card.performSemanticsAction(SemanticsActions.RequestFocus)
            card.performSemanticsAction(SemanticsActions.OnLongClick)
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasTestTag("tracking:detail:show") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sheet:close").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("sheet:close").assertIsFocused().performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasTestTag("tracking:play:show") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            card.performKeyInput { pressKey(Key.Menu) }
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasTestTag("tracking:detail:show") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("tracking:detail:show").assertIsFocused().performClick()
            assertEquals("show", opened?.id)
            card.performSemanticsAction(SemanticsActions.RequestFocus)
            card.performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithTag("tracking:remove:show").performClick()
            compose.onNodeWithText("取消追剧？这也会取消该剧在 Jellyfin 中的收藏。").assertIsDisplayed()
            assertNull(removed)
            compose.onNodeWithText("确认").performClick()
            assertEquals("show", removed)
        } finally { scenario.close() }
    }

    @Test fun tvProgressSortingKeepsTheInitialViewportAtTheStart() {
        val pending = (0..5).map { index -> TrackingEntry(watching.series.copy(id = "show-$index", name = "追剧 $index")) }
        val state = mutableStateOf(TrackingUiState(pending, loaded = true, loading = true))
        val scenario = mount(tv = true) { TrackingRoute(state.value, actions(), images) }
        try {
            compose.onNodeWithTag("tracking:play:show-0").assertIsDisplayed()
            compose.runOnIdle {
                state.value = TrackingUiState(pending.mapIndexed { index, entry -> entry.copy(progress =
                    if (index == 0) TrackingProgress(TrackingStatus.UNAVAILABLE) else watching.progress) }, loaded = true)
            }
            compose.onNodeWithTag("tracking:play:show-1").assertIsDisplayed()
            compose.onNodeWithTag("tracking:play:show-5").assertIsDisplayed()
            compose.onNodeWithTag("header:tracking").assertIsFocused()
        } finally { scenario.close() }
    }

    @Test fun returningFromDetailsRetainsFilter() {
        val detail = mutableStateOf(false)
        val scenario = mount {
            val holder = rememberSaveableStateHolder()
            if (!detail.value) holder.SaveableStateProvider("tracking") {
                TrackingRoute(TrackingUiState(listOf(watching, caught), loaded = true),
                    actions().copy(open = { detail.value = true }), images)
            }
        }
        try {
            compose.onNodeWithText("已追平", substring = false).performClick()
            compose.onNodeWithTag("tracking:detail:caught").performScrollTo().performClick()
            compose.runOnIdle { detail.value = false }
            compose.onNodeWithTag("tracking:detail:show").assertDoesNotExist()
            compose.onNodeWithTag("tracking:detail:caught").assertExists()
        } finally { scenario.close() }
    }

    @Test fun mobilePullDownRefreshesAtTop() {
        var refreshed = 0
        val scenario = mount {
            TrackingRoute(TrackingUiState(loaded = true), actions().copy(refreshTracking = { refreshed++ }), images)
        }
        try {
            compose.onNodeWithTag("tracking:list").performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(1, refreshed) }
        } finally { scenario.close() }
    }

    @Test fun homeUsesTrackingAndAnEmptyFavoriteListFallsBackToLatest() {
        val state = mutableStateOf(TrackingUiState(listOf(watching), loaded = true))
        var played: Media? = null
        val latest = Media("movie", "最近入库电影", "Movie")
        val scenario = mount {
            HomeRoute(HomeUiState(content = Home(emptyList(), listOf(latest), emptyList())),
                actions().copy(play = { item, _, _ -> played = item }), images, tracking = state.value)
        }
        try {
            compose.onNodeWithTag("hero:play").performClick()
            assertEquals("e5", played?.id)
            compose.runOnIdle { state.value = TrackingUiState(loaded = true) }
            compose.onNodeWithText("收藏剧集即可追剧").assertExists()
            compose.onNodeWithTag("hero:play").performClick()
            assertEquals("movie", played?.id)
        } finally { scenario.close() }
    }

    private fun actions() = BrowseActions(back = {}, home = {}, open = {}, openPerson = {}, selectEpisode = {},
        retryLibrary = {}, selectSeason = {}, library = { _, _ -> }, search = {}, submitSearch = {}, loadMore = {},
        settings = {}, favorite = {}, play = { _, _, _ -> }, retry = {}, logout = {}, setAutoNext = {})

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "tracking-$name.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
