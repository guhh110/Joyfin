package dev.joyfin.ui.features.player.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.tv.material3.Text
import dev.joyfin.ui.components.*
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.focusTarget

@Composable
internal fun SubtitleTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false,
    maxLines: Int = 1) {
    Text(text, modifier, color = Palette.Text, fontSize = if (large) 28.sp else 16.sp,
        lineHeight = if (large) 34.sp else 22.sp, fontFamily = FontFamily.SansSerif,
        fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Fit the beginning and extension into the available lines; details retain the full name. */
@Composable
internal fun SubtitleFileName(name: String, modifier: Modifier = Modifier,
    color: Color = Palette.Muted, maxLines: Int = 2) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = color, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.SansSerif)
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val width = constraints.maxWidth
    val display = remember(name, width, maxLines, measurer, style) {
        fun fits(value: String) = !measurer.measure(value, style, maxLines = maxLines,
            constraints = Constraints(maxWidth = width)).hasVisualOverflow
        if (fits(name)) name else {
            var low = 0
            var high = name.length
            var fitted = "… " + name.substringAfterLast('.', "").takeLast(8)
            while (low <= high) {
                val count = (low + high) / 2
                val tail = (count * .4f).toInt().coerceAtLeast(1)
                val candidate = name.take((count - tail).coerceAtLeast(0)) + " … " + name.takeLast(tail)
                if (fits(candidate)) { fitted = candidate; low = count + 1 } else high = count - 1
            }
            fitted
        }
    }
    Text(display, Modifier.fillMaxWidth(), style = style, softWrap = true,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SubtitleFullText(text: String, color: Color = Palette.Muted) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 21.sp, softWrap = true)
}

@Composable
internal fun SubtitleDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line)) }

@Composable
internal fun SubtitleEmpty(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(Glyph.Subtitle, color = Palette.Muted)
        SubtitleTitle(title)
        Caption(description, maxLines = 3)
    }
}

/** Search results are unframed rows; only the download control carries a surface. */
@Composable
internal fun SubtitleResultRow(title: String, metadata: String, release: String, focusKey: String,
    error: String? = null, compact: Boolean = false, action: @Composable () -> Unit) {
    var details by remember { mutableStateOf(false) }
    val focus = LocalScreenFocus.current
    if (details) Sheet("字幕详情", { details = false; focus?.restore() }, initialFocus = "sheet:close") {
        SubtitleFullText(title, Palette.Text)
        if (release.isNotBlank() && release != title) SubtitleFullText(release)
        SubtitleFullText(metadata)
        error?.let { SubtitleFullText(it, Palette.Error) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
                if (release == title || compact) SubtitleFileName(title, color = Palette.Text, maxLines = 1) else SubtitleTitle(title, maxLines = 1)
                if (release.isNotBlank() && release != title) SubtitleFileName(release)
                Caption(metadata, maxLines = 1)
            }
            IconButton("完整名称与详情", Glyph.Info, { details = true }, Modifier.focusTarget("$focusKey:info"))
            action()
        }
        error?.let { Caption(it, color = Palette.Error, maxLines = 2) }
    }
    SubtitleDivider()
}

@Composable
internal fun SubtitleSearchInput(field: @Composable (Modifier) -> Unit, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { field(Modifier.fillMaxWidth()) }
        action()
    }
}
