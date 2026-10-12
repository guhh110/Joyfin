package dev.joyfin.ui.interaction

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import dev.joyfin.ui.device.LocalDeviceLayout

/** Only explicit navigation events request focus. Resource updates never create an event. */
@Stable
class SeriesFocusCoordinator(
    private val remembered: () -> String,
    private val save: (String) -> Unit,
) {
    val attached = mutableStateMapOf<String, FocusRequester>()
    var pending by mutableStateOf<String?>(remembered().takeIf { it.isNotBlank() })
        private set

    val target: String
        get() = remembered()

    fun move(key: String) {
        pending = key
    }

    fun focused(key: String) {
        save(key)
        pending = null
    }
}

@Composable
fun Modifier.seriesFocus(
    key: String,
    coordinator: SeriesFocusCoordinator,
    onFocused: () -> Unit = {},
): Modifier {
    if (!LocalDeviceLayout.current.television) return testTag(key)
    val requester = remember { FocusRequester() }
    val latest by rememberUpdatedState(onFocused)
    DisposableEffect(coordinator, key) {
        coordinator.attached[key] = requester
        onDispose { if (coordinator.attached[key] === requester) coordinator.attached.remove(key) }
    }
    return testTag(key).focusRequester(requester).onFocusChanged {
        if (it.isFocused) {
            coordinator.focused(key)
            latest()
        }
    }
}

fun seriesPersonFocusKey(person: dev.joyfin.data.Person): String =
    "series:person:${person.id}:${person.name}:${person.role}"
