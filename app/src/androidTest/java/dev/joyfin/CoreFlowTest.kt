package dev.joyfin

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.joyfin.data.SessionStore
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android TV UI, local mock Jellyfin protocol; no real account or server is touched. */
@RunWith(AndroidJUnit4::class)
class CoreFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun connectionLoginBrowseRemoteAndSessionRestore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("joyfin", Context.MODE_PRIVATE).edit().clear().commit()
        val server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl?.encodedPath.orEmpty()
                    android.util.Log.i("JoyfinTest", "Mock API: ${request.method} $path")
                    val body =
                        when {
                            path.endsWith("/System/Info/Public") -> """{"ServerName":"测试服务器"}"""
                            path.endsWith("/Users/AuthenticateByName") ->
                                """{"AccessToken":"mock-token","User":{"Id":"user","Name":"测试用户"}}"""
                            path.endsWith("/UserItems/Resume") ->
                                """{"Items":[],"TotalRecordCount":0}"""
                            path.endsWith("/Items/Latest") ->
                                """[{"Id":"movie","Name":"测试影片","Type":"Movie"}]"""
                            path.endsWith("/UserViews") ->
                                """{"Items":[{"Id":"library","Name":"测试媒体库","Type":"CollectionFolder"}],"TotalRecordCount":1}"""
                            path.endsWith("/Items/movie") ->
                                """{"Id":"movie","Name":"测试影片","Type":"Movie","Overview":"测试简介","RunTimeTicks":600000000,"UserData":{"PlaybackPositionTicks":300000000}}"""
                            path.endsWith("/Users/user") -> """{"Id":"user","Name":"测试用户"}"""
                            path.endsWith("/Items") ->
                                """{"Items":[{"Id":"movie","Name":"测试影片","Type":"Movie"}],"TotalRecordCount":1}"""
                            path.endsWith("/Sessions/Logout") ->
                                return MockResponse().setResponseCode(204)
                            else -> return MockResponse().setResponseCode(404)
                        }
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        server.start()
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose
                .onAllNodes(hasSetTextAction())
                .onFirst()
                .performTextReplacement(server.url("/jellyfin").toString())
            compose
                .onNodeWithText("连接服务器", substring = false)
                .performScrollTo()
                .assertIsDisplayed()
                .performTouchInput { click() }
            waitFor("用户名")
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("测试用户")
            compose
                .onNodeWithText("进入影院", substring = false)
                .performScrollTo()
                .assertIsDisplayed()
                .performTouchInput { click() }
            waitFor("最近加入")
            waitFor("测试影片")
            val device = UiDevice.getInstance(instrumentation)
            // The immersive hero focuses play first; move right to its detail action.
            device.pressDPadRight()
            compose.onNodeWithTag("hero:detail").assertIsFocused()
            device.pressDPadCenter()
            waitFor("测试简介")
            compose.onNodeWithTag("detail:play").assertExists()
            device.pressBack()
            waitFor("最近加入")
            compose.onNodeWithTag("hero:detail").assertIsFocused()
            assertEquals("mock-token", SessionStore(context).load()?.token)
            assertEquals(
                server.url("/jellyfin").toString().trimEnd('/'),
                SessionStore(context).load()?.server,
            )
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor("最近加入")
            compose.onNodeWithTag("hero:play").assertExists()
            compose.onNodeWithContentDescription("搜索").performTouchInput { click() }
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("测试")
            compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
            waitFor("测试影片")
            compose.onNodeWithText("测试影片").assertExists()
        } finally {
            scenario.close()
            server.shutdown()
        }
    }

    private fun waitFor(text: String) {
        try {
            compose.waitUntil(timeoutMillis = 15_000) {
                compose
                    .onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (e: Throwable) {
            throw AssertionError("Missing $text\n${compose.onRoot().printToString()}", e)
        }
    }
}
