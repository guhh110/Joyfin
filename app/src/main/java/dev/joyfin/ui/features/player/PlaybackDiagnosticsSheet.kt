package dev.joyfin.ui.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.joyfin.data.PlaybackQuality
import dev.joyfin.playback.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.mobile.MobilePlaybackDiagnosticsLayout
import dev.joyfin.ui.features.player.tv.TvPlaybackDiagnosticsLayout
import dev.joyfin.ui.interaction.*
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
@Suppress("DEPRECATION")
internal fun PlaybackDiagnosticsSheet(onDismiss: () -> Unit) {
    val ui by PlaybackDiagnostics.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val tv = LocalDeviceLayout.current.television
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 80.dp.toPx() }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    RememberScreenFocus("diagnostics:copy") {
        Sheet("播放诊断", onDismiss, side = tv, preferredWidth = 640.dp, scrollContent = false, actions = {
            ActionButton(if (copied) "已复制" else "复制脱敏报告", {
                clipboard.setText(AnnotatedString(ui.report()))
                copied = true
            }, Modifier.fillMaxWidth().focusTarget("diagnostics:copy"), primary = true,
                enabled = ui.id != 0L)
        }) {
            val window = LocalWindowInfo.current
            val focus = LocalScreenFocus.current
            LaunchedEffect(Unit) {
                if (tv) {
                    snapshotFlow { window.isWindowFocused }.first { it }
                    withFrameNanos {}
                    focus?.rememberTarget?.invoke("diagnostics:copy")
                    focus?.requesters?.get("diagnostics:copy")?.requestFocus()
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).onPreviewKeyEvent { event ->
                if (!tv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) { Key.DirectionDown -> scrollStep; Key.DirectionUp -> -scrollStep; else -> 0f }
                if (delta > 0 && scroll.canScrollForward || delta < 0 && scroll.canScrollBackward) {
                    scope.launch { scroll.scrollBy(delta) }
                    true
                } else false
            }.focusTarget("diagnostics:content").focusable(enabled = tv),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (ui.id == 0L) {
                    Caption("暂无播放诊断")
                    Caption("尝试播放后，这里会保留最近一次播放的格式、协商结果与失败阶段。记录仅保存在当前应用进程内。", maxLines = 4)
                } else {
                    DiagnosticOverview(ui)
                    DiagnosticTiming(ui.timing)
                    if (tv) TvPlaybackDiagnosticsLayout({ DiagnosticSource(ui) }, { DiagnosticOutput(ui) })
                    else MobilePlaybackDiagnosticsLayout({ DiagnosticSource(ui) }, { DiagnosticOutput(ui) })
                    DiagnosticEvents(ui)
                    Caption("报告仅包含播放技术信息；不包含服务器地址、凭据、账号、媒体名称、文件路径或设备序列号。", maxLines = 4)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticTiming(timing: PlaybackTimingSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("启动与缓冲")
        timing.reportLines().forEach { Caption(it, color = Palette.Text) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticOverview(ui: PlaybackDiagnosticSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MediaPill(ui.state.label, outlined = true)
            MediaPill(ui.method, outlined = true)
        }
        DiagnosticValue("当前阶段", ui.stage.label)
        DiagnosticValue("播放策略", ui.selection)
        DiagnosticValue("画质设置", if (ui.quality == PlaybackQuality.ORIGINAL) "原画 · 不限码率"
            else diagnosticBitrate(ui.quality.toLong()))
        DiagnosticValue("兼容与协商说明", ui.reasons.takeIf { it.isNotEmpty() }?.joinToString("、")
            ?: if (ui.method == "直接播放") "无需转码" else "服务端未提供具体原因")
        DiagnosticValue("显示设备能力", ui.displayCapabilities)
        ui.decision?.let { decision ->
            DiagnosticValue("视频播放请求", "${decision.sourceRange.label} → ${decision.targetRange.label} · ${decision.videoPath.label}")
            DiagnosticValue("音频播放请求", "${decision.outputMode.label} · ${decision.audioPath.label}")
            decision.audioSupport?.let { support ->
                DiagnosticValue("音频能力探测 · ${support.sampleRate} Hz",
                    "编码输出 ${support.passthroughChannels} 声道 / 系统解码 ${support.platformChannels} 声道 / FFmpeg ${support.softwareChannels} 声道")
            }
        }
        DiagnosticValue("播放位置 / 已缓冲", "${diagnosticTime(ui.positionMs)} / ${ui.bufferMs / 1000} 秒")
        DiagnosticValue("网络带宽估算", diagnosticBitrate(ui.bandwidth))
        ui.failure?.let { DiagnosticValue("当前错误", it.description, warning = true) }
    }
}

@Composable
private fun DiagnosticSource(ui: PlaybackDiagnosticSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("源文件格式")
        DiagnosticValue("容器", ui.container)
        DiagnosticValue("源总码率", diagnosticBitrate(ui.sourceBitrate))
        DiagnosticValue("源视频", ui.sourceVideo?.description ?: "未提供")
        DiagnosticValue("源音频", ui.sourceAudio?.description ?: "未提供")
        DiagnosticValue("片源对象音效", ui.sourceSpatial)
    }
}

@Composable
private fun DiagnosticOutput(ui: PlaybackDiagnosticSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("渲染器输入")
        DiagnosticValue("视频", ui.outputVideo?.description ?: "播放器尚未提供")
        DiagnosticValue("音频", ui.outputAudio?.description ?: "播放器尚未提供")
        DiagnosticValue("视频解码器", ui.videoDecoder)
        DiagnosticValue("音频解码器", ui.audioDecoder)
        SectionTitle("AudioTrack 实际配置")
        DiagnosticValue("输出音频", ui.actualOutput?.description ?: "尚未取得")
        DiagnosticValue("输出路由", ui.actualOutput?.route ?: "未知")
        DiagnosticValue("输出方式", when (ui.actualOutput?.pcm) {
            true -> "PCM 输出"
            false -> if (ui.actualOutput?.offload == true) "编码输出 · Offload" else "编码输出"
            null -> "未知"
        })
        Caption("AudioTrack 配置不代表功放已启用对象音效；电视实际 HDR／DV 模式和接收端音效仍需设备确认。网络带宽为估算值。", maxLines = 6)
    }
}

@Composable
private fun DiagnosticEvents(ui: PlaybackDiagnosticSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("最近阶段记录")
        ui.events.forEach { event ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                Caption("+${diagnosticElapsed(event.elapsedMs)}", Modifier.width(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Caption("${event.stage.label} · ${event.outcome.label}", color = Palette.Text)
                    event.failure?.let { Caption(it.description, color = Palette.Error, maxLines = 4) }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String, warning: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Caption(label)
        Caption(value, color = if (warning) Palette.Error else Palette.Text, maxLines = 8)
    }
}
