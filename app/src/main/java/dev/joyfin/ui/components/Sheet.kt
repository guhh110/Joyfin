package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.layout.mobile.MobileSheetLayout
import dev.joyfin.ui.layout.tv.TvSheetLayout
import dev.joyfin.ui.theme.Palette

@Composable
fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    side: Boolean = false,
    bottom: Boolean = false,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    scrollContent: Boolean = true,
    preferredWidth: androidx.compose.ui.unit.Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (LocalDeviceLayout.current.television)
            TvSheetLayout(title, onDismiss, side, bottom, modifier, actions, scrollContent, preferredWidth, content)
        else MobileSheetLayout(title, onDismiss, side, modifier, actions, scrollContent, content)
    }
}

@Composable
fun ChoiceRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick,
        modifier.fillMaxWidth().heightIn(min = 64.dp),
        selected = selected,
        transparent = true,
        focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.tv.material3.Text(
                    title,
                    color = Palette.Text,
                )
                if (subtitle.isNotBlank()) Caption(subtitle)
            }
            if (selected) AppIcon(Glyph.Check, color = Palette.Text)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    RememberScreenFocus("confirm:cancel") {
        Sheet(title, onDismiss, actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("取消", onDismiss, Modifier.weight(1f).focusTarget("confirm:cancel"))
                ActionButton("确认", onConfirm, Modifier.weight(1f), primary = true)
            }
        }) {}
    }
}
