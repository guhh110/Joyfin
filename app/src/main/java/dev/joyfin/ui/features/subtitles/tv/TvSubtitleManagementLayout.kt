package dev.joyfin.ui.features.subtitles.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.FixedActionArea
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvSubtitleManagementLayout(header: @Composable () -> Unit, controls: @Composable () -> Unit,
    content: @Composable () -> Unit, actions: @Composable () -> Unit, hasActions: Boolean) {
    val gutter = LocalDeviceLayout.current.gutter
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 24.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) { header(); controls() }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter)) { content() }
        if (hasActions) FixedActionArea {
            Box(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = gutter, vertical = 18.dp)) { actions() }
        }
    }
}

@Composable
internal fun TvSubtitlePackageLayout(artwork: @Composable () -> Unit, details: @Composable () -> Unit,
    status: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        artwork()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { details() }
        Box(Modifier.widthIn(min = 112.dp), contentAlignment = Alignment.CenterEnd) { status() }
    }
}

@Composable
internal fun TvSubtitleResultLayout(details: @Composable () -> Unit, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f)) { details() }
        action()
    }
}

@Composable
internal fun TvSubtitleSearchInput(field: @Composable (Modifier) -> Unit, action: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { field(Modifier.fillMaxWidth()) }
        action()
    }
}
