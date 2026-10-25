package dev.joyfin.ui.tv.scroll

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import dev.joyfin.ui.model.LocalRailBringIntoViewSpec
import dev.joyfin.ui.model.NoBringIntoViewSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class TvFocusItem(val index: Int, val top: Int, val bottom: Int)

/**
 * One vertical focus scroller per TV page.
 *
 * Fully visible targets stay still; targets that leave the viewport are revealed with a single
 * animated scroll. A new focus event cancels the previous animation instead of queueing it.
 */
class TvFocusScroller internal constructor(
    private val scope: CoroutineScope,
    private val itemAt: (Int) -> TvFocusItem?,
    private val viewportEnd: () -> Int,
    private val animateTo: suspend (Int) -> Unit,
) {
    private var job: Job? = null
    private var lastIndex = -1

    fun onFocused(index: Int) {
        if (index < 0 || index == lastIndex) return
        lastIndex = index
        job?.cancel()
        val item = itemAt(index)
        val fullyVisible = item != null && item.top >= 0 && item.bottom <= viewportEnd()
        if (fullyVisible) return
        job = scope.launch { animateTo(index) }
    }
}

@Composable
fun rememberTvFocusScroller(state: LazyListState): TvFocusScroller {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        TvFocusScroller(
            scope = scope,
            itemAt = { index ->
                state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let {
                    TvFocusItem(it.index, it.offset, it.offset + it.size)
                }
            },
            viewportEnd = { state.layoutInfo.viewportEndOffset },
            animateTo = { state.animateScrollToItem(it) },
        )
    }
}

@Composable
fun rememberTvFocusScroller(state: LazyGridState): TvFocusScroller {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        TvFocusScroller(
            scope = scope,
            itemAt = { index ->
                state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let {
                    TvFocusItem(it.index, it.offset.y, it.offset.y + it.size.height)
                }
            },
            viewportEnd = { state.layoutInfo.viewportEndOffset },
            animateTo = { state.animateScrollToItem(it) },
        )
    }
}

val LocalTvFocusScroller = staticCompositionLocalOf<TvFocusScroller?> { null }

/** Reports the target's outer index; the rail's horizontal movement uses the same index. */
@Composable
fun Modifier.tvFocusTarget(index: Int): Modifier {
    val scroller = LocalTvFocusScroller.current
    return onFocusChanged { if (it.isFocused) scroller?.onFocused(index) }
}

/**
 * The vertical container owns focus scrolling. Nested rails keep the platform's horizontal
 * bring-into-view behavior through [LocalRailBringIntoViewSpec].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusScrollHost(scroller: TvFocusScroller, content: @Composable () -> Unit) {
    val platform = LocalBringIntoViewSpec.current
    CompositionLocalProvider(
        LocalTvFocusScroller provides scroller,
        LocalBringIntoViewSpec provides NoBringIntoViewSpec,
        LocalRailBringIntoViewSpec provides platform,
        content = content,
    )
}
