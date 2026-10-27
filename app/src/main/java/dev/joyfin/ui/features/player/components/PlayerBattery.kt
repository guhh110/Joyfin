package dev.joyfin.ui.features.player.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay

internal data class BatteryState(val level: Int, val charging: Boolean)

internal fun readBatteryState(context: Context): BatteryState {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return BatteryState(100, false)
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
    return BatteryState(pct, charging)
}

@Composable
fun PlayerBattery(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readBatteryState(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            state = readBatteryState(context)
        }
    }

    val tint = when {
        state.charging -> Color(0xFF22C55E)
        state.level <= 20 -> Color(0xFFEF4444)
        else -> Palette.Text.copy(alpha = 0.85f)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
            val strokeWidth = 1.2.dp.toPx()
            val capWidth = 1.6.dp.toPx()
            val capHeight = 4.dp.toPx()
            val bodyWidth = size.width - capWidth - 1.dp.toPx()
            val bodyHeight = size.height

            // 电池外壳
            drawRoundRect(
                color = tint.copy(alpha = 0.6f),
                topLeft = Offset(0f, 0f),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = strokeWidth),
            )

            // 正极微凸起 (右侧)
            drawRoundRect(
                color = tint.copy(alpha = 0.6f),
                topLeft = Offset(bodyWidth + 0.8.dp.toPx(), (bodyHeight - capHeight) / 2f),
                size = Size(capWidth, capHeight),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )

            // 内部电量进度填充
            val padding = strokeWidth + 1.dp.toPx()
            val fillMaxWidth = (bodyWidth - padding * 2).coerceAtLeast(0f)
            val fillWidth = fillMaxWidth * (state.level / 100f)
            val fillHeight = (bodyHeight - padding * 2).coerceAtLeast(0f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(padding, padding),
                    size = Size(fillWidth, fillHeight),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }

            // 充电时居中闪电微矢量
            if (state.charging) {
                val lightning = Path().apply {
                    val cx = bodyWidth / 2f
                    val cy = bodyHeight / 2f
                    moveTo(cx + 1.dp.toPx(), cy - 3.5.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy + 0.5.dp.toPx())
                    lineTo(cx, cy + 0.5.dp.toPx())
                    lineTo(cx - 1.dp.toPx(), cy + 3.5.dp.toPx())
                    lineTo(cx + 2.dp.toPx(), cy - 0.5.dp.toPx())
                    lineTo(cx, cy - 0.5.dp.toPx())
                    close()
                }
                drawPath(lightning, color = Color.White)
            }
        }

        Caption(
            "${state.level}%",
            color = tint,
        )
    }
}
