package dev.joyfin.ui.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/** Only fully visible controls on the initial hero bypass platform scrolling. */
@OptIn(ExperimentalFoundationApi::class)
internal class SeriesHeroScrollSpec(
    private val platform: BringIntoViewSpec,
    private val holdHero: () -> Boolean,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (holdHero() && offset >= 0f && offset + size <= containerSize) 0f
        else platform.calculateScrollDistance(offset, size, containerSize)
}
