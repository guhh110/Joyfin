package dev.joyfin.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import dev.joyfin.ui.device.LocalDeviceLayout

/** TV keeps an inert focus slot; touch retains the normal disabled behavior. */
@Composable
fun Modifier.stableAction(
    enabled: Boolean,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier = stableAction(enabled = enabled, interactionSource = interactionSource, onLongClick = null, onClick = onClick)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.stableAction(
    enabled: Boolean,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val television = LocalDeviceLayout.current.television
    val clickModifier = if (onLongClick != null) {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled || television,
            role = Role.Button,
            onLongClick = { if (enabled) onLongClick() },
            onClick = { if (enabled) onClick() },
        )
    } else {
        clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled || television,
            role = Role.Button,
        ) { if (enabled) onClick() }
    }

    val keyModifier = if (television && onLongClick != null) {
        onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && (event.key == Key.Menu || event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU)) {
                if (enabled) {
                    onLongClick()
                    true
                } else false
            } else false
        }
    } else Modifier

    return this.then(clickModifier).then(keyModifier).semantics { if (!enabled) disabled() }
}
