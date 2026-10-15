package dev.joyfin.ui.features.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.joyfin.browse.CollectionUiState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.collection.MediaCollection
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.features.search.mobile.MobileSearchHeader
import dev.joyfin.ui.features.search.tv.TvSearchHeader
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions

@Composable
fun SearchRoute(ui: CollectionUiState, actions: BrowseActions, images: ArtworkSource) {
    val query = rememberTextFieldState(ui.query)
    val keyboard = LocalSoftwareKeyboardController.current
    fun search() {
        keyboard?.hide()
        actions.submitSearch(query.text.toString())
    }
    MediaCollection(ui, actions, images) {
        val field: @Composable () -> Unit = {
            EntryTextField("影片与剧集", query, "片名、剧名或关键词",
                Modifier.focusTarget("search:input"), onSubmit = ::search)
        }
        val submit: @Composable () -> Unit = {
            ActionButton(
                "搜索",
                { search() },
                icon = Glyph.Search,
                primary = true,
                enabled = !ui.busy,
                loading = ui.busy && !ui.appending,
                maxLines = 1,
            )
        }
        val summary = if (ui.query.isBlank()) "搜索当前服务器的影片与剧集"
            else if (ui.error != null && ui.media.isEmpty()) "“${ui.query}” · 暂时无法读取结果"
            else if (ui.busy && ui.media.isEmpty()) "“${ui.query}”"
            else "“${ui.query}” · ${ui.total} 个结果"
        if (LocalDeviceLayout.current.television) TvSearchHeader(actions.back, summary, field, submit)
        else MobileSearchHeader(actions.back, summary, field, submit)
    }
}
