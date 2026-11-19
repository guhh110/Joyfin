package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.ui.features.home.tv.TvHomeBackdrop
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCollectionBackdropTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun collectionKeepsTheBackgroundVisibleWithDarkTextAndNavigationEdges() {
        val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        TvHomeBackdrop(null, images, collection = true) {
                            Box(Modifier.fillMaxSize().background(Color(.8f, .8f, .8f)))
                        }
                    }
                }
            }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val left = pixels[(pixels.width * .12f).toInt(), (pixels.height * .43f).toInt()]
            val right = pixels[(pixels.width * .85f).toInt(), (pixels.height * .43f).toInt()]
            val top = pixels[(pixels.width * .85f).toInt(), (pixels.height * .04f).toInt()]
            val bottom = pixels[(pixels.width * .85f).toInt(), (pixels.height * .96f).toInt()]
            assertTrue("The background subject stays visible", right.red > .45f)
            assertTrue("Text retains a darker left side", left.red < .35f && right.red > left.red + .2f)
            assertTrue("Top navigation keeps its shading", top.red < right.red)
            assertTrue("Bottom navigation keeps its shading", bottom.red < right.red * .5f)
        } finally {
            scenario.close()
        }
    }
}
