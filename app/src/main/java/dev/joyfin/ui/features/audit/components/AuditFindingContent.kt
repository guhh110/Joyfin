package dev.joyfin.ui.features.audit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.audit.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.MotionTokens
import dev.joyfin.ui.theme.Palette

private val AuditTarget.episodeMarker: String get() = " · S${season ?: "?"}E${episode ?: "?"} · "
internal val AuditTarget.workName: String get() =
    if (type == "Episode") name.substringBefore(episodeMarker).ifBlank { "未命名剧集" } else name
private val AuditTarget.episodeLabel: String get() =
    "S${season ?: "?"}E${episode ?: "?"} · ${name.substringAfter(episodeMarker, name)}"

@Composable
internal fun AuditWorkHeader(name: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, Modifier.weight(1f), color = Palette.Text, fontSize = 16.sp, lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Caption("$count 项")
    }
}

@Composable
internal fun AuditFindingRow(finding: AuditFinding, ignored: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.fillMaxWidth().focusTarget("audit:row:${finding.key}")
        .background(Palette.Surface.copy(alpha = .6f), RoundedCornerShape(10.dp))
        .border(.5.dp, Palette.Line, RoundedCornerShape(10.dp)),
        transparent = true, focusedScale = MotionTokens.FixedScale) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(finding.title, color = Palette.Text, fontSize = 15.sp, lineHeight = 22.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Caption(if (finding.target.type == "Episode") finding.target.episodeLabel else finding.confidence.label,
                    maxLines = 2)
            }
            if (ignored) Caption("已忽略")
            AuditBadge(finding.category.label)
            AppIcon(Glyph.Next, Modifier.size(15.dp), color = Palette.Muted)
        }
    }
}

@Composable
private fun AuditBadge(label: String) {
    Box(Modifier.background(Palette.Text.copy(alpha = .06f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = Palette.Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
internal fun AuditFindingDetails(
    finding: AuditFinding,
    ui: AuditUiState,
    onOpen: () -> Unit,
    onSubtitles: () -> Unit,
    onRecheck: () -> Unit,
    onIgnore: () -> Unit,
) {
    val available = !ui.running && ui.rechecking == null
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditBadge(finding.category.label)
                AuditBadge(finding.confidence.label)
            }
            Text(finding.title, color = Palette.Text, fontSize = 19.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold)
            Column(Modifier.fillMaxWidth().background(Palette.Text.copy(alpha = .035f), RoundedCornerShape(12.dp))
                .border(.5.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(finding.target.workName, color = Palette.Text, fontSize = 14.sp, lineHeight = 21.sp)
                if (finding.target.type == "Episode") Caption(finding.target.episodeLabel)
            }
            finding.evidence.filter { it.isNotBlank() }.forEach { evidence ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(4.dp).background(Palette.Muted, CircleShape))
                    Text(evidence, Modifier.weight(1f), color = Palette.Muted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
            ui.error?.let { Caption(it) }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(.5.dp).background(Palette.Line))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("查看作品", onOpen, Modifier.fillMaxWidth().focusTarget("audit:detail:open"),
                Glyph.Film, primary = true, enabled = available, focusedScale = MotionTokens.FixedScale)
            if (finding.category == AuditCategory.SUBTITLES) ActionButton("搜索与整理字幕", onSubtitles,
                Modifier.fillMaxWidth().focusTarget("audit:detail:subtitles"), Glyph.Subtitle,
                enabled = available, focusedScale = MotionTokens.FixedScale)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("重新检查", onRecheck, Modifier.weight(1f).focusTarget("audit:detail:recheck"), Glyph.Refresh,
                    enabled = !ui.running && (ui.rechecking == null || ui.rechecking == finding.key),
                    loading = ui.rechecking == finding.key, focusedScale = MotionTokens.FixedScale)
                ActionButton(if (finding.fingerprint in ui.ignored) "恢复提示" else "忽略提示", onIgnore,
                    Modifier.weight(1f).focusTarget("audit:detail:ignore"), Glyph.CheckCircle,
                    enabled = available, focusedScale = MotionTokens.FixedScale)
            }
        }
    }
}
