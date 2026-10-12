package dev.joyfin.ui.layout.mobile

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

@Composable
internal fun MobileSheetLayout(
    title: String,
    onDismiss: () -> Unit,
    side: Boolean,
    modifier: Modifier,
    actions: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalConfiguration.current
    val width = if (side) (config.screenWidthDp.dp * .48f).coerceAtMost(380.dp) else 520.dp
    val height =
        if (side) Modifier.fillMaxHeight()
        else Modifier.heightIn(max = config.screenHeightDp.dp * .85f)
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = if (side) Alignment.CenterEnd else Alignment.BottomCenter,
    ) {
        Box(
            Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
        )
        SheetPanel(
            title,
            onDismiss,
            modifier,
            width,
            height,
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = if (side) 0.dp else 20.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
            actions,
            content,
        )
    }
}
