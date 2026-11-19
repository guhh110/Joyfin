package dev.joyfin

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.data.Media
import dev.joyfin.ui.features.home.tv.TvHomeFeaturedItem
import dev.joyfin.ui.features.home.tv.TvHomeFeaturedReason
import dev.joyfin.ui.features.home.tv.TvHomeFeaturedTitle
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeFeaturedTitleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun tvHeroUsesLogoAndKeepsReadableTitlesWhenMissingOrFailed() {
        var media by mutableStateOf(Media("episode", "第一集", "Episode", seriesId = "series",
            seriesName = "剧集标题", logoId = "series"))
        var largeFont by mutableStateOf(false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var logoFile: File? = null
        try {
            scenario.onActivity { activity ->
                val fixture = File.createTempFile("home-title-", ".png", activity.cacheDir)
                logoFile = fixture
                val bitmap = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "", logo = {
                    when (it.logoTag) {
                        "available" -> fixture.toURI().toString()
                        "broken" -> File(activity.cacheDir, "missing-home-logo.png").toURI().toString()
                        else -> null
                    }
                })
                activity.setContent {
                    BoundlessTheme {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeFont) 1.5f else 1f)) {
                            TvHomeFeaturedTitle(TvHomeFeaturedItem(media.seriesId, media, media, media,
                                TvHomeFeaturedReason.RESUME), images, 1.dp, largeFont = largeFont)
                        }
                    }
                }
            }
            compose.onNodeWithTag("hero:title-text").assertTextEquals("剧集标题").assertIsDisplayed()
            val bounds = compose.onNodeWithTag("hero:title").getUnclippedBoundsInRoot()
            compose.onNodeWithTag("hero:title-logo").assertDoesNotExist()

            compose.runOnIdle { media = media.copy(logoTag = "available") }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("hero:title-logo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("hero:title-logo").assertIsDisplayed()
            compose.onNodeWithContentDescription("剧集标题").assertIsDisplayed()
            compose.onNodeWithTag("hero:title-text").assertDoesNotExist()
            assertEquals(bounds, compose.onNodeWithTag("hero:title").getUnclippedBoundsInRoot())

            compose.runOnIdle { media = media.copy(id = "other", name = "另一部电影", type = "Movie",
                seriesId = "", seriesName = "", logoTag = "broken") }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("hero:title-error").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("hero:title-text").assertTextEquals("另一部电影").assertIsDisplayed()
            compose.onNodeWithTag("hero:title-logo").assertDoesNotExist()
            assertEquals(bounds, compose.onNodeWithTag("hero:title").getUnclippedBoundsInRoot())
            compose.runOnIdle { media = media.copy(logoTag = "") }
            compose.onNodeWithTag("hero:title-text").assertTextEquals("另一部电影").assertIsDisplayed()

            compose.runOnIdle { media = media.copy(name = "群山之间：漫长旅途与归来的故事（演示）") }
            compose.onNodeWithTag("hero:title-text").assertTextEquals("群山之间", "漫长旅途与归来的故事（演示）")
                .assertIsDisplayed()
            assertEquals(bounds, compose.onNodeWithTag("hero:title").getUnclippedBoundsInRoot())
            fun layout(tag: String): TextLayoutResult {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                return layouts.single()
            }
            val main = layout("hero:title-main")
            val subtitle = layout("hero:title-subtitle")
            assertTrue(main.layoutInput.style.fontSize.value > subtitle.layoutInput.style.fontSize.value * 2)
            assertTrue(main.layoutInput.style.color.alpha > subtitle.layoutInput.style.color.alpha)
            assertEquals(1, main.lineCount)
            assertEquals(1, subtitle.lineCount)
            compose.runOnIdle { largeFont = true }
            val largeMain = compose.onNodeWithTag("hero:title-main", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val largeSubtitle = compose.onNodeWithTag("hero:title-subtitle", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(largeMain.bottom < largeSubtitle.top)
            assertTrue(largeMain.top >= bounds.top && largeSubtitle.bottom <= bounds.bottom)
            compose.runOnIdle { media = media.copy(name = "没有分隔符但很长的电影标题仍然保持两行排版而不是机械拆分") }
            compose.onNodeWithTag("hero:title-subtitle", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(2, layout("hero:title-main").lineCount)
            assertEquals(bounds, compose.onNodeWithTag("hero:title").getUnclippedBoundsInRoot())
        } finally {
            scenario.close()
            logoFile?.delete()
        }
    }
}
