package dev.joyfin.ui.features.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.BuildConfig
import dev.joyfin.app.SettingsUiState
import dev.joyfin.session.EntryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun ColumnScope.SettingsContent(
    ui: EntryState,
    actions: BrowseActions,
    autoNext: Boolean,
    request: SettingsUiState = SettingsUiState(),
) {
    IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("settings:back"))
    FilmTitle("账号与偏好。")
    Caption("你的家庭影院")
    SectionTitle(ui.userName)
    Caption(ui.server)
    Spacer(Modifier.height(12.dp))
    SectionTitle("播放偏好")
    ChoiceRow(
        "自动连播下一集",
        subtitle = if (autoNext) "播放结束后倒计时，可随时取消" else "每集结束后停止",
        selected = autoNext,
    ) {
        actions.setAutoNext(!autoNext)
    }
    ActionButton("退出当前账号", actions.logout, loading = request.busy)
    Caption("Joyfin · 无界影院 · ${BuildConfig.VERSION_NAME}")
}
