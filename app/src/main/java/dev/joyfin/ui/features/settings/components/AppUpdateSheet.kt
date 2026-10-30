package dev.joyfin.ui.features.settings.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joyfin.BuildConfig
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.update.AppUpdateController
import dev.joyfin.update.UpdatePhase

@Composable
internal fun AppUpdateSheet(controller: AppUpdateController, dismiss: () -> Unit) {
    val ui by controller.state.collectAsStateWithLifecycle()
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun launchInstaller() {
        try {
            controller.clearError()
            installer.launch(controller.installIntent())
        } catch (_: Exception) {
            controller.installError("无法打开系统安装器。请确认安装包仍可用，或通过 GitHub Releases 手动安装。")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (controller.installAllowed()) launchInstaller()
        else controller.installError("尚未允许 Joyfin 安装应用。允许后可再次点击安装更新。")
    }
    fun install() {
        if (controller.installAllowed()) launchInstaller()
        else try {
            permission.launch(controller.permissionIntent())
        } catch (_: Exception) {
            controller.installError("无法打开安装权限设置，请在系统设置中允许 Joyfin 安装未知来源应用。")
        }
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    RememberSelectionFocus("update:primary") {
        Sheet("应用更新", dismiss, preferredWidth = 680.dp, actions = {
            FixedActionArea {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("关闭", dismiss, Modifier.weight(1f).focusTarget("update:close"))
                    val label = when (ui.phase) {
                        UpdatePhase.CHECKING -> "取消检查"
                        UpdatePhase.DOWNLOADING -> "取消下载"
                        UpdatePhase.AVAILABLE -> if (ui.error == null) "下载更新" else "重新下载"
                        UpdatePhase.READY -> "安装更新"
                        else -> "检查更新"
                    }
                    ActionButton(label, {
                        when (ui.phase) {
                            UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> controller.cancel()
                            UpdatePhase.AVAILABLE -> controller.download()
                            UpdatePhase.READY -> install()
                            else -> controller.check()
                        }
                    }, Modifier.weight(1f).focusTarget("update:primary"), primary = true,
                        loading = ui.busy, enabledWhileLoading = true,
                        loadingLabel = if (ui.phase == UpdatePhase.DOWNLOADING) "已下载 ${ui.progress}%，点击取消" else "正在检查，点击取消")
                }
            }
        }) {
            Caption("当前版本 v${BuildConfig.VERSION_NAME}")
            when (ui.phase) {
                UpdatePhase.CURRENT -> SectionTitle("当前已是最新正式版")
                UpdatePhase.IDLE, UpdatePhase.CHECKING -> Caption("从 Joyfin 的 GitHub Releases 获取正式版本。")
                else -> ui.release?.let { release ->
                    SectionTitle("新版本 v${release.version}")
                    Caption("Android 手机／TV 通用 · ${release.sizeLabel}")
                    if (ui.phase == UpdatePhase.DOWNLOADING) Caption("${ui.progress}% · 下载进度")
                    if (ui.phase == UpdatePhase.READY) Caption("下载完成，点击安装更新后由系统确认。")
                    SectionTitle("更新说明")
                    release.notes.ifBlank { "此版本未提供更新说明。" }
                        .lineSequence().filter { it.isNotBlank() }.flatMap { it.chunked(360).asSequence() }
                        .forEachIndexed { index, line ->
                            Caption(line, Modifier.focusTarget("update:notes:$index").focusable())
                        }
                }
            }
            ui.error?.let { Caption(it) }
            if (!ui.busy && ui.release != null)
                ActionButton("重新检查版本", controller::check, Modifier.focusTarget("update:recheck"), quiet = true)
        }
    }
}
