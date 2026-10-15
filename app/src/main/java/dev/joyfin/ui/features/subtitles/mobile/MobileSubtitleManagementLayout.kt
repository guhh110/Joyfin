package dev.joyfin.ui.features.subtitles.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.FixedActionArea
import dev.joyfin.ui.theme.Palette

@Composable
internal fun MobileSubtitleManagementLayout(header: @Composable () -> Unit, controls: @Composable () -> Unit,
    content: @Composable () -> Unit, actions: @Composable () -> Unit, hasActions: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) { header(); controls() }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) { content() }
        if (hasActions) FixedActionArea {
            Box(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 20.dp, vertical = 14.dp)) { actions() }
        }
    }
}

@Composable
internal fun MobileSubtitlePackageLayout(artwork: @Composable () -> Unit, details: @Composable () -> Unit,
    status: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        artwork()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            details()
            status()
        }
    }
}

@Composable
internal fun MobileSubtitleResultLayout(title: @Composable () -> Unit, release: @Composable () -> Unit,
    metadata: @Composable () -> Unit, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title(); release(); metadata(); action()
    }
}

@Composable
internal fun MobileSubtitleSearchInput(field: @Composable (Modifier) -> Unit, action: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { field(Modifier.fillMaxWidth()) }
        action()
    }
}
