package dev.joyfin.ui.features.entry.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.features.entry.components.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvEntryLayout(step: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Background).testTag("entry:screen")) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        EntryArtwork(
            .67f,
            Alignment.Center,
            Brush.horizontalGradient(
                0f to Color.Transparent,
                .44f to Palette.Background.copy(alpha = .27f),
                .64f to Palette.Background,
                1f to Palette.Background,
            ),
        )
        Column(
            Modifier.fillMaxHeight()
                .fillMaxWidth(.55f)
                .padding(start = 60.dp, top = 44.dp, bottom = 42.dp)
        ) {
            EntryBrand(26.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "PRIVATE COLLECTION / PERSONAL CINEMA",
                color = Palette.Text.copy(alpha = .6f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "把喜欢的故事，\n留给自己。",
                color = Palette.Text,
                fontFamily = FontFamily.SansSerif,
                fontSize = 46.sp,
                lineHeight = 68.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "YOUR STORIES. YOUR CINEMA.",
                color = Palette.Text.copy(alpha = .5f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(64.dp))
            EntryText("01 — ∞", subdued = true)
        }
        Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Spacer(Modifier.weight(1.75f))
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 60.dp)) {
                EntryFormViewport(32.dp, 24.dp, 420.dp, 14.dp, Arrangement.Center, content)
                if (!imeVisible) EntryFooter(step, 30.dp)
            }
        }
    }
}
