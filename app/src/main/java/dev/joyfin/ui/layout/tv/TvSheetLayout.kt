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
    header: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalConfiguration.current
    val width = (preferredWidth ?: if (side) 420.dp else if (bottom) 800.dp else 512.dp)
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
            Modifier.fillMaxSize().padding(20.dp),
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
                RoundedCornerShape(12.dp),
                actions,
                scrollContent,
                isMobileBottom = false,
                header = header,
                content = content,
            )
        }
    }
}
