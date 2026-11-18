package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SubtitleFilePickerHost(title: String, onDismiss: () -> Unit, scrollContent: Boolean = false,
    preferredWidth: Dp? = null, actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (LocalDeviceLayout.current.television) {
        Sheet(title, onDismiss, scrollContent = scrollContent, preferredWidth = preferredWidth ?: 840.dp,
            actions = actions, content = content)
    } else Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            PageHeader(title, onBack = onDismiss)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            FixedActionArea { Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), content = actions) }
        }
    }
}
