package dev.joyfin.ui.features.player.mobile

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.SheetPresentationHost
import dev.joyfin.ui.features.player.components.PlayerSubtitleSearchPanel
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun MobilePlayerSubtitleSearchLayout(closing: Boolean, close: () -> Unit, finish: () -> Unit,
    title: String, controls: @Composable (Boolean) -> Unit, results: @Composable () -> Unit) {
    SheetPresentationHost(!closing, finish, MotionTokens.MobileSheetSlideDistance, false) { exiting ->
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            val panelWidth = (maxWidth * .96f).coerceAtMost(960.dp)
            Box(Modifier.matchParentSize().pointerInput(closing) {
                detectTapGestures { if (!exiting && !closing) close() }
            })
            PlayerSubtitleSearchPanel(title, close, Modifier.width(panelWidth).fillMaxHeight(.96f),
                split = panelWidth >= 600.dp, controls = controls, results = results)
        }
    }
}
