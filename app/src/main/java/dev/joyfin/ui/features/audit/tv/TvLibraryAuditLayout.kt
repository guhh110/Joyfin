package dev.joyfin.ui.features.audit.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
internal fun TvLibraryAuditLayout(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = LocalDeviceLayout.current.gutter, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) { header() }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
