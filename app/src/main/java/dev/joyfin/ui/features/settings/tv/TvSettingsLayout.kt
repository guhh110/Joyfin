package dev.joyfin.ui.features.settings.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.app.SettingsUiState
import dev.joyfin.session.EntryState
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.settings.components.SettingsContent
import dev.joyfin.ui.model.BrowseActions

@Composable
internal fun TvSettingsLayout(
    ui: EntryState,
    actions: BrowseActions,
    autoNext: Boolean,
    request: SettingsUiState = SettingsUiState(),
    audioOutputMode: dev.joyfin.data.AudioOutputMode = dev.joyfin.data.AudioOutputMode.AUTO,
    homeLayout: dev.joyfin.data.HomeLayoutPreferences = dev.joyfin.data.HomeLayoutPreferences(),
    homeLibraries: List<dev.joyfin.data.Media> = emptyList(),
    updates: dev.joyfin.update.AppUpdateController? = null,
) {
    var category by rememberSaveable { mutableStateOf("account") }
    val categories = listOf("account" to "账号", "playback" to "播放", "library" to "媒体库与首页", "support" to "应用与支持")
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CinemaHeader("设置", actions, active = "settings")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.fillMaxWidth(.22f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (id, label) ->
                    ChoiceRow(label, Modifier.focusTarget("settings:category:$id")
                        .onFocusChanged { if (it.isFocused) category = id },
                        selected = category == id) { category = id }
                }
            }
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = 20.dp)
                .background(Palette.Surface.copy(alpha = .8f), shape)
                .border(.5.dp, Palette.Line, shape).padding(24.dp)) {
                SectionTitle(categories.first { it.first == category }.second)
                Spacer(Modifier.height(20.dp))
                key(category) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SettingsContent(ui, actions, autoNext, request, audioOutputMode,
                            homeLayout, homeLibraries, updates, category)
                    }
                }
            }
        }
    }
}
