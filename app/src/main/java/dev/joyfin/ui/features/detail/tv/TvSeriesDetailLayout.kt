package dev.joyfin.ui.features.detail.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.browse.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.model.*
import dev.joyfin.ui.theme.Palette

/** TV alone owns the viewport-sized hero and its restricted bring-into-view policy. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSeriesDetailLayout(content: SeriesOverviewContent, modifier: Modifier = Modifier) {
    val platformScroll = LocalBringIntoViewSpec.current
    val visibilityScroll =
        remember(platformScroll, content.scroll, content.focus) {
            FirstViewportScrollSpec(platformScroll) {
                content.scroll.value == 0 &&
                    (content.focus.pending ?: content.focus.target) in
                        setOf("header:back", "detail:play", "favorite")
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides visibilityScroll) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val height = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(content.scroll).testTag("detail:scroll")) {
                HeroBackdrop(
                    content.ui.item,
                    content.images,
                    0.dp,
                    Modifier.height(height).testTag("detail:hero"),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SeriesOverviewHeader(content)
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val available = maxHeight / LocalDensity.current.fontScale
                            Column(
                                Modifier.align(Alignment.BottomStart),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TvSeriesOverviewHeading(content.ui, available)
                            }
                        }
                        SeriesOverviewFeedback(content, Modifier.height(40.dp))
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SeriesPlayAction(
                                content,
                                Modifier.widthIn(min = 220.dp).fillMaxHeight(),
                                Glyph.Play,
                            )
                            SeriesFavoriteAction(content, Modifier.fillMaxHeight())
                        }
                        Spacer(Modifier.height(12.dp))
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
private fun TvSeriesOverviewHeading(ui: DetailUiState, available: Dp) {
    val media = ui.item
    val compact = available < 240.dp
    if (available > 120.dp)
        Caption(
            "剧集${ui.series?.seasons?.data?.size?.let { " · $it 季" }.orEmpty()}",
            color = Palette.Gold,
        )
    if (media?.name.isNullOrBlank())
        SkeletonBlock(Modifier.fillMaxWidth(.65f).height(if (compact) 36.dp else 48.dp))
    else
        Text(
            media.name,
            color = Palette.Text,
            fontFamily = FontFamily.Serif,
            fontSize = if (compact) 36.sp else 54.sp,
            lineHeight = if (compact) 46.sp else 65.sp,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    if (media != null) {
        if (available > 100.dp)
            Text(
                listOfNotNull(
                        media.year.takeIf { it > 0 }?.toString(),
                        media.rating?.let {
                            java.lang.String.format(java.util.Locale.ROOT, "★ %.1f", it)
                        },
                        media.genres.take(2).joinToString(" / ").takeIf { it.isNotBlank() },
                        media.officialRating.takeIf { it.isNotBlank() },
                    )
                    .joinToString(" · "),
                color = Palette.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
    if (available > 180.dp)
        if (ui.busy && media?.overview.isNullOrBlank())
            SkeletonText(Modifier.widthIn(max = 680.dp), 3)
        else
            Text(
                media?.overview.orEmpty().ifBlank { "暂无剧情简介" },
                Modifier.widthIn(max = 680.dp),
                color = Palette.Muted,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
}
