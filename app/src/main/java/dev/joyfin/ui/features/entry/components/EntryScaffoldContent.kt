package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.R
import dev.joyfin.ui.theme.Palette

@Composable
internal fun EntryArtwork(widthFraction: Float, alignment: Alignment, brush: Brush) {
    Image(
        painterResource(R.drawable.entry_harbor),
        null,
        Modifier.fillMaxHeight().fillMaxWidth(widthFraction),
        contentScale = ContentScale.Crop,
        alignment = alignment,
    )
    Box(Modifier.fillMaxSize().background(brush))
}

@Composable
internal fun ColumnScope.EntryFormViewport(
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    arrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .testTag("entry:form-scroll")
                .padding(top = topPadding, bottom = bottomPadding),
            verticalArrangement = arrangement,
        ) {
            Column(
                Modifier.widthIn(max = width).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

@Composable
internal fun EntryFooter(step: String, bottomPadding: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "YOUR PERSONAL CINEMA",
            color = Color(0xFF636E75),
            fontSize = 8.sp,
            letterSpacing = 1.5.sp,
        )
        Text(step, color = Palette.Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
internal fun EntryBrand(fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
            val u = size.width / 28f
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(12 * u, 5 * u)
                    lineTo(5 * u, 5 * u)
                    lineTo(5 * u, 23 * u)
                    lineTo(12 * u, 23 * u)
                    moveTo(16 * u, 5 * u)
                    lineTo(23 * u, 5 * u)
                    lineTo(23 * u, 23 * u)
                    lineTo(16 * u, 23 * u)
                    moveTo(12 * u, 10 * u)
                    lineTo(19 * u, 14 * u)
                    lineTo(12 * u, 18 * u)
                    close()
                }
            drawPath(
                path,
                Palette.Text,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
            )
        }
        Text("Joyfin", color = Palette.Text, fontSize = fontSize)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(1.dp).height(14.dp).background(Palette.Line))
        Text("无界影院", color = Palette.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
internal fun EntryStep(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(20.dp, 1.dp).background(EntryChampagne.copy(alpha = .6f)))
        Text(text, color = EntryChampagne, fontSize = 9.sp, letterSpacing = 2.sp)
    }
}
