package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
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
    reserveSpace: Boolean = true,
) {
    var retrying by remember(requestKey) { mutableStateOf(false) }
    LaunchedEffect(loading, error) { if (!loading) retrying = false }
    val showRetry = onRetry != null && (stableRetrySlot || error != null || retrying && loading)
    if (!reserveSpace && error == null && !showRetry) return

    if (error != null || retrying && loading) {
        Row(
            modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics {
                    if (loading && !hasContent) stateDescription = "正在加载"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(Palette.Elevated, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(Glyph.Info, Modifier.size(16.dp), color = Palette.Muted)
            }
            Text(
                text = if (!loading) error?.let(::browseErrorText) ?: " " else "正在重新加载…",
                color = Palette.Text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showRetry) {
                ActionButton(
                    "重试",
                    {
                        retrying = true
                        onRetry()
                    },
                    if (stableRetrySlot) Modifier.focusTarget("detail:retry") else Modifier,
                    enabled = error != null || loading && retrying,
                    loading = loading && retrying,
                    icon = Glyph.Refresh,
                    quiet = false,
                    maxLines = 1,
                )
            }
        }
    } else if (reserveSpace) {
        Spacer(modifier.fillMaxWidth().height(if (showRetry) 48.dp else 24.dp))
    }
}

/** Keep transport details out of browse rows, while retaining server-provided explanations. */
internal fun browseErrorText(message: String): String = when {
    message.contains("503", ignoreCase = true) -> "服务器暂时不可用（HTTP 503），请稍后重试"
    message.contains("502", ignoreCase = true) || message.contains("504", ignoreCase = true) -> "网关响应超时或异常，请检查网络后重试"
    message.contains("500", ignoreCase = true) -> "服务器处理请求时遇到错误（HTTP 500），请稍后重试"
    message.contains("401", ignoreCase = true) || message.contains("403", ignoreCase = true) -> "访问受限或登录已失效，请重新登录"
    message.contains("404", ignoreCase = true) || message.contains("not found:", ignoreCase = true) -> "请求的内容或接口未找到，请检查地址后重试"
    message.contains("JSON", ignoreCase = true) &&
        (message.contains("token", ignoreCase = true) || message.contains("parse", ignoreCase = true) ||
            message.contains("input", ignoreCase = true)) -> "服务器返回的数据格式异常，请稍后重试"
    message.contains("timeout", ignoreCase = true) ||
        message.contains("timed out", ignoreCase = true) ||
        message.contains("connect", ignoreCase = true) && message.contains("after ") &&
        message.contains("ms") -> "连接服务器超时，请检查网络后重试"
    message.contains("failed to connect", ignoreCase = true) ||
        message.contains("connection refused", ignoreCase = true) -> "暂时无法连接服务器，请检查网络后重试"
    message.contains("unable to resolve host", ignoreCase = true) -> "无法解析服务器地址，请检查网络设置后重试"
    message.contains("network is unreachable", ignoreCase = true) -> "网络不可达，请检查设备网络连接"
    else -> message
}
