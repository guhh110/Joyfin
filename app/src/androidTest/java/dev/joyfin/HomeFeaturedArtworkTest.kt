package dev.joyfin

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.data.Media
import dev.joyfin.ui.features.home.tv.TvHomeFeaturedBackdrop
import dev.joyfin.ui.features.home.tv.TvHomeFeaturedState
import dev.joyfin.ui.features.home.tv.rememberTvHomeFeaturedState
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeFeaturedArtworkTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun stageKeepsLandscapeAndPosterGeometryAndRecoversFromMissingArtwork() {
        val warm = Media("warm", "暖色横图", "Movie", backdropTag = "available")
        val cool = Media("cool", "冷色横图", "Movie", backdropTag = "available")
        val poster = Media("poster-only", "只有海报", "Movie")
        val broken = Media("broken", "图片失败", "Movie", backdropTag = "missing")
        var items by mutableStateOf(listOf(warm, cool, poster, broken))
        lateinit var state: TvHomeFeaturedState
        val fixtures = mutableListOf<File>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                fun fixture(width: Int, height: Int, color: Int): String {
                    val file = File.createTempFile("home-stage-", ".png", activity.cacheDir)
                    fixtures += file
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(color)
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    return file.toURI().toString()
                }
                val warmImage = fixture(160, 90, 0xFFCC6633.toInt())
                val coolImage = fixture(160, 90, 0xFF3366CC.toInt())
                val posterImage = fixture(80, 120, 0xFF668844.toInt())
                val missing = File(activity.cacheDir, "missing-home-stage.png").toURI().toString()
                val images = ArtworkSource({ posterImage }, {
                    when (it.id) { warm.id -> warmImage; cool.id -> coolImage; else -> missing }
                }, { null }, { null }, "", cacheNamespace = "home-artwork-test")
                activity.setContent {
                    BoundlessTheme {
                        state = rememberTvHomeFeaturedState(emptyList(), null, items, images, true, false)
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            TvHomeFeaturedBackdrop(state, minOf(maxWidth / 1920, maxHeight / 1080))
                        }
                    }
                }
            }
            compose.waitForIdle()
            fun awaitArtwork(key: String) {
                compose.waitUntil(5_000) { state.artwork?.key == key }
                compose.waitForIdle()
            }
            awaitArtwork(warm.id)
            compose.onNodeWithTag("hero:backdrop:landscape").assertIsDisplayed()
            val landscapeBounds = compose.onNodeWithTag("hero:backdrop:warm").getUnclippedBoundsInRoot()
            val landscapePixels = compose.onRoot().captureToImage().toPixelMap()
            val left = landscapePixels[(landscapePixels.width * .15f).toInt(), (landscapePixels.height * .42f).toInt()]
            val right = landscapePixels[(landscapePixels.width * .82f).toInt(), (landscapePixels.height * .42f).toInt()]
            assertTrue("Landscape keeps the subject brighter than the text area", right.red > left.red + .25f)
            compose.runOnIdle {
                assertTrue(state.artwork!!.tint.red > state.artwork!!.tint.blue)
                state.onInput()
                state.select(poster.id)
            }
            awaitArtwork(poster.id)
            compose.onNodeWithTag("hero:backdrop:landscape").assertDoesNotExist()
            compose.onNodeWithTag("hero:backdrop:poster").assertIsDisplayed()
            val posterBounds = compose.onNodeWithTag("hero:backdrop:poster").getUnclippedBoundsInRoot()
            val artworkBounds = compose.onNodeWithTag("hero:backdrop:${poster.id}").getUnclippedBoundsInRoot()
            assertEquals(landscapeBounds, posterBounds)
            val artworkWidth = artworkBounds.right - artworkBounds.left
            assertTrue(artworkWidth < (landscapeBounds.right - landscapeBounds.left) / 2)
            assertTrue(artworkBounds.bottom - artworkBounds.top > artworkWidth)
            assertTrue(artworkBounds.left > landscapeBounds.right * .60f)
            assertTrue(artworkBounds.bottom < landscapeBounds.bottom * .76f)
            val posterPixels = compose.onRoot().captureToImage().toPixelMap()
            val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val imageBounds = compose.onNodeWithTag("hero:backdrop:${poster.id}").fetchSemanticsNode().boundsInRoot
            val posterCenter = posterPixels[(imageBounds.center.x - rootBounds.left).toInt(),
                (imageBounds.center.y - rootBounds.top).toInt()]
            assertEquals("Poster is not dimmed by the landscape masks", 102 / 255f, posterCenter.red, .035f)
            assertEquals(136 / 255f, posterCenter.green, .035f)
            assertEquals(68 / 255f, posterCenter.blue, .035f)
            val atmosphere = posterPixels[(posterPixels.width * .91f).toInt(), (posterPixels.height * .42f).toInt()]
            assertTrue("Poster color fills the surrounding stage", atmosphere.green > .08f)

            compose.runOnIdle { state.select(broken.id) }
            awaitArtwork(broken.id)
            compose.onNodeWithTag("hero:backdrop:poster").assertDoesNotExist()
            compose.onNodeWithTag("hero:backdrop:${broken.id}").assertDoesNotExist()
            compose.runOnIdle {
                assertNull(state.artwork!!.request)
                assertEquals(androidx.compose.ui.graphics.Color.Transparent, state.artwork!!.tint)
                state.select(warm.id)
                state.select(cool.id)
            }
            awaitArtwork(cool.id)
            compose.onNodeWithTag("hero:backdrop:${cool.id}").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(state.artwork!!.tint.blue > state.artwork!!.tint.red)
                items = emptyList()
            }
            compose.waitUntil(5_000) { state.artwork == null }
            compose.waitForIdle()
            compose.onNodeWithTag("hero:backdrop:landscape").assertDoesNotExist()
        } finally {
            scenario.close()
            fixtures.forEach { it.delete() }
        }
    }
}
