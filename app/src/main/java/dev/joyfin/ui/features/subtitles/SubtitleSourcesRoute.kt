package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.subtitles.SubtitlePageController
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberSelectionFocus

@Composable
internal fun SubtitleSourcesRoute(controller: SubtitlePageController, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf<String?>(null) }
    var assrt by remember { mutableStateOf("正在读取配置") }
    var os by remember { mutableStateOf("正在读取配置") }
    LaunchedEffect(source) {
        if (source != null) return@LaunchedEffect
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                controller.assrtAccounts.load() to controller.openSubtitlesAccounts.load()
            }.let { (a, b) ->
                assrt = if (!a.enabled) "已停用" else if (a.token.isBlank()) "尚未配置 Token" else "已配置 Token"
                os = if (!b.enabled) "已停用" else if (b.apiKey.isBlank()) "尚未配置 API Key" else if (b.token.isBlank()) "已配置 API Key · 未登录" else "已配置 · 已登录"
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { assrt = "读取配置失败，可进入重试"; os = assrt }
    }
    RememberSelectionFocus("sheet:close") {
        Sheet(when (source) { "assrt" -> "ASSRT"; "os" -> "OpenSubtitles"; else -> "字幕来源设置" },
            { if (source != null) source = null else onDismiss() }) {
            if (source == null) {
                ChoiceRow("ASSRT", subtitle = assrt, chevron = true) { source = "assrt" }
                ChoiceRow("OpenSubtitles", subtitle = os, chevron = true) { source = "os" }
                Caption("凭据仅在本机加密保存。下载后进入待整理，回传 Jellyfin 需单独确认。")
            } else {
                ActionButton("返回来源列表", { source = null }, quiet = true)
                if (source == "assrt") AssrtSettings(controller.assrtAccounts)
                else OpenSubtitlesSettings(controller.openSubtitlesAccounts, controller.openSubtitlesApi)
            }
        }
    }
}
