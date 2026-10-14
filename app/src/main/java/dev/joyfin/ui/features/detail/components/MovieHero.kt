package dev.joyfin.ui.features.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.*
import dev.joyfin.ui.theme.Palette

@Composable
internal fun MovieHero(
    media: Media?,
    images: ArtworkSource,
    actions: BrowseActions,
    ready: Boolean,
    favoriteBusy: Boolean,
    height: Dp,
    topPadding: Dp,
    gutter: Dp,
    titleSize: TextUnit,
    titleLineHeight: TextUnit,
) {
    val canFavorite = ready && media != null
    HeroBackdrop(media, images, height, Modifier.testTag("detail:hero")) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("header:back"))
                Text("Joyfin", color = Palette.Text, fontSize = 18.sp)
                ActionButton("字幕管理", actions.subtitles, Modifier.focusTarget("detail:subtitles"),
                    enabled = ready, quiet = true)
                IconButton(
                    if (media?.favorite == true) "取消收藏" else "收藏",
                    if (media?.favorite == true) Glyph.Check else Glyph.Plus,
                    actions.favorite,
                    Modifier.focusTarget("favorite", canFavorite),
                    selected = media?.favorite == true,
                    enabled = canFavorite,
                    loading = favoriteBusy,
                )
            }
            Column(
                Modifier.padding(horizontal = gutter).padding(top = topPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Caption("电影", color = Palette.Gold)
                val knownName = media?.name?.takeIf { it.isNotBlank() }
                Box(Modifier.fillMaxWidth()) {
                    // The same text metrics reserve the title slot, including larger system fonts.
                    Text(
                        knownName ?: "\u00a0",
                        modifier =
                            if (knownName == null) Modifier.clearAndSetSemantics {} else Modifier,
                        color = Palette.Text,
                        fontFamily = FontFamily.Serif,
                        fontSize = titleSize,
                        lineHeight = titleLineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (knownName == null)
                        SkeletonBlock(
                            Modifier.fillMaxWidth(.75f)
                                .height(titleSize.value.dp)
                                .align(Alignment.CenterStart)
                        )
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 18.dp)) {
                    val hasMetadata =
                        media != null &&
                            (media.rating != null ||
                                media.year > 0 ||
                                media.genres.isNotEmpty() ||
                                media.officialRating.isNotBlank() ||
                                media.ticks > 0 ||
                                media.sources.isNotEmpty())
                    if (!ready && !hasMetadata)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) { SkeletonBlock(Modifier.width(64.dp).height(14.dp)) }
                        }
                    else if (media != null) MediaMetadata(media)
                }
            }
        }
    }
}
