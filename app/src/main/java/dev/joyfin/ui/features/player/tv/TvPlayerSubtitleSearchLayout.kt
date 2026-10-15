package dev.joyfin.ui.features.player.tv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.SheetPresentationHost
import dev.joyfin.ui.features.player.components.PlayerSubtitleSearchPanel
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun TvPlayerSubtitleSearchLayout(closing: Boolean, close: () -> Unit, finish: () -> Unit,
    title: String, controls: @Composable (Boolean) -> Unit, results: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    SheetPresentationHost(!closing, finish, MotionTokens.TvSheetSlideDistance, true) { exiting ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.matchParentSize().pointerInput(closing) {
                detectTapGestures { if (!exiting && !closing) close() }
            })
            PlayerSubtitleSearchPanel(title, close,
                Modifier.width((config.screenWidthDp.dp * .58f).coerceAtMost(640.dp)).fillMaxHeight(),
                split = false, controls = controls, results = results)
        }
    }
}
