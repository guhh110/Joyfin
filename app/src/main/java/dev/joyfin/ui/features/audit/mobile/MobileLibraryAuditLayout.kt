package dev.joyfin.ui.features.audit.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MobileLibraryAuditLayout(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerLimit = maxHeight * .48f
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { header() }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
