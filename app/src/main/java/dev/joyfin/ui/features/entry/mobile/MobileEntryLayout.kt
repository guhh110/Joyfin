package dev.joyfin.ui.features.entry.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
        Modifier.fillMaxSize().background(Palette.Background).testTag("entry:screen")
    ) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val compactHeight = maxHeight < 600.dp || imeVisible
        EntryArtwork(
            1f,
            Alignment.TopCenter,
            Brush.verticalGradient(
                0f to Palette.Background.copy(alpha = .12f),
                .15f to Palette.Background.copy(alpha = .27f),
                .46f to Palette.Background,
                1f to Palette.Background,
            ),
        )
        Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 30.dp)) {
                Spacer(Modifier.height(if (compactHeight) 16.dp else 48.dp))
                EntryBrand(20.sp)
                EntryFormViewport(
                    if (compactHeight) 24.dp else if (login) 52.dp else 92.dp,
                    20.dp,
                    460.dp,
                    16.dp,
                    Arrangement.Top,
                    content,
                )
                if (!imeVisible) EntryFooter(step, 16.dp)
            }
        }
    }
}
