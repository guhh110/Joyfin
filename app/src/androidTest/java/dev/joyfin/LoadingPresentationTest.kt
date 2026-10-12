package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadingPresentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun mount(
        tv: Boolean,
        content: @Composable () -> Unit,
    ): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                BoundlessTheme {
                    CompositionLocalProvider(
                        LocalDeviceLayout provides DeviceLayout(tv, if (tv) 960 else 360)
                    ) {
                        content()
                    }
                }
            }
        }
        return scenario
    }

    @Test
    fun tvButtonKeepsBoundsAndFocusAndBlocksDuplicateSubmissions() {
        var loading by mutableStateOf(false)
        var calls = 0
        val scenario =
            mount(true) {
                ActionButton(
                    "收藏",
                    { calls++ },
                    Modifier.testTag("action"),
                    icon = Glyph.Plus,
                    loading = loading,
                )
            }
        try {
            val button = compose.onNodeWithTag("action")
            button.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val before = button.getUnclippedBoundsInRoot()
            compose.runOnIdle { loading = true }
            button
                .assertIsFocused()
                .assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在加载"))
                .performClick()
            assertEquals(0, calls)
            assertEquals(before, button.getUnclippedBoundsInRoot())
            compose.onNodeWithText("收藏").assertDoesNotExist()
            compose.runOnIdle { loading = false }
            button.assertIsFocused().assertIsEnabled().performClick()
            assertEquals(1, calls)
            assertEquals(before, button.getUnclippedBoundsInRoot())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun phoneCancellableLoadingUsesTheSameButtonAndNoExtraStatusText() {
        var loading by mutableStateOf(false)
        var cancellations = 0
        val scenario =
            mount(false) {
                ActionButton(
                    "播放本集",
                    { if (loading) cancellations++ else loading = true },
                    Modifier.testTag("play"),
                    loading = loading,
                    enabledWhileLoading = true,
                    loadingLabel = "正在准备，点击取消",
                )
            }
        try {
            val button = compose.onNodeWithTag("play")
            val bounds = button.getUnclippedBoundsInRoot()
            button.performClick()
            button
                .assertIsEnabled()
                .assertContentDescriptionEquals("播放本集")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在准备，点击取消")
                )
            assertEquals(bounds, button.getUnclippedBoundsInRoot())
            compose.onNodeWithText("正在加载…").assertDoesNotExist()
            button.performClick()
            assertEquals(1, cancellations)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun posterAndEpisodeSkeletonsMatchRealCardBoundsAtLargeFontScale() {
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        var skeleton by mutableStateOf(true)
        var wide by mutableStateOf(false)
        val scenario =
            mount(false) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(density.density, 1.4f)
                ) {
                    if (skeleton) MediaCardSkeleton(Modifier.width(232.dp).testTag("card"), wide)
                    else
                        MediaCard(
                            Media("movie", "A very long title", "Movie", genres = listOf("Drama")),
                            images,
                            {},
                            Modifier.width(232.dp).testTag("card"),
                            wide = wide,
                            cardWidth = 232.dp,
                        )
                }
            }
        try {
            for (isWide in listOf(false, true)) {
                compose.runOnIdle {
                    wide = isWide
                    skeleton = true
                }
                val before = compose.onNodeWithTag("card").getUnclippedBoundsInRoot()
                compose.onNodeWithTag("card").assertHasNoClickAction()
                compose.runOnIdle { skeleton = false }
                val after = compose.onNodeWithTag("card").getUnclippedBoundsInRoot()
                assertEquals(
                    (before.right - before.left).value,
                    (after.right - after.left).value,
                    .5f,
                )
                assertEquals(
                    (before.bottom - before.top).value,
                    (after.bottom - after.top).value,
                    .5f,
                )
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun retryRetainsItsFocusAndBoundsDuringTheRequest() {
        var loading by mutableStateOf(false)
        var error by mutableStateOf<String?>("offline")
        val scenario =
            mount(true) {
                RetryAction(
                    "重试",
                    error,
                    loading,
                    {
                        error = null
                        loading = true
                    },
                    Modifier.testTag("retry"),
                )
            }
        try {
            val button = compose.onNodeWithTag("retry")
            button.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val before = button.getUnclippedBoundsInRoot()
            button.performClick()
            button.assertIsFocused().assertIsNotEnabled()
            assertEquals(before, button.getUnclippedBoundsInRoot())
            compose.runOnIdle { loading = false }
            button.assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }
}
