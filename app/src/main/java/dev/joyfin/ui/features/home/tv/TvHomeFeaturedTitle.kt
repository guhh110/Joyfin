package dev.joyfin.ui.features.home.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import dev.joyfin.ui.components.rememberArtworkRequest
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

@Composable
internal fun TvHomeFeaturedTitle(item: TvHomeFeaturedItem, images: ArtworkSource, unit: Dp, largeFont: Boolean) {
    @Composable
    fun fallback() {
        val title = tvHomeTitleParts(item.title)
        Column(Modifier.fillMaxWidth().testTag("hero:title-text").semantics(mergeDescendants = true) {}) {
            Text(title.main, color = Palette.Text, fontWeight = FontWeight.SemiBold,
                fontSize = (unit.value * when { largeFont -> 48; title.subtitle != null -> 72; else -> 64 }).sp,
                lineHeight = (unit.value * when { largeFont -> 62; title.subtitle != null -> 84; else -> 78 }).sp,
                maxLines = if (title.subtitle != null) 1 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hero:title-main"))
            title.subtitle?.let { subtitle ->
                Spacer(Modifier.height(unit * 12))
                Text(subtitle, color = Palette.Text.copy(alpha = .72f), fontWeight = FontWeight.Normal,
                    fontSize = (unit.value * if (largeFont) 26 else 30).sp,
                    lineHeight = (unit.value * if (largeFont) 36 else 42).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hero:title-subtitle"))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(unit * 224).testTag("hero:title").semantics { heading() },
        contentAlignment = Alignment.CenterStart) {
        val logo = images.logo(item.media)?.takeIf { it.isNotBlank() }
        if (logo == null) fallback()
        else SubcomposeAsyncImage(
            model = rememberArtworkRequest(logo, images), contentDescription = item.title,
            modifier = Modifier.widthIn(max = unit * 640).fillMaxWidth(), alignment = Alignment.CenterStart,
            contentScale = ContentScale.Fit, loading = { fallback() },
            error = { Box(Modifier.testTag("hero:title-error")) { fallback() } },
            success = {
                SubcomposeAsyncImageContent(Modifier.fillMaxWidth().height(unit * 160).testTag("hero:title-logo"))
            },
        )
    }
}
