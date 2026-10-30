package dev.joyfin.ui.features.home.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import dev.joyfin.browse.HomeUiState
import dev.joyfin.data.Media
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.model.BrowseActions
import kotlinx.coroutines.flow.first

internal val LocalResumeQuickAction = staticCompositionLocalOf<(Media) -> Unit> { {} }

/** The dialog outlives its rail, including removal of the final resume item. */
@Composable
internal fun HomeResumeActionsHost(ui: HomeUiState, actions: BrowseActions, content: @Composable () -> Unit) {
    var item by remember { mutableStateOf<Media?>(null) }
    var itemIndex by remember { mutableIntStateOf(0) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    val focus = LocalScreenFocus.current
    val window = LocalWindowInfo.current
    val television = LocalDeviceLayout.current.television
    fun dismiss() {
        val resume = ui.content?.resume.orEmpty()
        val target = resume.firstOrNull { it.id == item?.id }
            ?: resume.getOrNull(itemIndex.coerceAtMost((resume.size - 1).coerceAtLeast(0)))
        restoreTarget = target?.let { "继续观看:${it.id}" }
            ?: if (ui.content?.latest?.isNotEmpty() == true) "hero:play" else "header:account"
        item = null
        actions.cancelResumeAction()
    }
    LaunchedEffect(ui.resumeAction.completed, ui.resumeAction.itemId) {
        if (ui.resumeAction.completed && ui.resumeAction.itemId == item?.id) dismiss()
    }
    LaunchedEffect(item, restoreTarget) {
        if (item == null && restoreTarget != null && television) {
            snapshotFlow { window.isWindowFocused }.first { it }
            withFrameNanos {}
            val key = requireNotNull(restoreTarget)
            val target = focus?.requesters?.get(key)
            if (target != null) {
                focus?.rememberTarget?.invoke(key)
                target.requestFocus()
            } else {
                focus?.rememberTarget?.invoke("header:account")
                focus?.restore()
            }
            restoreTarget = null
        }
    }
    CompositionLocalProvider(LocalResumeQuickAction provides { media ->
        actions.cancelResumeAction()
        itemIndex = ui.content?.resume.orEmpty().indexOfFirst { it.id == media.id }.coerceAtLeast(0)
        item = media
    }) { content() }
    item?.let { ResumeQuickActionSheet(it, actions, ui.resumeAction, ::dismiss) }
}
