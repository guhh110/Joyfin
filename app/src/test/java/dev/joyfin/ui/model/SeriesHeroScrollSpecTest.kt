package dev.joyfin.ui.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class SeriesHeroScrollSpecTest {
    @Test
    fun visibleHeroIsHeldButSecondScreenUsesTheExactPlatformDistance() {
        var hold = true
        var calls = 0
        val platform =
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float {
                    calls++
                    return -123f
                }
            }
        val spec = SeriesHeroScrollSpec(platform) { hold }
        assertEquals(0f, spec.calculateScrollDistance(620f, 60f, 720f), 0f)
        assertEquals(0, calls)
        hold = false
        assertEquals(-123f, spec.calculateScrollDistance(620f, 60f, 720f), 0f)
        assertEquals(1, calls)
    }

    @Test
    fun offscreenOrOversizedTargetsAreNeverPreventedFromBeingRevealed() {
        val platform =
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ) = 87f
            }
        val spec = SeriesHeroScrollSpec(platform) { true }
        assertEquals(87f, spec.calculateScrollDistance(700f, 60f, 720f), 0f)
        assertEquals(87f, spec.calculateScrollDistance(-1f, 60f, 720f), 0f)
        assertEquals(87f, spec.calculateScrollDistance(0f, 800f, 720f), 0f)
    }
}
