package dev.joyfin.ui.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.joyfin.browse.CollectionKind
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

internal fun LazyGridScope.mediaCollectionItems(
    ui: CollectionUiState,
    actions: BrowseActions,
    images: ArtworkSource,
    header: @Composable () -> Unit,
    skeletonCount: Int,
    skeletonVisible: Boolean,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
    item(span = { GridItemSpan(maxLineSpan) }, key = "feedback") {
        RegionLoadFeedback(
            ui.busy && !ui.appending,
            ui.error.takeUnless { ui.appending },
            actions.retry,
            ui.media.isNotEmpty(),
            requestKey = Triple(ui.parent, ui.query, ui.person?.id),
        )
    }
    if (ui.media.isEmpty() && ui.busy)
        items(skeletonCount, key = { "skeleton:$it" }) {
            MediaCardSkeleton(Modifier.fillMaxWidth(), visible = skeletonVisible)
        }
    if (
        ui.media.isEmpty() &&
            !ui.busy &&
            ui.error == null &&
            (ui.kind != CollectionKind.SEARCH || ui.query.isNotBlank())
    )
        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            EmptyContent("暂无内容", if (ui.query.isNotBlank()) "试试其他关键词。" else "服务器没有返回媒体。")
        }
    items(ui.media, key = { it.id }) { media ->
        MediaCard(
            media,
            images,
            { actions.open(media) },
            Modifier.fillMaxWidth(),
            focusKey = "collection:${media.id}",
        )
    }
    if (ui.total > 0)
        item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
            Column {
                if (ui.appending && !ui.busy)
                    ui.error?.let { Caption(it, color = dev.joyfin.ui.theme.Palette.Error) }
                val more = ui.nextOffset < ui.total
                ActionButton(
                    if (!more) "已加载全部 · ${ui.media.size}"
                    else if (ui.appending && ui.error != null) "重试加载更多"
                    else "加载更多 · ${ui.media.size} / ${ui.total}",
                    actions.loadMore,
                    Modifier.focusTarget("collection:more"),
                    enabled = more && !ui.busy,
                    loading = ui.appending && ui.busy,
                )
            }
        }
    if (ui.appending && ui.busy)
        items(skeletonCount, key = { "append-skeleton:$it" }) {
            MediaCardSkeleton(Modifier.fillMaxWidth(), visible = skeletonVisible)
        }
}
