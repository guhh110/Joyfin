package dev.joyfin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
): Modifier {
    val television = LocalDeviceLayout.current.television
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled || television,
        role = Role.Button,
    ) { if (enabled) onClick() }
        .semantics { if (!enabled) disabled() }
}
