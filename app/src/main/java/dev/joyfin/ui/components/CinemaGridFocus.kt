package dev.joyfin.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.joyfin.ui.interaction.LocalScreenFocus

/** Resolve only initial/removed grid targets; background refreshes never steal focus. */
@Composable
internal fun CinemaGridFocus(
    ids: List<String>,
    prefix: String,
    loading: Boolean,
    state: LazyGridState,
    headerItems: Int = 0,
    fallback: String = "header:back",
) {
    val focus = LocalScreenFocus.current
    var previous by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Capture before removing lazy items: native focus relocation can otherwise overwrite
    // the removed card's key before this effect gets a chance to choose its neighbour.
    val targetBeforeLayout = focus?.target?.invoke()
    LaunchedEffect(ids, loading) {
        if (loading && ids.isEmpty()) return@LaunchedEffect
        val target = targetBeforeLayout ?: return@LaunchedEffect
        val initial = target == "$prefix:initial"
        val id = target.removePrefix("$prefix:")
        val removed = target.startsWith("$prefix:") && id in previous && id !in ids
        if (initial || removed) {
            val index = if (initial) 0 else previous.indexOf(id).coerceAtLeast(0).coerceAtMost((ids.size - 1).coerceAtLeast(0))
            val next = ids.getOrNull(index)
            focus.rememberTarget(if (next != null) "$prefix:$next" else fallback)
            if (next != null) state.scrollToItem(index + headerItems)
            withFrameNanos { }
            focus.restore()
        }
        previous = ids
    }
}
