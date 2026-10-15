package dev.joyfin.ui.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
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
    val directories = ui.directoryGrid
    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
        Column {
            header()
            if (ui.media.isNotEmpty()) {
                RegionLoadFeedback(
                    ui.busy && !ui.appending,
                    ui.error.takeUnless { ui.appending },
                    actions.retry,
                    true,
                    requestKey = Triple(ui.parent, ui.query, ui.person?.id),
                    reserveSpace = false,
                )
            }
        }
    }
    if (ui.media.isEmpty() && ui.busy)
        items(skeletonCount, key = { "skeleton:$it" }) {
            MediaCardSkeleton(Modifier.fillMaxWidth(), wide = directories, visible = skeletonVisible,
                showSubtitle = !directories)
        }
    if (ui.media.isEmpty() && !ui.busy && ui.error != null)
        item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
            ErrorContent(
                title = if (ui.kind == CollectionKind.SEARCH) "暂时无法读取搜索结果" else "暂时无法加载媒体库",
                error = ui.error,
                action = if (ui.kind == CollectionKind.SEARCH) "重新搜索" else "重新加载",
                onAction = actions.retry,
                loading = ui.busy,
            )
        }
    if (
        ui.media.isEmpty() &&
            !ui.busy &&
            ui.error == null &&
            (ui.kind != CollectionKind.SEARCH || ui.query.isNotBlank())
    )
        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            EmptyContent(if (ui.kind == CollectionKind.SEARCH) "没有找到相关作品" else "这里还没有内容",
                if (ui.kind == CollectionKind.SEARCH) "换一个片名、剧名或更短的关键词试试。"
                else "在服务器中添加媒体后，重新进入此页面查看。")
        }
    items(ui.media, key = { it.id }) { media ->
        MediaCard(
            media,
            images,
            { actions.open(media) },
            Modifier.fillMaxWidth(),
            focusKey = "collection:${media.id}",
            wide = directories,
            showSubtitle = !directories,
        )
    }
    if (ui.total > 0)
        item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.appending && !ui.busy && ui.error != null)
                    RegionLoadFeedback(
                        false,
                        ui.error,
                        actions.loadMore,
                        true,
                        reserveSpace = false,
                    )
                val more = ui.nextOffset < ui.total
                if (!ui.appending || ui.error == null) ActionButton(
                    if (!more) "已加载全部 · ${ui.media.size}"
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
            MediaCardSkeleton(Modifier.fillMaxWidth(), wide = directories, visible = skeletonVisible,
                showSubtitle = !directories)
        }
}
