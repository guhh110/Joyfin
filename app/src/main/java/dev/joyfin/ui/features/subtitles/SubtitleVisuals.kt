package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.theme.Palette
import dev.joyfin.ui.features.subtitles.tv.TvSubtitleResultLayout
import dev.joyfin.ui.features.subtitles.mobile.MobileSubtitleResultLayout

@Composable
internal fun SubtitleTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false, maxLines: Int = if (large) 1 else 2) {
    Text(text, modifier, color = Palette.Text, fontSize = if (large) 26.sp else 16.sp,
        fontWeight = FontWeight.Medium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** File names carry release/episode/language information at both ends; never ellipsize them. */
@Composable
internal fun SubtitleFileName(name: String, modifier: Modifier = Modifier, emphasizeDetails: Boolean = false) {
    val text = buildAnnotatedString {
        append(name)
        if (emphasizeDetails) subtitleFileDetails.findAll(name).forEach { match ->
            addStyle(SpanStyle(color = Palette.Gold, fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
        }
    }
    Text(text, modifier.fillMaxWidth(), color = Palette.Text, fontSize = 13.sp,
        lineHeight = 20.sp, fontFamily = FontFamily.Monospace, softWrap = true,
        maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
}

@Composable
internal fun SubtitleStatus(text: String, warning: Boolean = false, subdued: Boolean = false) {
    val color = if (warning) Palette.Error else if (subdued) Palette.Muted else Palette.Gold
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .09f)).padding(horizontal = 9.dp, vertical = 4.dp),
        color = color, fontSize = 12.sp, maxLines = 1)
}

@Composable
internal fun SubtitleNumber(text: String) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Gold.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SubtitleDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Muted.copy(alpha = .12f))) }

@Composable
internal fun SubtitleEmpty(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(Glyph.Subtitle, color = Palette.Muted)
        SubtitleTitle(title)
        Caption(description, maxLines = 3)
    }
}

@Composable
internal fun SubtitleResultCard(title: String, metadata: String, release: String, error: String? = null, action: @Composable () -> Unit) {
    val tv = LocalDeviceLayout.current.television
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(if (tv) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tv) TvSubtitleResultLayout(details = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleTitle(title)
                Caption(metadata, color = Palette.Gold, maxLines = 1)
                if (release.isNotBlank()) Caption(release, maxLines = 2)
            }
        }, action = action)
        else MobileSubtitleResultLayout(
            title = { SubtitleTitle(title) },
            release = { if (release.isNotBlank()) Caption(release, maxLines = 2) },
            metadata = { Caption(metadata, color = Palette.Gold, maxLines = 2) },
            action = action,
        )
        error?.let { Caption(it, color = Palette.Error) }
    }
}

// Presentation emphasis only; does not participate in automatic episode matching.
private val subtitleFileDetails = Regex("""(?i)S\d{1,2}E\d{1,3}|第\d{1,3}[集话話]|简体|簡體|繁体|繁體|简繁|簡繁|双语|雙語|\b(?:CHS|CHT|CHI|ENG|JPN)\b|\.(?:ASS|SSA|SRT|VTT)$""")
