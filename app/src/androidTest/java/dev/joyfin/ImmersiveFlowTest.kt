package dev.joyfin

import android.content.Context
import android.graphics.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.joyfin.data.Session
import dev.joyfin.data.SessionStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImmersiveFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun seriesSelectionActorAndRemoteRestoration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("joyfin", Context.MODE_PRIVATE).edit().clear().commit()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val server = MockWebServer()
        val pictures = backdropFixture()
        val people =
            """[{"Id":"actor","Name":"陈屿","Role":"林舟","Type":"Actor","PrimaryImageTag":"face"},{"Id":"director","Name":"顾川","Type":"Director"}]"""
        val series =
            """{"Id":"series","Name":"雾港","Type":"Series","Overview":"潮水带走了所有痕迹。十年后，一封来信让他回到雾港。","ProductionYear":2025,"CommunityRating":8.7,"Genres":["悬疑","剧情"],"OfficialRating":"TV-14","BackdropImageTags":["backdrop"],"ImageTags":{"Primary":"poster"},"People":$people}"""
        val season =
            """{"Id":"season1","Name":"第 1 季","Type":"Season","SeriesId":"series","SeriesName":"雾港","Overview":"旧港的秘密随着潮水浮现。","ChildCount":2,"ParentBackdropItemId":"series","ParentBackdropImageTags":["backdrop"],"People":$people}"""
        val first =
            """{"Id":"e1","Name":"潮声","Type":"Episode","SeriesId":"series","SeriesName":"雾港","SeasonId":"season1","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":28200000000,"ImageTags":{"Primary":"still-e1"},"UserData":{"Played":true}}"""
        val second =
            """{"Id":"e2","Name":"潮汐之间","Type":"Episode","SeriesId":"series","SeriesName":"雾港","SeasonId":"season1","ParentIndexNumber":1,"IndexNumber":2,"Overview":"暴雨将至，林舟沿着无线电信号来到旧港。","PremiereDate":"2025-10-17T00:00:00Z","RunTimeTicks":28200000000,"ImageTags":{"Primary":"still-e2"},"ParentBackdropItemId":"series","ParentBackdropImageTags":["backdrop"],"UserData":{"PlaybackPositionTicks":10800000000},"People":$people,"MediaSources":[{"Id":"source","Name":"4K 原始版本","Container":"mkv","Size":18400000000,"MediaStreams":[{"Type":"Video","Codec":"hevc","Width":3840,"Height":2160,"VideoRangeType":"HDR10"},{"Type":"Audio","DisplayTitle":"普通话 AAC 双声道"},{"Type":"Subtitle","DisplayTitle":"简体中文 SRT"}]}]}"""
        val movie = """{"Id":"movie","Name":"星海回声","Type":"Movie"}"""
        fun page(items: String) =
            """{"Items":[$items],"TotalRecordCount":${if (items.isBlank()) 0 else 1}}"""
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.add(request)
                    val path = request.requestUrl!!.encodedPath
                    if (path.contains("/Images/"))
                        return MockResponse()
                            .setHeader("Content-Type", "image/png")
                            .setBody(Buffer().write(pictures))
                    if (path.contains("/UserFavoriteItems/"))
                        return MockResponse().setResponseCode(204)
                    val body =
                        when {
                            path.endsWith("/Users/user") -> """{"Id":"user"}"""
                            path.endsWith("/UserItems/Resume") -> page("")
                            path.endsWith("/Items/Latest") -> "[$series]"
                            path.endsWith("/UserViews") ->
                                page("""{"Id":"library","Name":"电视剧","Type":"CollectionFolder"}""")
                            path.endsWith("/Items/series") -> series
                            path.endsWith("/Items/season1") -> season
                            path.endsWith("/Items/e1") -> first
                            path.endsWith("/Items/e2") -> second
                            path.endsWith("/Shows/series/Seasons") -> page(season)
                            path.endsWith("/Shows/series/Episodes") ->
                                """{"Items":[$first,$second],"TotalRecordCount":2}"""
                            path.endsWith("/Similar") -> page("")
                            path.contains("/Persons/") ->
                                """{"Id":"actor","Name":"陈屿","Type":"Person","Overview":"演员陈屿的简介。","ImageTags":{"Primary":"face"}}"""
                            path.endsWith("/Items") -> page(movie)
                            else -> return MockResponse().setResponseCode(404)
                        }
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        server.start()
        SessionStore(context)
            .save(
                Session(server.url("/jellyfin").toString().trimEnd('/'), "mock-token", "user", "林"),
            )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val device = UiDevice.getInstance(instrumentation)
        fun shot(name: String) {
            val file = File(context.getExternalFilesDir(null), "ui-$name.png")
            assertTrue(device.takeScreenshot(file))
            device.executeShellCommand(
                "cp ${file.absolutePath} /sdcard/Download/joyfin-ui-$name.png",
            )
        }
        fun remoteClick(tag: String) {
            compose.waitUntil(15_000) {
                compose
                    .onAllNodes(hasTestTag(tag) and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) {
                it()
            }
            compose.onNodeWithTag(tag).assertIsFocused()
            device.pressDPadCenter()
        }
        try {
            waitForTag("hero:play")
            compose.onNodeWithTag("hero:play").assertIsFocused()
            shot("home")
            remoteClick("hero:detail")
            waitForTag("detail:play")
            compose.onNodeWithText("第 1 季 · 第 2 集　看到 18:00").assertExists()
            shot("series")
            remoteClick("favorite")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isNotEmpty()
            }
            remoteClick("choose-season")
            waitForTag("season-choice:season1")
            remoteClick("season-choice:season1")
            waitForTag("series-episodes")
            shot("season")
            compose.onNodeWithTag("series-episodes").performScrollToNode(hasTestTag("episode:e2"))
            remoteClick("episode:e1")
            waitForText("潮声")
            remoteClick("episode:e2")
            waitForText("潮汐之间")
            compose.onNodeWithTag("series-detail").assertExists()
            assertFalse(requests.any { it.requestUrl!!.encodedPath.endsWith("/PlaybackInfo") })
            compose.onNodeWithTag("selected-episode").assertExists()
            device.pressDPadRight()
            compose.waitForIdle()
            compose.onNodeWithTag("episode:play").assertIsFocused()
            device.pressDPadLeft()
            compose.waitForIdle()
            compose.onNodeWithTag("episode:e2").assertIsFocused()
            shot("episode")
            remoteClick("media-info")
            waitForText("媒体信息与版本")
            compose.onNodeWithText("4K 原始版本").assertExists()
            compose.onNodeWithText("音轨 · 普通话 AAC 双声道").assertExists()
            device.pressBack()
            compose
                .onNodeWithTag("series-detail")
                .performScrollToNode(hasTestTag("person:actor:陈屿"))
            remoteClick("person:actor:陈屿")
            waitForText("演员陈屿的简介。")
            waitForTag("collection:movie")
            compose.onNodeWithText("参演作品 · 1").assertExists()
            shot("person")
            device.pressBack()
            waitForTag("person:actor:陈屿")
            compose.onNodeWithTag("person:actor:陈屿").assertIsFocused()
            device.pressBack()
            waitForTag("hero:detail")
            compose.onNodeWithTag("hero:detail").assertIsFocused()
            assertTrue(requests.any { it.requestUrl!!.encodedPath.endsWith("/Images/Backdrop/0") })
            assertTrue(
                requests.any { it.requestUrl!!.encodedPath.endsWith("/Items/e1/Images/Primary") },
            )
            assertTrue(
                requests.any { it.requestUrl!!.encodedPath.endsWith("/Items/e2/Images/Primary") },
            )
            assertTrue(requests.any { it.requestUrl!!.queryParameter("PersonIds") == "actor" })
            assertTrue(
                requests
                    .filter { it.requestUrl!!.encodedPath.contains("/Images/") }
                    .all { it.getHeader("Authorization")?.contains("mock-token") == true },
            )
        } finally {
            scenario.close()
            server.shutdown()
        }
    }

    private fun waitForTag(tag: String) {
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            throw AssertionError("Missing $tag\n${compose.onRoot().printToString()}", e)
        }
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            throw AssertionError("Missing $text\n${compose.onRoot().printToString()}", e)
        }
    }

    private fun backdropFixture(): ByteArray {
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader =
            LinearGradient(
                0f,
                0f,
                1280f,
                720f,
                intArrayOf(Color.rgb(12, 25, 42), Color.rgb(74, 104, 123), Color.rgb(31, 50, 65)),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, 1280f, 720f, paint)
        paint.shader = null
        paint.color = Color.rgb(199, 163, 103)
        canvas.drawCircle(970f, 200f, 60f, paint)
        paint.color = Color.rgb(25, 46, 62)
        repeat(14) { i ->
            canvas.drawRect(600f + i * 43, 430f - (i * 31 % 120), 630f + i * 43, 470f, paint)
        }
        paint.color = Color.rgb(14, 31, 44)
        canvas.drawRect(0f, 470f, 1280f, 720f, paint)
        paint.color = Color.rgb(87, 107, 115)
        paint.strokeWidth = 2f
        repeat(10) { i ->
            canvas.drawLine(700f - i * 9, 490f + i * 20, 1120f + i * 11, 490f + i * 20, paint)
        }
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        return bytes.toByteArray()
    }
}
