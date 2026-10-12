package dev.joyfin.ui.features.detail.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.browse.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.model.*
import dev.joyfin.ui.theme.Palette

/** Mobile content height follows its text and never uses a TV viewport budget. */
@Composable
internal fun MobileSeriesDetailLayout(
    content: SeriesOverviewContent,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(content.scroll).testTag("detail:scroll")) {
        HeroBackdrop(content.ui.item, content.images, 0.dp, Modifier.testTag("detail:hero")) {
            Column(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeriesOverviewHeader(content)
                Spacer(Modifier.fillMaxWidth().aspectRatio(2.8f))
                MobileSeriesOverviewHeading(content.ui)
                if (content.feedback.isNotBlank()) SeriesOverviewFeedback(content)
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeriesPlayAction(content, Modifier.weight(1.25f).fillMaxHeight())
                    SeriesFavoriteAction(content, Modifier.weight(.85f).fillMaxHeight())
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SeriesOverviewSections(content)
    }
}

/** Phone text determines its own height; it never depends on the viewport or TV hero budget. */
@Composable
private fun MobileSeriesOverviewHeading(ui: DetailUiState) {
    val media = ui.item
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Caption(
            "剧集${ui.series?.seasons?.data?.size?.let { " · $it 季" }.orEmpty()}",
            color = Palette.Gold,
        )
        if (media?.name.isNullOrBlank()) SkeletonBlock(Modifier.fillMaxWidth(.7f).height(42.dp))
        else
            Text(
                media.name,
                color = Palette.Text,
                fontFamily = FontFamily.Serif,
                fontSize = 36.sp,
                lineHeight = 46.sp,
            )
        if (media != null) MediaMetadata(media)
        if (ui.busy && media?.overview.isNullOrBlank())
            SkeletonText(Modifier.widthIn(max = 680.dp), 3)
        else
            Text(
                media?.overview.orEmpty().ifBlank { "暂无剧情简介" },
                Modifier.fillMaxWidth(),
                color = Palette.Muted,
                fontSize = 14.sp,
                lineHeight = 23.sp,
            )
    }
}
