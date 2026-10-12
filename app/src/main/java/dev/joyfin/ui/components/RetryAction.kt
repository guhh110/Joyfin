package dev.joyfin.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun RetryAction(
    text: String,
    error: String?,
    loading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hadError by remember { mutableStateOf(false) }
    SideEffect { if (error != null) hadError = true else if (!loading) hadError = false }
    if (error != null || loading && hadError)
        ActionButton(text, onRetry, modifier, loading = loading, maxLines = 1)
}
