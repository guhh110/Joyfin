package dev.joyfin.ui.features.audit.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.device.LocalDeviceLayout

@Composable
internal fun TvLibraryAuditLayout(header: @Composable () -> Unit, controls: @Composable () -> Unit,
    content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = LocalDeviceLayout.current.gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        header()
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    }
}
