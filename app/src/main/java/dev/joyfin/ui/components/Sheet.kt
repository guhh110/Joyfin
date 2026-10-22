package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.RememberSelectionFocus
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rowShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    FocusSurface(
        onClick,
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(
                if (selected) {
                    Modifier
                        .background(androidx.compose.ui.graphics.Color(0xFF161A26), rowShape)
                        .border(0.5.dp, Palette.Line, rowShape)
                } else Modifier
            ),
        selected = selected,
        enabled = enabled,
        transparent = true,
        focusedScale = dev.joyfin.ui.theme.MotionTokens.FixedScale,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.tv.material3.Text(
                    title,
                    color = if (enabled) Palette.Text else Palette.Muted,
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                )
                if (subtitle.isNotBlank()) Caption(subtitle)
            }
            if (selected) AppIcon(Glyph.CheckCircle, Modifier.size(20.dp), color = Palette.Text)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmInitiallyFocused: Boolean = false,
) {
    RememberSelectionFocus(if (confirmInitiallyFocused) "confirm:confirm" else "confirm:cancel") {
        Sheet(title, onDismiss, actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("取消", onDismiss, Modifier.weight(1f).focusTarget("confirm:cancel"))
                ActionButton("确认", onConfirm, Modifier.weight(1f).focusTarget("confirm:confirm"), primary = true)
            }
        }) {}
    }
}
