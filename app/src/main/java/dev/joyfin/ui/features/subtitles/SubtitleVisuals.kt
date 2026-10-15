package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    maxLines: Int = 2) {
    Text(text, modifier, color = Palette.Text, fontSize = if (large) 28.sp else 16.sp,
        lineHeight = if (large) 36.sp else 23.sp, fontFamily = FontFamily.SansSerif,
        fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Names remain complete, in the same sans-serif family as every other product label. */
@Composable
internal fun SubtitleFileName(name: String, modifier: Modifier = Modifier, emphasizeDetails: Boolean = false, color: Color = Palette.Muted) {
    val text = buildAnnotatedString {
        append(name)
        if (emphasizeDetails) subtitleFileDetails.findAll(name).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
        }
    }
    Text(text, modifier.fillMaxWidth(), color = color, fontSize = 13.sp,
        lineHeight = 21.sp, fontFamily = FontFamily.SansSerif, softWrap = true,
        maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
}

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
    val modifier = Modifier.width(if (tv) 52.dp else 44.dp).aspectRatio(2f / 3f)
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
    Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        if (tv || compact) TvSubtitleResultLayout(details = {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                if (release == title || compact) SubtitleFileName(title, color = Palette.Text) else SubtitleTitle(title)
                if (release.isNotBlank() && release != title) SubtitleFileName(release)
                Caption(metadata)
            }
        }, action = action)
        else MobileSubtitleResultLayout(
            title = { if (release == title) SubtitleFileName(title, color = Palette.Text) else SubtitleTitle(title) },
            release = { if (release.isNotBlank() && release != title) SubtitleFileName(release) },
            metadata = { Caption(metadata) }, action = action,
        )
        error?.let { Caption(it, color = Palette.Error) }
    }
    SubtitleDivider()
}

// Presentation emphasis only; automatic matching continues to belong to SubtitleEpisodeMatcher.
private val subtitleFileDetails = Regex("""(?i)S\d{1,2}E\d{1,3}|第\d{1,3}[集话話]|简体|簡體|繁体|繁體|简繁|簡繁|双语|雙語|\b(?:CHS|CHT|CHI|ENG|JPN)\b|\.(?:ASS|SSA|SRT|VTT)$""")
