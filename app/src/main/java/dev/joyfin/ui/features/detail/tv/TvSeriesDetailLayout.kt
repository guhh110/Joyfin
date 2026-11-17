package dev.joyfin.ui.features.detail.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.model.FirstViewportScrollSpec
import dev.joyfin.ui.model.ArtworkSource

/** TV owns the complete introductory viewport; only subsequent sections scroll into view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSeriesDetailLayout(content: SeriesOverviewContent, modifier: Modifier = Modifier) {
    val platformScroll = LocalBringIntoViewSpec.current
    val visibilityScroll = remember(platformScroll, content.scroll, content.focus) {
        FirstViewportScrollSpec(platformScroll) {
            val target = content.focus.pending ?: content.focus.target
            content.scroll.value == 0 && (target.startsWith("header:") || target in setOf("detail:play", "favorite"))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides visibilityScroll) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val height = maxHeight
            val gutter = LocalDeviceLayout.current.gutter
            Column(Modifier.fillMaxSize().verticalScroll(content.scroll).testTag("detail:scroll")) {
                HeroBackdrop(content.ui.item, content.images, 0.dp,
                    Modifier.height(height).testTag("detail:hero")) {
                    Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = 16.dp)) {
                        SeriesOverviewHeader(content)
                        Row(Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            BoxWithConstraints(Modifier.fillMaxWidth(.46f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                val available = maxHeight / LocalDensity.current.fontScale
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TvSeriesIntroduction(content.ui, content.images, available)
                                    if (content.feedback.isNotBlank()) SeriesOverviewFeedback(content)
                                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SeriesPlayAction(content, Modifier.widthIn(min = 144.dp).fillMaxHeight(), Glyph.Play)
                                        SeriesFavoriteAction(content, Modifier.fillMaxHeight())
                                    }
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll) {
                    SeriesOverviewSections(content)
                }
            }
        }
    }
}

@Composable
private fun TvSeriesIntroduction(ui: DetailUiState, images: ArtworkSource, available: Dp) {
    val media = ui.item
    val compact = available < 330.dp
    if (media?.name.isNullOrBlank())
        SkeletonBlock(Modifier.fillMaxWidth(.7f).height(if (compact) 40.dp else 56.dp))
    else MediaTitle(media, images, large = !compact, maxLines = if (compact) 1 else 2,
        logoWidth = 420.dp, logoHeight = if (compact) 40.dp else 88.dp)
    if (media != null) {
        if (!compact && !hasMediaLogo(media, images) && media.originalTitle.isNotBlank() && media.originalTitle != media.name)
            Caption(media.originalTitle, maxLines = 1)
        if (available > 220.dp) MediaMetadata(media)
    }
    if (available > 260.dp) {
        if (ui.busy && media?.overview.isNullOrBlank()) SkeletonText(lines = if (compact) 2 else 3)
        else Caption(media?.overview.orEmpty().ifBlank { "暂无剧情简介" }, maxLines = if (compact) 2 else 3)
    }
}
