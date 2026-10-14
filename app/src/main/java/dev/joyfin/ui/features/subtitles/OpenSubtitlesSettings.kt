package dev.joyfin.ui.features.subtitles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.data.subtitles.opensubtitles.*
import dev.joyfin.data.subtitles.subtitleFailure
import dev.joyfin.ui.components.*
import dev.joyfin.ui.features.entry.components.EntryTextField
import dev.joyfin.ui.interaction.focusTarget
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpenSubtitlesSettings(store: OpenSubtitlesAccountStore, api: OpenSubtitlesApi) {
    val scope = rememberCoroutineScope()
    val apiKey = rememberTextFieldState()
    val username = rememberTextFieldState()
    val password = rememberTextFieldState()
    var account by remember { mutableStateOf(OpenSubtitlesAccount()) }
    var enabled by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try {
            account = withContext(Dispatchers.IO) { store.load() }
            apiKey.edit { replace(0, length, account.apiKey) }
            username.edit { replace(0, length, account.username) }
            enabled = account.enabled
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = subtitleFailure(e) }
    }
    fun draft(): OpenSubtitlesAccount {
        val key = apiKey.text.toString().trim()
        val user = username.text.toString().trim()
        require(!enabled || key.isNotBlank() && key.all { it.code in 33..126 })
        val sameIdentity = key == account.apiKey && user == account.username
        return OpenSubtitlesAccount(enabled, key, user, if (sameIdentity) account.token else "",
            if (sameIdentity) account.host else "api.opensubtitles.com")
    }
    fun save(login: Boolean) {
        busy = if (login) "login" else "save"
        val secret = password.text.toString()
        password.edit { replace(0, length, "") }
        scope.launch {
            try {
                var config = draft()
                if (login) {
                    require(config.apiKey.isNotBlank() && config.username.isNotBlank() && secret.isNotBlank())
                    config = api.login(config, secret)
                }
                withContext(Dispatchers.IO) { store.save(config) }
                account = config
                message = if (login) "OpenSubtitles 已登录" else "OpenSubtitles 配置已保存"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = subtitleFailure(e) }
            finally { busy = null }
        }
    }
    SectionTitle("OpenSubtitles")
    ChoiceRow(if (enabled) "已启用" else "已停用", selected = enabled) { if (busy == null) enabled = !enabled }
    EntryTextField("API Key", apiKey, "你的 OpenSubtitles API Key", secret = true, enabled = busy == null, onSubmit = {})
    EntryTextField("用户名（可选登录）", username, "OpenSubtitles 用户名", enabled = busy == null, onSubmit = {})
    EntryTextField("密码", password, "仅用于本次登录，不保存", secret = true, enabled = busy == null, onSubmit = {})
    Caption(if (account.token.isNotBlank()) "已保存登录令牌" else "未登录，可仅保存 API Key；下载权限由字幕站决定")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("保存", { save(false) }, Modifier.focusTarget("opensubtitles:save"), enabled = busy == null, loading = busy == "save")
        ActionButton("登录并保存", { save(true) }, enabled = busy == null, loading = busy == "login")
        ActionButton("移除", {
            busy = "remove"
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.remove() }
                    account = OpenSubtitlesAccount()
                    apiKey.edit { replace(0, length, "") }; username.edit { replace(0, length, "") }
                    message = "OpenSubtitles 配置已移除"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = subtitleFailure(e) }
                finally { busy = null }
            }
        }, enabled = busy == null, loading = busy == "remove")
    }
    message?.let { Caption(it) }
    Caption("API Key 获取：www.opensubtitles.com/consumers")
}
