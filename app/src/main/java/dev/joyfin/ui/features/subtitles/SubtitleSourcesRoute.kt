package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.SubtitlePageController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberSelectionFocus

@Composable
internal fun SubtitleSourcesRoute(controller: SubtitlePageController, onDismiss: () -> Unit) {
    RememberSelectionFocus("sheet:close") {
        Sheet("字幕来源设置", onDismiss) {
            AssrtSettings(controller.assrtAccounts)
            Spacer(Modifier.height(16.dp))
            OpenSubtitlesSettings(controller.openSubtitlesAccounts, controller.openSubtitlesApi)
            Caption("凭据仅在本机加密保存。下载文件进入待整理，回传 Jellyfin 需单独确认。")
        }
    }
}
