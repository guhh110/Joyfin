package dev.joyfin.ui.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/** Keep controls that fit in the first viewport together, including when returning to it. */
@OptIn(ExperimentalFoundationApi::class)
internal class FirstViewportScrollSpec(
    private val platform: BringIntoViewSpec,
    private val viewportOffset: () -> Float? = { 0f },
    private val holdHero: () -> Boolean,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val origin = viewportOffset()
        if (holdHero() && origin != null) {
            val alignedOffset = offset - origin
            if (alignedOffset >= 0f && alignedOffset + size <= containerSize) return origin
        }
        return platform.calculateScrollDistance(offset, size, containerSize)
    }
}
