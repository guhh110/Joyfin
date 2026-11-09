package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.browse.CollectionKind
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.data.Media
import dev.joyfin.data.Person
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.library.LibraryRoute
import dev.joyfin.ui.features.person.PersonRoute
import dev.joyfin.ui.features.search.SearchRoute
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactHeaderLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
    private val film = Media("film", "演示影片", "Movie")

    @Test fun libraryToolsShareOneTitleRowAndLeaveTheGridVisible() {
        val scenario = mount {
            LibraryRoute(CollectionUiState(title = "名称很长的电影与剧集媒体库", parent = "library",
                media = listOf(film), total = 1, nextOffset = 1), actions(), images)
        }
        try {
            val header = compose.onNodeWithTag("page:header").getUnclippedBoundsInRoot()
            assertTrue(header.bottom - header.top <= 56.dp)
            listOf("library:sort", "library:watched", "library:audit").forEach {
                val tool = compose.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(tool.top >= header.top && tool.bottom <= header.bottom)
            }
            compose.onNodeWithTag("collection:film").assertIsDisplayed()
            assertTrue(compose.onNodeWithTag("collection:film").getUnclippedBoundsInRoot().top < 180.dp)
        } finally { scenario.close() }
    }

    @Test fun tvSearchKeepsTheInputWideAndTheSubmitOnTheSameRow() {
        var query = ""
        val scenario = mount {
            SearchRoute(CollectionUiState(kind = CollectionKind.SEARCH),
                actions().copy(submitSearch = { query = it }), images)
        }
        try {
            val header = compose.onNodeWithTag("page:header").getUnclippedBoundsInRoot()
            val field = compose.onNodeWithTag("search:input").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(header.bottom - header.top <= 60.dp)
            assertTrue(field.right - field.left >= 240.dp)
            val submit = compose.onNodeWithText("搜索", substring = false).getUnclippedBoundsInRoot()
            assertTrue(submit.top >= header.top && submit.bottom <= header.bottom)
            compose.onNodeWithTag("search:input").performTextInput("灯塔")
            compose.onNodeWithText("搜索", substring = false).performClick()
            assertEquals("灯塔", query)
        } finally { scenario.close() }
    }

    @Test fun narrowLargeFontHeaderScrollsToolsWithoutAddingRows() {
        var clicked = false
        val scenario = mount(tv = false, fontScale = 1.6f) {
            PageHeader("一个很长但应当保持单行的页面标题", onBack = {}) {
                repeat(4) { index ->
                    ActionButton("筛选操作 $index", { clicked = true }, Modifier.focusTarget("tool:$index"),
                        quiet = true, maxLines = 1)
                }
            }
        }
        try {
            val before = compose.onNodeWithTag("page:header").getUnclippedBoundsInRoot()
            assertTrue(before.bottom - before.top <= 64.dp)
            compose.onNodeWithTag("tool:3").performScrollTo().assertIsDisplayed().performClick()
            assertTrue(clicked)
            assertEquals(before, compose.onNodeWithTag("page:header").getUnclippedBoundsInRoot())
        } finally { scenario.close() }
    }

    @Test fun narrowLargeFontSearchDoesNotWrapItsPlaceholder() {
        val scenario = mount(tv = false, fontScale = 1.6f) {
            SearchRoute(CollectionUiState(kind = CollectionKind.SEARCH), actions(), images)
        }
        try {
            val header = compose.onNodeWithTag("page:header").getUnclippedBoundsInRoot()
            assertTrue("Large-font search header: $header", header.bottom - header.top <= 64.dp)
            compose.onNodeWithTag("search:input").assertIsDisplayed()
            val submit = compose.onNodeWithText("搜索", substring = false).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(submit.right <= header.right && submit.bottom <= header.bottom)
        } finally { scenario.close() }
    }

    @Test fun personIntroductionIsAvailableWithoutDisplacingFilmography() {
        val scenario = mount {
            PersonRoute(CollectionUiState(kind = CollectionKind.PERSON,
                person = Person("person", "演示演员", "", "演员", ""), personDetail = Media("person", "演示演员", "Person", overview = "完整的人物介绍"),
                media = listOf(film), total = 1, nextOffset = 1), actions(), images)
        }
        try {
            compose.onNodeWithTag("collection:film").assertIsDisplayed()
            assertTrue(compose.onNodeWithTag("collection:film").getUnclippedBoundsInRoot().top < 180.dp)
            compose.onNodeWithTag("person:information").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("person:information").performClick()
            compose.onNodeWithText("完整的人物介绍").assertIsDisplayed()
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasTestTag("sheet:close") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sheet:close").performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasTestTag("person:information") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("person:information").assertIsFocused()
        } finally { scenario.close() }
    }

    private fun mount(tv: Boolean = true, fontScale: Float = 1f, content: @Composable () -> Unit): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity -> activity.setContent {
            BoundlessTheme {
                val density = LocalDensity.current
                val input = LocalInputModeManager.current
                SideEffect { if (tv) input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(tv, if (tv) 960 else 360),
                    LocalDensity provides Density(density.density, fontScale)) {
                    Box(if (tv) Modifier.fillMaxSize() else Modifier.width(360.dp).fillMaxHeight()) {
                        RememberScreenFocus("header:back", content)
                    }
                }
            }
        } }
        return scenario
    }

    private fun actions() = BrowseActions(back = {}, home = {}, open = {}, openPerson = {}, selectEpisode = {},
        retryLibrary = {}, selectSeason = {}, library = { _, _ -> }, search = {}, submitSearch = {}, loadMore = {},
        settings = {}, favorite = {}, play = { _, _, _ -> }, retry = {}, logout = {}, setAutoNext = {})
}
