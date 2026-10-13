package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.browse.HomeUiState
import dev.joyfin.data.Home
import dev.joyfin.data.Media
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
    fun tvFirstScreenIncludesResumeAndFocusDoesNotMoveIt() {
        val home =
            Home(
                resume = listOf(Media("resume", "继续的影片", "Movie", resumeTicks = 10)),
                latest = listOf(Media("latest", "最近的影片", "Movie")),
                libraries = emptyList(),
            )
        val actions =
            BrowseActions(
                back = {},
                home = {},
                open = {},
                openPerson = {},
                selectEpisode = {},
                retryLibrary = {},
                selectSeason = {},
                library = { _, _ -> },
                search = {},
                submitSearch = {},
                loadMore = {},
                settings = {},
                favorite = {},
                play = { _, _, _ -> },
                retry = {},
                logout = {},
                setAutoNext = {},
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
                            RememberScreenFocus("header:search") {
                                HomeRoute(HomeUiState(content = home), actions, images)
                            }
                        }
                    }
                }
            }
            val viewport = compose.onNodeWithTag("home:scroll").getUnclippedBoundsInRoot()
            val hero = compose.onNodeWithTag("home:hero").getUnclippedBoundsInRoot()
            assertEquals(viewport.bottom - viewport.top, hero.bottom - hero.top)
            for (target in listOf("hero:play", "hero:detail", "继续观看:resume")) {
                compose
                    .onNodeWithTag(target)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.RequestFocus)
                compose.mainClock.advanceTimeBy(1_000)
                assertEquals(hero, compose.onNodeWithTag("home:hero").getUnclippedBoundsInRoot())
                val bounds = compose.onNodeWithTag(target).getUnclippedBoundsInRoot()
                assertTrue(bounds.top >= hero.top && bounds.bottom <= hero.bottom)
            }
            compose.onNodeWithTag("home:scroll").performScrollToIndex(1)
            compose.onNodeWithTag("最近加入:latest").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
