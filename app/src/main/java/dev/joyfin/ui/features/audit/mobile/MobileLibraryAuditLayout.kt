package dev.joyfin.ui.features.audit.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MobileLibraryAuditLayout(header: @Composable () -> Unit, controls: @Composable () -> Unit,
    content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerLimit = maxHeight * .48f
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp)) { header() }
            Column(Modifier.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
