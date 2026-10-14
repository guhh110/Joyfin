package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.SubtitleRequestRecord
import dev.joyfin.ui.components.ActionButton
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.components.SectionTitle
import dev.joyfin.ui.components.FocusSurface
import dev.joyfin.ui.interaction.focusTarget

@Composable
@Suppress("DEPRECATION")
internal fun SubtitleRequestHistoryContent(records: List<SubtitleRequestRecord>) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("最近请求（${records.size} / 50）")
        Caption("选择请求展开响应；JSON 已格式化，凭据已隐藏。历史仅保留在当前页面内存中。")
        if (records.isEmpty()) Caption("尚未发送请求")
        records.asReversed().forEach { record ->
            key(record.id) {
                var expanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusSurface(
                        { expanded = !expanded },
                        Modifier.fillMaxWidth().focusTarget("subtitle-request:${record.id}"),
                        transparent = true,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Caption("#${record.id} · ${record.status?.let { "HTTP $it" } ?: if (record.note == null) "请求中" else "请求记录"} · ${if (expanded) "收起" else "展开"}")
                            SubtitleFileName(record.request)
                        }
                    }
                    if (expanded) {
                        record.note?.let { Caption(it) }
                        record.responseJson?.let { body ->
                            SelectionContainer { SubtitleFileName(body) }
                        }
                        ActionButton("复制请求与响应", {
                            clipboard.setText(AnnotatedString(buildString {
                                appendLine(record.request)
                                record.status?.let { appendLine("HTTP $it") }
                                record.note?.let { appendLine(it) }
                                record.responseJson?.let { append(it) }
                            }))
                        }, Modifier.focusTarget("subtitle-request-copy:${record.id}"), quiet = true)
                    }
                    SubtitleDivider()
                }
            }
        }
    }
}
