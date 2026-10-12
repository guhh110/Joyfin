package dev.joyfin.ui.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.features.search.components.SearchField
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
fun SearchRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    var query by rememberSaveable { mutableStateOf(ui.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun search() {
        keyboard?.hide()
        actions.submitSearch(query)
    }
    MediaCollection(ui, actions, images) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton("返回", Glyph.Back, actions.back)
            FilmTitle("寻找一个故事。")
            SearchField(
                "影片与剧集",
                query,
                { query = it },
                Modifier.focusTarget("search:input"),
                onSubmit = { search() },
            )
            ActionButton(
                "搜索",
                { search() },
                icon = Glyph.Search,
                primary = true,
                enabled = !ui.busy,
                loading = ui.busy && !ui.appending,
            )
            Caption(
                if (ui.query.isBlank()) "输入关键词，探索你的媒体库。"
                else if (ui.busy && ui.media.isEmpty()) "“${ui.query}”"
                else "“${ui.query}” · ${ui.total} 个结果"
            )
        }
    }
}
