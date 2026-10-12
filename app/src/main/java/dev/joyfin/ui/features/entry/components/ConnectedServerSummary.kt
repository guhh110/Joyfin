package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun ConnectedServerSummary(
    name: String,
    address: String,
    enabled: Boolean,
    onChange: () -> Unit,
    actionModifier: Modifier = Modifier,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryServerIcon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(4.dp).background(EntryChampagne, CircleShape))
                    Text(
                        name.ifBlank { "Jellyfin" },
                        color = Palette.Text,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    address,
                    color = Palette.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.width(68.dp)) {
                EntryButton(
                    "更换",
                    onChange,
                    actionModifier.focusTarget(
                        "auth:change-server",
                        LocalDeviceLayout.current.television || enabled,
                    ),
                    enabled = enabled,
                    quiet = true,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
    }
}
