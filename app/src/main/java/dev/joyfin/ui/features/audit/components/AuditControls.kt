package dev.joyfin.ui.features.audit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.audit.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AuditControls(
    ui: AuditUiState,
    counts: Map<AuditCategory, Int>,
    onBack: () -> Unit,
    onScope: () -> Unit,
    onScan: () -> Unit,
    onFilter: (AuditCategory?) -> Unit,
    onIgnored: () -> Unit,
    onWarnings: () -> Unit,
) {
    val tv = LocalDeviceLayout.current.television
    val available = !ui.loading && !ui.running && ui.rechecking == null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton("返回", Glyph.Back, onBack, Modifier.focusTarget("audit:back"))
        SectionTitle("片库体检")
    }
    FocusSurface(onScope, Modifier.fillMaxWidth().heightIn(min = 42.dp).focusTarget("audit:scope")
        .background(Palette.Surface, RoundedCornerShape(10.dp)),
        enabled = available, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(Glyph.Library, Modifier.size(17.dp), color = Palette.Muted)
            Text(ui.report.scope.title, Modifier.weight(1f), color = Palette.Text,
                fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AppIcon(Glyph.Down, Modifier.size(15.dp), color = Palette.Muted)
        }
    }
    ActionButton(if (ui.running) "取消扫描" else if (ui.report.status == AuditStatus.READY) "开始扫描" else "重新扫描",
        onScan, Modifier.fillMaxWidth().focusTarget("audit:start"), Glyph.Refresh, primary = true,
        enabled = !ui.loading && ui.rechecking == null, loading = ui.running, enabledWhileLoading = true,
        loadingLabel = "取消扫描", focusedScale = MotionTokens.FixedScale)
    val progress = ui.progress ?: ui.report
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Caption(if (ui.running) "${ui.phase} · ${progress.checked} 项"
            else "${ui.report.status.label} · 已检查 ${ui.report.checked} 项", maxLines = 2)
        if (ui.running) Caption("${progress.processedWorks} / ${progress.totalWorks ?: "—"} 部作品")
        else if (ui.report.finishedAt > 0) Caption(DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(Instant.ofEpochMilli(ui.report.finishedAt).atZone(ZoneId.systemDefault())))
    }
    if (ui.report.warnings.isNotEmpty()) ActionButton("${ui.report.warnings.size} 项未完成", onWarnings,
        Modifier.fillMaxWidth().focusTarget("audit:warnings"), Glyph.Info, quiet = true,
        focusedScale = MotionTokens.FixedScale)
    if (ui.report.futureEpisodes > 0) Caption("未播出 ${ui.report.futureEpisodes} 集")
    ui.cacheError?.let { Caption(it) }
    if (tv) {
        Column(Modifier.fillMaxWidth().background(Palette.Surface.copy(alpha = .6f), RoundedCornerShape(12.dp))
            .border(.5.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(4.dp)) {
            AuditFilter("全部", counts.values.sum(), ui.category == null, Modifier.focusTarget("audit:filter:all")) { onFilter(null) }
            AuditCategory.entries.forEach { category ->
                AuditFilter(category.label, counts[category] ?: 0, ui.category == category,
                    Modifier.focusTarget("audit:filter:${category.name}")) { onFilter(category) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextTab("全部 ${counts.values.sum()}", { onFilter(null) }, ui.category == null,
                Modifier.focusTarget("audit:filter:all"), compact = true)
            AuditCategory.entries.forEach { category ->
                TextTab("${category.label} ${counts[category] ?: 0}", { onFilter(category) }, ui.category == category,
                    Modifier.focusTarget("audit:filter:${category.name}"), compact = true)
            }
        }
    }
    FocusSurface(onIgnored, Modifier.fillMaxWidth().heightIn(min = if (tv) 36.dp else 44.dp)
        .focusTarget("audit:ignored").semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(ui.showIgnored)
        }, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Caption("显示已忽略", Modifier.weight(1f), color = Palette.Text)
            Box(Modifier.size(18.dp).border(1.dp, Palette.Muted, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center) {
                if (ui.showIgnored) AppIcon(Glyph.Check, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun AuditFilter(title: String, count: Int, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick, modifier.fillMaxWidth().heightIn(min = 32.dp).semantics { selected = isSelected },
        selected = isSelected, transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Palette.Text, fontSize = 13.sp, lineHeight = 18.sp)
            Text(count.toString(), color = Palette.Muted, fontSize = 12.sp)
        }
    }
}
