package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.joyfin.playback.*
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.PlayerRoute
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise key routing separately from the controller's virtual-time acceleration tests. */
@RunWith(AndroidJUnit4::class)
class RemoteSeekInputTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var ui by mutableStateOf(PlayerUiState(
        playing = true, canSeek = true, positionMs = 50_000, durationMs = 500_000,
    ))
    private val downs = mutableListOf<Int>()
    private val ups = mutableListOf<Int>()
    private var commits = 0
    private var toggles = 0
    private var closes = 0
    private val preview = SeekPreviewActions(
        adjust = { fail("Directional keys must use the hold-aware actions") },
        beginSwipe = {}, swipeOffset = {}, absolute = {},
        commit = {
            commits++
            ui = ui.copy(seekPreview = ui.seekPreview?.copy(active = false, remoteStepMs = null))
        },
        cancel = { ui = ui.copy(seekPreview = null) },
        remoteDown = { direction ->
            downs += direction
            ui = ui.copy(seekPreview = SeekPreviewState(
                50_000, 50_000 + direction * 30_000L, SeekInput.REMOTE,
                remoteStepMs = 30_000, requiresConfirmation = true,
            ))
        },
        remoteUp = { direction ->
            ups += direction
            ui = ui.copy(seekPreview = ui.seekPreview?.copy(remoteStepMs = null))
        },
    )
    private val actions = PlayerActions(
        toggle = { toggles++ }, preview = preview, seek = {}, selectEpisode = {}, selectSeason = {},
        selectAudio = {}, selectSubtitle = {}, selectBitrate = {}, speed = {}, selectVersion = {},
        compatible = {}, selectCompatibility = {}, retry = {}, cancelNext = {}, nextNow = {},
        close = { closes++ },
    )

    private fun mount(): ActivityScenario<MainActivity> {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressDPadUp()
        return ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(LocalDeviceLayout provides DeviceLayout(true, 960)) {
                            PlayerRoute(ui, actions)
                        }
                    }
                }
            }
        }
    }

    private fun hideControls() {
        compose.mainClock.advanceTimeBy(5500)
        compose.onNodeWithTag("player:toggle").assertDoesNotExist()
    }

    @Test
    fun hiddenHoldRoutesOnePressAndReleaseDespiteNativeRepeatsAndConfirmDoesNotPause() {
        compose.mainClock.autoAdvance = false
        val scenario = mount()
        try {
            hideControls()
            val surface = compose.onNodeWithTag("player:surface")
            surface.performKeyInput {
                keyDown(Key.DirectionRight)
                advanceEventTime(1700)
                keyUp(Key.DirectionRight)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(listOf(1), downs)
                assertEquals(listOf(1), ups)
                assertTrue(ui.seekPreview!!.requiresConfirmation)
                assertNull(ui.seekPreview!!.remoteStepMs)
            }
            compose.onNodeWithText("左右微调 · 确认跳转 · 返回取消").assertIsDisplayed()
            compose.onNodeWithTag("player:toggle").assertDoesNotExist()
            surface.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(1, commits)
                assertEquals(0, toggles)
            }
            surface.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle { assertEquals(1, toggles) }
        } finally {
            scenario.close()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun focusedProgressRoutesHoldAndEnterConfirms() {
        compose.mainClock.autoAdvance = false
        val scenario = mount()
        try {
            compose.mainClock.advanceTimeBy(250)
            val progress = compose.onNodeWithTag("player:progress")
            progress.performSemanticsAction(SemanticsActions.RequestFocus)
            progress.performKeyInput {
                keyDown(Key.DirectionLeft)
                advanceEventTime(1700)
                keyUp(Key.DirectionLeft)
            }
            compose.mainClock.advanceTimeByFrame()
            progress.assertIsFocused()
            compose.runOnIdle {
                assertEquals(listOf(-1), downs)
                assertEquals(listOf(-1), ups)
            }
            progress.performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle {
                assertEquals(1, commits)
                assertEquals(0, toggles)
            }
        } finally {
            scenario.close()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun backCancelsHeldPreviewWithoutSeekingOrExiting() {
        compose.mainClock.autoAdvance = false
        val scenario = mount()
        try {
            hideControls()
            compose.onNodeWithTag("player:surface").performKeyInput {
                pressKey(Key.DirectionRight)
            }
            compose.mainClock.advanceTimeByFrame()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertNull(ui.seekPreview)
                assertEquals(0, commits)
                assertEquals(0, closes)
            }
        } finally {
            scenario.close()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun mediaKeyPreviewCanBeFineTunedAndConfirmedFromTransportFocus() {
        compose.mainClock.autoAdvance = false
        val scenario = mount()
        try {
            compose.mainClock.advanceTimeBy(250)
            val toggle = compose.onNodeWithTag("player:toggle")
            toggle.performSemanticsAction(SemanticsActions.RequestFocus)
            compose.runOnIdle {
                ui = ui.copy(seekPreview = SeekPreviewState(
                    50_000, 200_000, SeekInput.REMOTE, requiresConfirmation = true,
                ))
            }
            compose.mainClock.advanceTimeByFrame()
            toggle.performKeyInput { pressKey(Key.DirectionLeft) }
            compose.mainClock.advanceTimeByFrame()
            toggle.assertIsFocused()
            toggle.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle {
                assertEquals(listOf(-1), downs)
                assertEquals(listOf(-1), ups)
                assertEquals(1, commits)
                assertEquals(0, toggles)
            }
        } finally {
            scenario.close()
            compose.mainClock.autoAdvance = true
        }
    }
}
