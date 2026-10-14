package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.assrt.*
import dev.joyfin.data.subtitles.subtitleFailure
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.*

@Composable
internal fun AssrtSettings(store: AssrtAccountStore) {
    val scope = rememberCoroutineScope()
    val token = rememberTextFieldState()
    var enabled by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try {
            val config = withContext(Dispatchers.IO) { store.load() }
            token.edit { replace(0, length, config.token) }
            enabled = config.enabled
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = subtitleFailure(e) }
    }
    SectionTitle("ASSRT")
    Caption("使用 assrt.net 的 API Token，无需登录。")
    ChoiceRow(if (enabled) "已启用" else "已停用", selected = enabled) { if (!busy) enabled = !enabled }
    EntryTextField("Token", token, "你的 ASSRT Token", secret = true, enabled = !busy, onSubmit = {})
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("保存", {
            val config = AssrtAccount(enabled, token.text.toString().trim())
            busy = true
            scope.launch {
                try {
                    require(!config.enabled || config.token.matches(Regex("[A-Za-z0-9]{32}")))
                    withContext(Dispatchers.IO) { store.save(config) }
                    message = "ASSRT 配置已保存"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = subtitleFailure(e) }
                finally { busy = false }
            }
        }, Modifier.focusTarget("assrt:save"), enabled = !busy, loading = busy)
        ActionButton("移除", {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.remove() }
                    token.edit { replace(0, length, "") }
                    message = "ASSRT 配置已移除"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = subtitleFailure(e) }
                finally { busy = false }
            }
        }, enabled = !busy)
    }
    message?.let { Caption(it, color = Palette.Muted) }
    Caption("Token 获取：assrt.net/usercp.php")
}
