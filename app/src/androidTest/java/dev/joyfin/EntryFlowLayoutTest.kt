package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.session.EntryState
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.ConnectionRoute
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an isolated test device; source compilation does not validate Android IME behavior. */
@RunWith(AndroidJUnit4::class)
class EntryFlowLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun phoneCancelPreservesAddressAndSubmissionUsesVisibleInput() {
        var ui by mutableStateOf(EntryState())
        var submitted = ""
        var cancelled = false
        val actions =
            actions()
                .copy(
                    connect = {
                        submitted = it
                        ui = ui.copy(busy = true)
                    },
                    cancel = {
                        cancelled = true
                        ui = ui.copy(busy = false)
                    },
                )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(false, 390)
                        ) {
                            ConnectionRoute(ui, actions)
                        }
                    }
                }
            }
            compose.onNodeWithTag("auth:input").performTextReplacement("192.168.1.8:8096")
            compose
                .onNodeWithTag("auth:submit")
                .performScrollTo()
                .assertHeightIsAtLeast(54.dp)
                .performClick()
            compose.onAllNodesWithTag("auth:submit").assertCountEquals(1)
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
            compose
                .onNodeWithTag("auth:submit")
                .assertIsEnabled()
                .assertContentDescriptionEquals("取消连接")
                .performClick()
            compose.onNodeWithTag("auth:submit").assertTextContains("连接服务器")
            compose.onNodeWithTag("auth:input").assertTextContains("192.168.1.8:8096")
            assertEquals("192.168.1.8:8096", submitted)
            assertTrue(cancelled)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvAuthenticationErrorClearsPasswordAndPreservesSubmitFocus() {
        androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            )
            .pressDPadUp()
        var ui by
            mutableStateOf(EntryState(server = "https://media.example.com", serverName = "测试影院"))
        var logins = 0
        var cancellations = 0
        val actions =
            actions()
                .copy(
                    login = { _, _ ->
                        logins++
                        ui = ui.copy(busy = true, error = null)
                    },
                    cancel = {
                        cancellations++
                        ui = ui.copy(busy = false)
                    },
                )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 1280)
                        ) {
                            RememberScreenFocus("auth:input") {
                                ConnectionRoute(ui, actions, login = true)
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("auth:input").performTextReplacement("demo")
            compose.onNodeWithTag("auth:password").performTextReplacement("prototype-only")
            compose
                .onNodeWithTag("auth:submit")
                .performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            val submit = compose.onNodeWithTag("auth:submit")
            val bounds = submit.getUnclippedBoundsInRoot()
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            submit.assertIsFocused().assertIsEnabled().assertContentDescriptionEquals("取消登录")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            compose.onAllNodesWithTag("auth:submit").assertCountEquals(1)
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            submit.assertIsFocused().assertIsEnabled().assertTextContains("进入影院")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            compose.runOnIdle {
                assertEquals(1, logins)
                assertEquals(1, cancellations)
            }
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle { ui = ui.copy(busy = false, error = "用户名或密码不正确") }
            compose
                .onNodeWithTag("auth:password")
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                        androidx.compose.ui.text.AnnotatedString(""),
                    )
                )
            submit.assertIsFocused().assertIsEnabled().assertTextContains("进入影院")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
            compose.onNodeWithTag("auth:input").assertTextContains("demo")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvConnectionUsesOneActionAndKeepsBoundsAcrossCancelAndFailure() {
        var ui by mutableStateOf(EntryState(server = "https://media.example.com"))
        var connections = 0
        var cancellations = 0
        val actions =
            actions()
                .copy(
                    connect = {
                        connections++
                        ui = ui.copy(busy = true, error = null)
                    },
                    cancel = {
                        cancellations++
                        ui = ui.copy(busy = false)
                    },
                )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            RememberScreenFocus("auth:submit") { ConnectionRoute(ui, actions) }
                        }
                    }
                }
            }
            val submit = compose.onNodeWithTag("auth:submit").performScrollTo()
            submit.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.RequestFocus
            )
            val bounds = submit.getUnclippedBoundsInRoot()
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            submit.assertIsFocused().assertIsEnabled().assertContentDescriptionEquals("取消连接")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            submit.assertIsFocused().assertIsEnabled().assertTextContains("连接服务器")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            compose.runOnIdle {
                assertEquals(1, connections)
                assertEquals(1, cancellations)
            }
            submit.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle {
                ui = ui.copy(busy = false, error = "服务器暂时不可用，请检查网络或地址后重试。".repeat(5))
            }
            submit.assertIsFocused().assertIsEnabled().assertTextContains("连接服务器")
            assertEquals(bounds, submit.getUnclippedBoundsInRoot())
            compose.onAllNodesWithTag("auth:submit").assertCountEquals(1)
            compose.onNodeWithTag("auth:cancel").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvConfirmOpensSystemImeAndBackKeepsFieldFocus() {
        val device =
            androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            )
        device.pressDPadUp()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            RememberScreenFocus("auth:input") {
                                ConnectionRoute(
                                    EntryState(server = "https://media.example.com"),
                                    actions(),
                                    login = true,
                                )
                            }
                        }
                    }
                }
            }
            val field = compose.onNodeWithTag("auth:input").performScrollTo()
            field.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.RequestFocus
            )
            field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            fun imeVisible(): Boolean {
                var visible = false
                scenario.onActivity {
                    visible =
                        androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            compose.waitUntil(5000) { imeVisible() }
            device.pressBack()
            compose.waitUntil(5000) { !imeVisible() }
            field.assertIsFocused()
        } finally {
            scenario.close()
        }
    }

    private fun actions() =
        EntryActions(connect = {}, login = { _, _ -> }, changeServer = {}, cancel = {}, retry = {})
}
