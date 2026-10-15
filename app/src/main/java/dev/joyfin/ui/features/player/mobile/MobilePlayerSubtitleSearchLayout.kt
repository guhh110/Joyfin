package dev.joyfin.ui.features.player.mobile

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
internal fun MobilePlayerSubtitleSearchLayout(closing: Boolean, back: () -> Unit, finish: () -> Unit,
    title: String, content: @Composable ColumnScope.() -> Unit) {
    val config = LocalConfiguration.current
    SheetPresentationHost(!closing, finish, MotionTokens.MobileSheetSlideDistance, false) { exiting ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.matchParentSize().pointerInput(closing) {
                detectTapGestures { if (!exiting && !closing) back() }
            })
            SheetPanel(title, back, Modifier, (config.screenWidthDp.dp * .94f).coerceAtMost(720.dp),
                Modifier.fillMaxHeight(.94f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                actions = null, scrollContent = false, content = content)
        }
    }
}
