package dev.joyfin.ui.layout.tv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.SheetPanel
import dev.joyfin.ui.components.SheetPresentationHost
import dev.joyfin.ui.theme.MotionTokens

@Composable
internal fun TvSheetLayout(
    title: String,
    onDismiss: () -> Unit,
    side: Boolean,
    bottom: Boolean,
    modifier: Modifier,
    actions: (@Composable ColumnScope.() -> Unit)?,
    scrollContent: Boolean,
    preferredWidth: androidx.compose.ui.unit.Dp?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalConfiguration.current
    val width = (preferredWidth ?: if (side) (config.screenWidthDp.dp * .48f).coerceAtMost(380.dp) else 512.dp)
        .coerceAtMost(config.screenWidthDp.dp * .9f)
    val height =
        if (side) Modifier.fillMaxHeight()
        else Modifier.heightIn(max = config.screenHeightDp.dp * .85f)

    SheetPresentationHost(
        visible = true,
        onDismissRequest = onDismiss,
        slideDistance = if (side) MotionTokens.TvSheetSlideDistance else 0.dp,
        slideHorizontal = side,
    ) { isExiting ->
        Box(
            Modifier.fillMaxSize(),
            contentAlignment =
                if (side) Alignment.CenterEnd
                else if (!bottom) Alignment.Center else Alignment.BottomCenter,
        ) {
            Box(
                Modifier.matchParentSize().pointerInput(onDismiss) {
                    detectTapGestures { if (!isExiting) onDismiss() }
                }
            )
            SheetPanel(
                title,
                onDismiss,
                modifier,
                width,
                height,
                RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = if (side) 0.dp else 12.dp,
                    bottomStart = if (!side && !bottom) 12.dp else 0.dp,
                    bottomEnd = if (!side && !bottom) 12.dp else 0.dp,
                ),
                actions,
                scrollContent,
                isMobileBottom = false,
                content = content,
            )
        }
    }
}
