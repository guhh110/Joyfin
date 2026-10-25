package dev.joyfin.ui.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/** Keep a fully visible focus target still; only reveal targets that leave the viewport. */
@OptIn(ExperimentalFoundationApi::class)
internal class FirstViewportScrollSpec(
    private val platform: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (offset >= 0f && offset + size <= containerSize) 0f
        else platform.calculateScrollDistance(offset, size, containerSize)
}

/** Focus scrolling is driven by an explicit animateScrollToItem instead of an instant jump. */
@OptIn(ExperimentalFoundationApi::class)
internal object NoBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
