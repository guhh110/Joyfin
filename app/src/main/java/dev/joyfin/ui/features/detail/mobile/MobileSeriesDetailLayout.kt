package dev.joyfin.ui.features.detail.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.theme.Palette

/** Artwork keeps a fixed proportion; phone titles and synopsis determine their own height. */
@Composable
internal fun MobileSeriesDetailLayout(content: SeriesOverviewContent, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val artworkHeight = (maxWidth * .85f).coerceIn(240.dp, 420.dp)
        val favoriteWidth = 112.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        Column(Modifier.fillMaxSize().verticalScroll(content.scroll).testTag("detail:scroll")) {
            HeroBackdrop(content.ui.item, content.images, 0.dp, Modifier.testTag("detail:hero"),
                artworkHeight = artworkHeight) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeriesOverviewHeader(content)
                    Spacer(Modifier.height(artworkHeight * .48f))
                    MobileSeriesIntroduction(content.ui)
                    if (content.feedback.isNotBlank()) SeriesOverviewFeedback(content)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SeriesPlayAction(content, Modifier.weight(1f).fillMaxHeight(), Glyph.Play)
                        SeriesFavoriteAction(content, Modifier.width(favoriteWidth).fillMaxHeight())
                    }
                }
            }
            SeriesOverviewSections(content)
        }
        if (content.scroll.value > 0) Box(Modifier.fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars).background(Palette.Background))
    }
}

@Composable
private fun MobileSeriesIntroduction(ui: DetailUiState) {
    val media = ui.item
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (media?.name.isNullOrBlank()) SkeletonBlock(Modifier.fillMaxWidth(.7f).height(40.dp))
        else FilmTitle(media.name, maxLines = Int.MAX_VALUE)
        if (media != null) {
            if (media.originalTitle.isNotBlank() && media.originalTitle != media.name)
                Caption(media.originalTitle)
            MediaMetadata(media)
        }
        if (ui.busy && media?.overview.isNullOrBlank()) SkeletonText(lines = 3)
        else Caption(media?.overview.orEmpty().ifBlank { "暂无剧情简介" })
    }
}
