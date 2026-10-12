package dev.joyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/** Only failures are visible here; retries remain mounted until their request finishes. */
@Composable
fun RegionLoadFeedback(
    loading: Boolean,
    error: String?,
    onRetry: (() -> Unit)?,
    hasContent: Boolean,
    modifier: Modifier = Modifier,
    requestKey: Any? = Unit,
    stableRetrySlot: Boolean = false,
) {
    var retrying by remember(requestKey) { mutableStateOf(false) }
    LaunchedEffect(loading, error) { if (!loading) retrying = false }
    val showRetry = onRetry != null && (stableRetrySlot || error != null || retrying && loading)
    Row(
        modifier.fillMaxWidth().heightIn(min = if (showRetry) 48.dp else 24.dp).semantics {
            if (loading && !hasContent) stateDescription = "正在加载"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Caption(if (!loading) error ?: " " else " ", Modifier.weight(1f), color = Palette.Error)
        if (showRetry)
            ActionButton(
                "重试",
                {
                    retrying = true
                    onRetry()
                },
                if (stableRetrySlot) Modifier.focusTarget("detail:retry") else Modifier,
                enabled = error != null || loading && retrying,
                loading = loading && retrying,
                quiet = true,
                maxLines = 1,
            )
    }
}
