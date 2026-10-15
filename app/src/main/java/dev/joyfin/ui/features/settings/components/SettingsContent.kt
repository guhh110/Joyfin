package dev.joyfin.ui.features.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton("返回", Glyph.Back, actions.back, Modifier.focusTarget("settings:back"))
        FilmTitle("设置", Modifier.weight(1f), maxLines = 1)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption("当前账号")
        SectionTitle(ui.userName)
        Caption(ui.server)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("播放偏好")
        ChoiceRow(
            "自动连播下一集",
            subtitle = if (autoNext) "播放结束后倒计时，可随时取消" else "每集结束后停止",
            selected = autoNext,
        ) {
            actions.setAutoNext(!autoNext)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton("退出当前账号", actions.logout, loading = request.busy)
        Caption("Joyfin ${BuildConfig.VERSION_NAME}")
    }
}
