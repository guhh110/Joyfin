package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.theme.Palette

@Composable
fun RequestStatus(
    busy: Boolean,
    error: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (busy || error == null) return
    Column(
        modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .background(Palette.Elevated, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Caption(error, color = Palette.Error)
        if (onRetry != null) ActionButton("重试", onRetry, icon = Glyph.Refresh)
    }
}

@Composable
fun EmptyContent(
    title: String,
    detail: String = "",
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(title)
        if (detail.isNotBlank()) Caption(detail)
        if (action != null && onAction != null) ActionButton(action, onAction)
    }
}

@Composable
fun ErrorContent(
    title: String,
    error: String,
    action: String = "重新加载",
    onAction: (() -> Unit)? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    icon: Glyph = Glyph.Info,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(Palette.Elevated, RoundedCornerShape(12.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(22.dp), color = Palette.Text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(title)
            Caption(browseErrorText(error), color = Palette.Muted)
        }
        if (onAction != null) {
            ActionButton(
                action,
                onAction,
                icon = Glyph.Refresh,
                primary = true,
                loading = loading,
            )
        }
    }
}
