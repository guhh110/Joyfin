package dev.joyfin

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.data.Media
import dev.joyfin.ui.features.home.tv.TvHomeEpisodeArtwork
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeCollectionArtworkTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun episodeUsesRealStillThenSoftSeriesPosterAndRecoversFromMissingArtwork() {
        val noStill = Media("no-still", "第一集", "Episode", seriesId = "series",
            seriesName = "同一部剧", seriesImageTag = "series-poster", season = 1, episode = 1)
        val brokenStill = noStill.copy(id = "broken-still")
        val realStill = noStill.copy(id = "real-still")
        val brokenPoster = noStill.copy(id = "broken-poster", seriesId = "missing-series")
        val orphan = noStill.copy(id = "orphan", seriesId = "")
        var current by mutableStateOf(noStill)
        val posterTargets = mutableListOf<Media>()
        val fixtures = mutableListOf<File>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                fun fixture(width: Int, height: Int, color: Int): String {
                    val file = File.createTempFile("home-episode-", ".png", activity.cacheDir)
                    fixtures += file
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(color)
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    return file.toURI().toString()
                }
                val poster = fixture(80, 120, 0xFFCC6633.toInt())
                val still = fixture(160, 90, 0xFF3366CC.toInt())
                val missing = File(activity.cacheDir, "missing-home-episode.png").toURI().toString()
                val images = ArtworkSource({ media ->
                    posterTargets += media
                    if (media.imageId == "series") poster else missing
                }, { error("Episode cards must not request unrelated backdrops") }, {
                    when (it.id) { realStill.id -> still; brokenStill.id -> missing; else -> null }
                }, { null }, "", cacheNamespace = "home-episode-test")
                activity.setContent {
                    BoundlessTheme {
                        Box(Modifier.size(432.dp, 243.dp).testTag("episode-artwork")) {
                            TvHomeEpisodeArtwork(current, images, 1.dp, Modifier.matchParentSize())
                        }
                    }
                }
            }
            fun awaitPoster(item: Media) {
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithTag("home:episode-poster:${item.id}").fetchSemanticsNodes().isNotEmpty() &&
                        compose.onAllNodesWithTag("home:episode-empty:${item.id}").fetchSemanticsNodes().isEmpty()
                }
                compose.onNodeWithTag("home:episode-placeholder:${item.id}")
                    .assertIsDisplayed().assertTextEquals("暂无剧照")
                compose.onNodeWithText("第 1 集").assertDoesNotExist()
                val pixels = compose.onNodeWithTag("episode-artwork").captureToImage().toPixelMap()
                val sample = pixels[pixels.width / 4, pixels.height / 4]
                assertTrue("Poster is visible but subdued", sample.red in .3f..0.6f)
                assertTrue("Poster belongs to the warm-colored series", sample.red > sample.blue + .15f)
            }
            awaitPoster(noStill)
            compose.runOnIdle {
                assertEquals("series", posterTargets.single().imageId)
                assertEquals("series-poster", posterTargets.single().imageTag)
                assertEquals("Series", posterTargets.single().type)
                current = brokenStill
            }
            awaitPoster(brokenStill)
            compose.runOnIdle { current = realStill }
            compose.waitUntil(5_000) {
                val pixels = compose.onNodeWithTag("episode-artwork").captureToImage().toPixelMap()
                val sample = pixels[pixels.width / 4, pixels.height / 4]
                sample.blue > sample.red + .3f
            }
            compose.onNodeWithTag("home:episode-still:${realStill.id}").assertIsDisplayed()
            compose.onNodeWithTag("home:episode-placeholder:${realStill.id}").assertDoesNotExist()
            compose.onNodeWithTag("home:episode-poster:${realStill.id}").assertDoesNotExist()
            compose.runOnIdle { assertEquals(2, posterTargets.size); current = brokenPoster }
            compose.onNodeWithTag("home:episode-empty:${brokenPoster.id}").assertIsDisplayed()
            compose.onNodeWithTag("home:episode-placeholder:${brokenPoster.id}")
                .assertTextEquals("暂无剧照")
            compose.runOnIdle { current = orphan }
            compose.onNodeWithTag("home:episode-poster:${orphan.id}").assertDoesNotExist()
            compose.onNodeWithTag("home:episode-empty:${orphan.id}").assertIsDisplayed()
            val pixels = compose.onNodeWithTag("episode-artwork").captureToImage().toPixelMap()
            val light = pixels[pixels.width / 8, pixels.height / 8]
            val dark = pixels[pixels.width * 3 / 4, pixels.height * 3 / 4]
            assertTrue("Missing images retain a subtle gradient", light.red > dark.red + .025f)
            compose.runOnIdle { current = noStill }
            awaitPoster(noStill)
            compose.onNodeWithTag("home:episode-still:${realStill.id}").assertDoesNotExist()
        } finally {
            scenario.close()
            fixtures.forEach { it.delete() }
        }
    }
}
