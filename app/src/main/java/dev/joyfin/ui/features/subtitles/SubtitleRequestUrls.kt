package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.SectionTitle
import dev.joyfin.ui.components.FocusSurface
import dev.joyfin.ui.interaction.focusTarget

/** Page-local diagnostics: actual encoded request URLs, without headers or request bodies. */
@Composable
@Suppress("DEPRECATION")
internal fun SubtitleRequestUrls(urls: List<String>) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("请求 URL（${urls.size}）")
        if (urls.isEmpty()) Caption("尚未发送请求")
        else SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                urls.forEachIndexed { index, url ->
                    FocusSurface(
                        { clipboard.setText(AnnotatedString(url)) },
                        Modifier.fillMaxWidth().focusTarget("subtitle-request:$index"),
                        transparent = true,
                    ) {
                        Caption("${index + 1}. $url", Modifier.fillMaxWidth().padding(8.dp))
                    }
                }
            }
        }
    }
}
