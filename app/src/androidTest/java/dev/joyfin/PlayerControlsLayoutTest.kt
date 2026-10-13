package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.components.PlayerToggleButton
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun unknownDurationShowsPendingProgressUntilDurationArrives() {
        var duration by mutableStateOf(0L)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        dev.joyfin.ui.features.player.components.SeekBar(
                            60_000L,
                            duration,
                            90_000L,
                            null,
                            dev.joyfin.playback.SeekPreviewActions({}, {}, {}, {}, {}, {}),
                            true,
                        )
                    }
                }
            }
            val progress = compose.onNodeWithContentDescription("播放进度")
            progress
                .assertIsNotEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                        androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate,
                    )
                )
            compose.onNodeWithText("--:--").assertExists()
            compose.runOnIdle { duration = 120_000L }
            progress
                .assertIsEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                        androidx.compose.ui.semantics.ProgressBarRangeInfo(.5f, 0f..1f),
                    )
                )
            compose.onNodeWithText("--:--").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun phoneToggleKeepsTouchSizeAcrossPlayingAndPausedStates() {
        var playing by mutableStateOf(true)
        var loading by mutableStateOf(false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(false, 360)
                        ) {
                            PlayerToggleButton(
                                playing,
                                true,
                                { playing = !playing },
                                Modifier.focusTarget("toggle"),
                                loading = loading,
                            )
                        }
                    }
                }
            }
            val button = compose.onNodeWithTag("toggle")
            button.assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
            val bounds = button.getUnclippedBoundsInRoot()
            button.assertContentDescriptionEquals("暂停").performClick()
            button.assertContentDescriptionEquals("继续播放")
            assertEquals(bounds, button.getUnclippedBoundsInRoot())
            compose.runOnIdle { loading = true }
            button.assertIsNotEnabled().assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
            val spinner =
                compose
                    .onNodeWithTag("player:loading", useUnmergedTree = true)
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
            assertEquals(
                (bounds.left.value + bounds.right.value) / 2,
                (spinner.left.value + spinner.right.value) / 2,
                .5f,
            )
            assertEquals(
                (bounds.top.value + bounds.bottom.value) / 2,
                (spinner.top.value + spinner.bottom.value) / 2,
                .5f,
            )
            compose.runOnIdle { loading = false }
            compose.onNodeWithTag("player:loading", useUnmergedTree = true).assertDoesNotExist()
            button.assertIsEnabled()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvReenabledControlDoesNotStealFocusMovedDuringLoading() {
        androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            )
            .pressDPadUp()
        var enabled by mutableStateOf(true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            RememberScreenFocus("toggle") {
                                Row {
                                    PlayerToggleButton(
                                        true,
                                        enabled,
                                        {},
                                        Modifier.focusTarget("toggle", enabled),
                                    )
                                    PlayerToggleButton(
                                        false,
                                        true,
                                        {},
                                        Modifier.focusTarget("other"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val toggle = compose.onNodeWithTag("toggle")
            toggle.assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp).assertIsFocused()
            compose.runOnIdle { enabled = false }
            compose
                .onNodeWithTag("other")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            compose.runOnIdle { enabled = true }
            compose.onNodeWithTag("other").assertIsFocused()
            toggle.assertIsNotFocused().assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvHudKeepsTransportAndEmptyTrackSlotsAcrossLoadingAndError() {
        var ui by mutableStateOf(dev.joyfin.playback.PlayerUiState(title = "Test"))
        var toggles = 0
        val actions =
            dev.joyfin.playback.PlayerActions(
                toggle = { toggles++ },
                preview = dev.joyfin.playback.SeekPreviewActions({}, {}, {}, {}, {}, {}),
                seek = {},
                selectEpisode = {},
                selectSeason = {},
                selectAudio = {},
                selectSubtitle = {},
                selectBitrate = {},
                speed = {},
                selectVersion = {},
                compatible = {},
                selectCompatibility = {},
                retry = {},
                cancelNext = {},
                nextNow = {},
                close = {},
            )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            dev.joyfin.ui.features.player.PlayerRoute(ui, actions)
                        }
                    }
                }
            }
            val toggle = compose.onNodeWithTag("player:toggle")
            toggle.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.RequestFocus
            )
            val bounds = toggle.getUnclippedBoundsInRoot()
            for (next in
                listOf(
                    ui.copy(loading = true),
                    ui.copy(loading = true, hasRenderedFrame = true, playing = true),
                    ui.copy(error = "网络中断"),
                    ui,
                )) {
                compose.runOnIdle { ui = next }
                toggle.assertIsFocused()
                assertEquals(bounds, toggle.getUnclippedBoundsInRoot())
                compose.onNodeWithTag("player:episodes").assertExists().assertIsNotEnabled()
                compose.onNodeWithTag("player:audio").assertExists().assertIsNotEnabled()
                if (next.error != null) compose.onNodeWithTag("player:retry").assertIsDisplayed()
                else compose.onNodeWithTag("player:retry").assertDoesNotExist()
                listOf("正在读取播放信息…", "正在准备播放", "正在连接视频流", "正在缓冲", "正在等待视频数据").forEach {
                    compose.onNodeWithText(it).assertDoesNotExist()
                }
                if (next.loading && next.error == null) {
                    val spinner =
                        compose
                            .onNodeWithTag("player:loading", useUnmergedTree = true)
                            .assertIsDisplayed()
                            .getUnclippedBoundsInRoot()
                    assertEquals(
                        (bounds.left.value + bounds.right.value) / 2,
                        (spinner.left.value + spinner.right.value) / 2,
                        .5f,
                    )
                    assertEquals(
                        (bounds.top.value + bounds.bottom.value) / 2,
                        (spinner.top.value + spinner.bottom.value) / 2,
                        .5f,
                    )
                    compose
                        .onAllNodesWithTag("player:loading", useUnmergedTree = true)
                        .assertCountEquals(1)
                } else
                    compose
                        .onNodeWithTag("player:loading", useUnmergedTree = true)
                        .assertDoesNotExist()
                if (next.loading || next.error != null) {
                    toggle.assertIsNotEnabled().performKeyInput {
                        pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)
                    }
                    compose.runOnIdle { assertEquals(0, toggles) }
                }
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun hiddenSeekingAndBufferingKeepHudHiddenAndWakeFocusesPlay() {
        var ui by mutableStateOf(dev.joyfin.playback.PlayerUiState(canSeek = true))
        var adjustments = 0
        var toggles = 0
        var commits = 0
        val actions =
            dev.joyfin.playback.PlayerActions(
                toggle = {
                    toggles++
                    ui = ui.copy(playing = !ui.playing)
                },
                preview =
                    dev.joyfin.playback.SeekPreviewActions(
                        { delta ->
                            adjustments++
                            ui =
                                ui.copy(
                                    seekPreview =
                                        dev.joyfin.playback.SeekPreviewState(
                                            30_000,
                                            30_000 + delta,
                                            dev.joyfin.playback.SeekInput.REMOTE,
                                        )
                                )
                        },
                        {},
                        {},
                        {},
                        { commits++ },
                        {},
                    ),
                seek = {},
                selectEpisode = {},
                selectSeason = {},
                selectAudio = {},
                selectSubtitle = {},
                selectBitrate = {},
                speed = {},
                selectVersion = {},
                compatible = {},
                selectCompatibility = {},
                retry = {},
                cancelNext = {},
                nextNow = {},
                close = {},
            )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            dev.joyfin.ui.features.player.PlayerRoute(ui, actions)
                        }
                    }
                }
            }
            compose
                .onNodeWithTag("player:forward")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            androidx.test.uiautomator.UiDevice.getInstance(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                )
                .pressBack()
            compose.onNodeWithTag("player:toggle").assertDoesNotExist()
            val surface = compose.onNodeWithTag("player:surface")
            for (key in
                listOf(
                    androidx.compose.ui.input.key.Key.DirectionRight,
                    androidx.compose.ui.input.key.Key.DirectionLeft,
                )) {
                surface.performKeyInput { pressKey(key) }
                compose.onNodeWithTag("player:toggle").assertDoesNotExist()
            }
            compose.runOnIdle { assertEquals(2, adjustments) }
            surface.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle {
                assertEquals(1, toggles)
                assertEquals(true, ui.playing)
                assertEquals(0, commits)
                assertEquals(true, ui.seekPreview?.active)
            }
            surface.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle {
                assertEquals(2, toggles)
                assertEquals(false, ui.playing)
            }
            compose.onNodeWithTag("player:toggle").assertDoesNotExist()
            surface.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
            compose.runOnIdle {
                assertEquals(3, toggles)
                assertEquals(true, ui.playing)
                assertEquals(0, commits)
                ui = ui.copy(loading = true)
            }
            surface.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle { assertEquals(3, toggles) }
            compose.onNodeWithTag("player:toggle").assertDoesNotExist()
            compose.runOnIdle { ui = ui.copy(loading = false) }
            surface.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
            compose.onNodeWithTag("player:toggle").assertIsDisplayed().assertIsFocused()
        } finally {
            scenario.close()
        }
    }
}
