package dev.joyfin.ui.features.player.tv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.SheetPanel
import dev.joyfin.ui.components.SheetPresentationHost
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun TvPlayerSubtitleSearchLayout(closing: Boolean, back: () -> Unit, finish: () -> Unit,
    title: String, content: @Composable ColumnScope.() -> Unit) {
    val config = LocalConfiguration.current
    SheetPresentationHost(!closing, finish, MotionTokens.TvSheetSlideDistance, true) { exiting ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.matchParentSize().pointerInput(closing) {
                detectTapGestures { if (!exiting && !closing) back() }
            })
            SheetPanel(title, back, Modifier, (config.screenWidthDp.dp * .58f).coerceAtMost(640.dp),
                Modifier.fillMaxHeight(), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                actions = null, scrollContent = false, content = content)
        }
    }
}
