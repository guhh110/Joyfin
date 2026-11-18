package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleResultLayout
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleResultLayout

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
internal fun SubtitleFileName(name: String, modifier: Modifier = Modifier, emphasizeDetails: Boolean = false,
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
    val text = buildAnnotatedString {
        append(display)
        if (emphasizeDetails) subtitleFileDetails.findAll(display).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
        }
    }
    Text(text, Modifier.fillMaxWidth(), style = style, softWrap = true,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SubtitleFullText(text: String, color: Color = Palette.Muted) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 21.sp, softWrap = true)
}

internal fun subtitleFileMetadata(name: String): String = subtitleFileDetails.findAll(name)
    .map { it.value.trimStart('.').uppercase() }.distinct().joinToString(" · ")

@Composable
internal fun SubtitleStatus(text: String, warning: Boolean = false, subdued: Boolean = false) {
    Text(text, color = if (warning) Palette.Error else if (subdued) Palette.Muted else Palette.Text,
        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun SubtitleNumber(text: String) {
    Text(text, Modifier.width(28.dp), color = Palette.Muted, fontSize = 15.sp,
        fontWeight = FontWeight.Medium, maxLines = 1)
}

/** Only persisted work identity supplies an image; filenames never guess artwork ownership. */
@Composable
internal fun SubtitlePackageArtwork(pack: ManagedSubtitle, images: ArtworkSource) {
    val tv = LocalDeviceLayout.current.television
    val modifier = Modifier.width(if (tv) 48.dp else 40.dp).aspectRatio(2f / 3f)
        .clip(RoundedCornerShape(5.dp))
    if (pack.workId != null) {
        val media = Media(pack.workId, pack.workName, "Folder")
        Artwork(images.poster(media), images, modifier, pack.workName.take(1),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    } else Box(modifier.background(Palette.Surface), contentAlignment = Alignment.Center) {
        AppIcon(Glyph.Subtitle, color = Palette.Muted)
    }
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
internal fun SubtitleResultRow(title: String, metadata: String, release: String,
    error: String? = null, compact: Boolean = false, action: @Composable () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    val inspection = LocalSubtitleInspection.current
    val details = SubtitleDetails(title, release, metadata, error)
    val wrappedAction: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!tv && inspection != null) IconButton("完整名称与详情", Glyph.Info, { inspection.value = details; inspection.opened = true })
            action()
        }
    }
    Column(Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) inspection?.value = details }
        .padding(vertical = if (compact) 4.dp else 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
        if (tv || compact) TvSubtitleResultLayout(details = {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
                if (release == title || compact) SubtitleFileName(title, color = Palette.Text, maxLines = 1) else SubtitleTitle(title, maxLines = 1)
                if (release.isNotBlank() && release != title) SubtitleFileName(release)
                Caption(metadata, maxLines = 1)
            }
        }, action = wrappedAction)
        else MobileSubtitleResultLayout(
            title = { if (release == title) SubtitleFileName(title, color = Palette.Text, maxLines = 1) else SubtitleTitle(title, maxLines = 1) },
            release = { if (release.isNotBlank() && release != title) SubtitleFileName(release) },
            metadata = { Caption(metadata, maxLines = 2) }, action = wrappedAction,
        )
        error?.let { Caption(it, color = Palette.Error, maxLines = 2) }
    }
    SubtitleDivider()
}

// Presentation emphasis only; automatic matching continues to belong to SubtitleEpisodeMatcher.
private val subtitleFileDetails = Regex("""(?i)S\d{1,2}E\d{1,3}|第\d{1,3}[集话話]|简体|簡體|繁体|繁體|简繁|簡繁|双语|雙語|\b(?:CHS|CHT|CHI|ENG|JPN)\b|\.(?:ASS|SSA|SRT|VTT)$""")
