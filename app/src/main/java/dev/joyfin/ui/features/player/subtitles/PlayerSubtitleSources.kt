package dev.joyfin.ui.features.player.subtitles

import androidx.compose.runtime.*
import dev.joyfin.subtitles.PlayerSubtitleSearchController
import dev.joyfin.subtitles.PlayerSubtitleSource
import dev.joyfin.ui.components.*

/** Source credentials are configured inside the player that uses them. */
@Composable
internal fun PlayerSubtitleSources(controller: PlayerSubtitleSearchController) {
    val ui by controller.state.collectAsState()
    Sheet("在线字幕来源", controller::finishConfiguration, initialFocus = "sheet:close") {
        TextTab("ASSRT", { controller.configurationSource(PlayerSubtitleSource.ASSRT) }, ui.source == PlayerSubtitleSource.ASSRT)
        TextTab("OpenSubtitles", { controller.configurationSource(PlayerSubtitleSource.OPEN_SUBTITLES) }, ui.source == PlayerSubtitleSource.OPEN_SUBTITLES)
        key(ui.source) {
            if (ui.source == PlayerSubtitleSource.ASSRT)
                AssrtSettings(controller.assrtAccounts, controller::configurationSaved)
            else OpenSubtitlesSettings(controller.openSubtitlesAccounts, controller.openSubtitlesApi, controller::configurationSaved)
        }
        Caption("凭据仅在本机加密保存。下载的字幕仅用于本机播放。")
    }
}
