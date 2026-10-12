package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SheetPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier,
    sheetWidth: androidx.compose.ui.unit.Dp,
    heightModifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    actions: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .widthIn(max = sheetWidth)
            .fillMaxWidth()
            .then(heightModifier)
            .background(Palette.Surface, shape)
            .padding(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionTitle(title, Modifier.weight(1f))
            IconButton("关闭", Glyph.Close, onDismiss, Modifier.focusTarget("sheet:close"))
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        actions?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}
