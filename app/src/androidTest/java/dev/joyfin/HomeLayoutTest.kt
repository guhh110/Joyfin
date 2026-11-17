package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
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
    fun tvStageKeepsBoundsAcrossActionFocusAndLibraryChanges() {
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
            val viewport = compose.onNodeWithTag("home:cinerama").getUnclippedBoundsInRoot()
            val stage = compose.onNodeWithTag("tunnel:stage").getUnclippedBoundsInRoot()
            assertEquals(viewport, stage)
            compose.onNodeWithTag("tunnel:screen")
                .assertIsFocused().assertContentDescriptionEquals("继续观看，继续的影片")
            for (target in listOf("cinema:play", "cinema:detail")) {
                compose
                    .onNodeWithTag(target)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.RequestFocus)
                compose.mainClock.advanceTimeBy(1_000)
                assertEquals(stage, compose.onNodeWithTag("tunnel:stage").getUnclippedBoundsInRoot())
                val bounds = compose.onNodeWithTag(target).getUnclippedBoundsInRoot()
                assertTrue(bounds.top >= stage.top && bounds.bottom <= stage.bottom)
            }
            compose.onNodeWithTag("tunnel:screen")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("tunnel:screen")
                .assertIsFocused().assertContentDescriptionEquals("最近加入，最近的影片")
            assertEquals(stage, compose.onNodeWithTag("tunnel:stage").getUnclippedBoundsInRoot())
            androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            ).pressBack()
            compose.onNodeWithTag("header:tracking").assertIsFocused()
            compose.onNodeWithTag("header:tracking").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("cinema:play").assertIsFocused()
        } finally {
            scenario.close()
        }
    }
}
