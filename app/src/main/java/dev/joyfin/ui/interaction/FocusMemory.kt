package dev.joyfin.ui.interaction

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import dev.joyfin.ui.device.LocalDeviceLayout

class ScreenFocus(val target: () -> String, val rememberTarget: (String) -> Unit) {
    internal val requesters = mutableMapOf<String, FocusRequester>()

    fun restore() {
        requesters[target()]?.requestFocus()
    }
}

val LocalScreenFocus = staticCompositionLocalOf<ScreenFocus?> { null }

@Composable
fun RememberScreenFocus(initial: String, content: @Composable () -> Unit) {
    var target by rememberSaveable { mutableStateOf(initial) }
    val memory = remember { ScreenFocus({ target }) { target = it } }
    CompositionLocalProvider(LocalScreenFocus provides memory, content = content)
}

/** Restore the last target when a page re-enters composition, never on ordinary recomposition. */
@Composable
fun Modifier.focusTarget(key: String, enabled: Boolean = true): Modifier {
    if (!LocalDeviceLayout.current.television) return this.testTag(key)
    val memory = LocalScreenFocus.current
    val requester = remember { FocusRequester() }
    val television = LocalDeviceLayout.current.television
    DisposableEffect(memory, key, requester) {
        memory?.requesters?.put(key, requester)
        onDispose { if (memory?.requesters?.get(key) === requester) memory.requesters.remove(key) }
    }
    LaunchedEffect(enabled) {
        if (television && enabled) {
            withFrameNanos {}
            // Re-check after attachment: the user may have moved focus while this target was
            // disabled.
            if (memory?.target?.invoke() == key) requester.requestFocus()
        }
    }
    return this.testTag(key).focusRequester(requester).onFocusChanged {
        if (it.isFocused) memory?.rememberTarget?.invoke(key)
    }
}
