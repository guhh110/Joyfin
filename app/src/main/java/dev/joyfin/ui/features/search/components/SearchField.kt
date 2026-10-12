package dev.joyfin.ui.features.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joyfin.ui.components.Caption
import dev.joyfin.ui.theme.Palette

@Composable
internal fun SearchField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(label)
        BasicTextField(
            value,
            onChange,
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics { contentDescription = label }
                .onFocusChanged { focused = it.isFocused }
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Palette.Text else Palette.Line,
                    RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(Palette.Gold),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
    }
}
