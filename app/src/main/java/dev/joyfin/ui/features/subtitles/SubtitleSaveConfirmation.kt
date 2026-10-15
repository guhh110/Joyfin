package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.SubtitleOrganizer
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/** Confirms the existing local save operation; never starts a Jellyfin upload. */
@Composable
internal fun SubtitleSaveConfirmation(organizer: SubtitleOrganizer, dismiss: () -> Unit) {
    val state by organizer.state.collectAsState()
    val pack = state.pack ?: return
    var submitted by remember(pack.id) { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(dismiss)
    LaunchedEffect(submitted, state.saving, state.dirty, state.error, state.notice) {
        val current = organizer.state.value
        if (submitted && !current.saving && current.pack?.id == pack.id &&
            current.pack?.localSaved == true && !current.dirty && current.error == null) latestDismiss()
    }
    RememberSelectionFocus("subtitles:save-confirm") {
        Sheet("保存本地关联", dismiss, actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("取消", dismiss, Modifier.weight(1f).focusTarget("subtitles:save-cancel"), quiet = true)
                ActionButton(if (submitted && state.error != null) "重试保存" else "确认保存", {
                    submitted = true
                    organizer.save()
                }, Modifier.weight(1f).focusTarget("subtitles:save-confirm"), primary = true,
                    loading = state.saving, enabled = !state.loading && !state.saving && pack.workId != null)
            }
        }) {
            SubtitleTitle(listOf(pack.workName, pack.seasonName).filter(String::isNotBlank).joinToString(" · "))
            Caption("保存 ${pack.matched} 份字幕的本地关联。回传 Jellyfin 需另行确认。")
            if (submitted) state.error?.let { Caption(it, color = Palette.Error) }
        }
    }
}
