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
import dev.joyfin.ui.tv.scroll.tvFocusTarget

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
        Column(Modifier.tvFocusTarget(0)) {
            header()
            if (ui.media.isNotEmpty()) {
                RegionLoadFeedback(
                    ui.busy && !ui.appending,
                    ui.error.takeUnless { ui.appending },
                    actions.retry,
                    true,
                    requestKey = listOf(ui.parent, ui.query, ui.person?.id, ui.libraryOptions, ui.worksOnly),
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
                title = when (ui.kind) {
                    CollectionKind.SEARCH -> "暂时无法读取搜索结果"
                    CollectionKind.PERSON -> "暂时无法加载人物作品"
                    CollectionKind.LIBRARY -> "暂时无法加载媒体库"
                },
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
            val (emptyTitle, emptyDesc) = when (ui.kind) {
                CollectionKind.SEARCH -> "没有找到相关作品" to "换一个片名、剧名或更短的关键词试试。"
                CollectionKind.PERSON -> "暂无参演作品" to "服务器中暂无此人物的相关收录作品。"
                CollectionKind.LIBRARY -> if (ui.canArrangeLibrary && ui.libraryOptions.watched != dev.joyfin.data.LibraryWatched.ALL)
                    "没有符合条件的作品" to "可将观看状态切回“全部”，查看这个媒体库的其他内容。"
                    else "这里还没有内容" to "在服务器中添加媒体后，重新进入此页面查看。"
            }
            EmptyContent(emptyTitle, emptyDesc)
        }
    itemsIndexed(ui.media, key = { _, media -> media.id },
        contentType = { _, _ -> if (directories) "directory" else "media" }) { index, media ->
        MediaCard(
            media,
            images,
            { actions.open(media) },
            Modifier.fillMaxWidth(),
            focusKey = "collection:${media.id}",
            focusModifier = Modifier.focusTarget("collection:${media.id}").tvFocusTarget(index + 1),
            wide = directories,
            showSubtitle = !directories,
        )
    }
    if (ui.total > 0 && ui.nextOffset < ui.total)
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
                if (!ui.appending || ui.error == null)
                    ActionButton(
                        "加载更多 · ${ui.media.size} / ${ui.total}",
                        actions.loadMore,
                        Modifier.focusTarget("collection:more"),
                        enabled = !ui.busy,
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
