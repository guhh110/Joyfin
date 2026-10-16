package dev.joyfin.ui.features.entry.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun MobileEntryLayout(
    step: String,
    login: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .testTag("entry:screen")
    ) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val compactHeight = maxHeight < 620.dp || imeVisible

        // 纯 Compose 渲染手机端沉浸微光漫射场
        EntryArtwork(isTelevision = false)

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(if (compactHeight) 12.dp else 36.dp))

            // 顶部居中或左对齐品牌微组
            EntryBrand(if (compactHeight) 18.sp else 22.sp)

            // 表单视口：自适应滚动与键盘防遮挡
            EntryFormViewport(
                topPadding = if (compactHeight) 16.dp else if (login) 32.dp else 48.dp,
                bottomPadding = if (compactHeight) 12.dp else 24.dp,
                width = 460.dp,
                spacing = 16.dp,
                arrangement = Arrangement.Top,
                content = content,
            )

            if (!imeVisible) {
                EntryFooter(step, bottomPadding = 16.dp)
            }
        }
    }
}
