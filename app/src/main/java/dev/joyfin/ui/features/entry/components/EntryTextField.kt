package dev.joyfin.ui.features.entry.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.ui.components.stableAction
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.theme.Palette

/** Remote focus is quiet; confirming a field explicitly opens the system IME. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryTextField(
    label: String,
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    uri: Boolean = false,
    error: String? = null,
    showErrorText: Boolean = true,
    next: Boolean = false,
    keyboardRequest: Int = 0,
    visibilityModifier: Modifier = Modifier,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var keyboardActivated by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) revealed = false }
    val tv = LocalDeviceLayout.current.television
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(keyboardRequest) {
        if (keyboardRequest > 0 && enabled) {
            keyboardActivated = true
            withFrameNanos {}
            keyboard?.show()
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            keyboardActivated = false
            keyboard?.hide()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val style = TextStyle(color = Palette.Text, fontSize = if (tv) 15.sp else 14.sp)
    val options =
        KeyboardOptions(
            keyboardType =
                if (secret) if (revealed) KeyboardType.PasswordVisible else KeyboardType.Password
                else if (uri) KeyboardType.Uri else KeyboardType.Text,
            imeAction = if (next) ImeAction.Next else ImeAction.Done,
            autoCorrectEnabled = false,
            showKeyboardOnFocus = enabled && (!tv || keyboardActivated),
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryText(label)
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(shape)
                .background(Palette.Surface.copy(alpha = .95f))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Palette.Text
                    else if (error != null) Palette.Error.copy(alpha = .6f)
                    else Palette.Text.copy(alpha = .15f),
                    shape,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fieldModifier =
                modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) keyboardActivated = false
                    }
                    .onPreviewKeyEvent { event ->
                        if (
                            tv &&
                                !imeVisible &&
                                event.type == KeyEventType.KeyDown &&
                                event.key in
                                    listOf(
                                        Key.DirectionUp,
                                        Key.DirectionDown,
                                        Key.DirectionLeft,
                                        Key.DirectionRight,
                                    )
                        ) {
                            focusManager.moveFocus(
                                when (event.key) {
                                    Key.DirectionUp -> FocusDirection.Up
                                    Key.DirectionDown -> FocusDirection.Down
                                    Key.DirectionLeft -> FocusDirection.Left
                                    else -> FocusDirection.Right
                                }
                            )
                            true
                        } else if (
                            tv &&
                                (event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter)
                        ) {
                            if (enabled && event.type == KeyEventType.KeyUp) {
                                keyboardActivated = true
                                keyboard?.show()
                            }
                            true
                        } else false
                    }
                    .semantics {
                        contentDescription = label
                        if (!enabled) disabled()
                        if (error != null) semanticsError(error)
                    }
                    .padding(horizontal = 15.dp, vertical = 16.dp)
            val decorator: androidx.compose.foundation.text.input.TextFieldDecorator =
                androidx.compose.foundation.text.input.TextFieldDecorator { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty())
                            Text(
                                placeholder,
                                color = Palette.Muted.copy(alpha = .6f),
                                fontSize = style.fontSize,
                            )
                        inner()
                    }
                }
            if (secret)
                BasicSecureTextField(
                    state,
                    fieldModifier,
                    enabled = enabled || tv,
                    inputTransformation =
                        androidx.compose.foundation.text.input.InputTransformation {
                            if (!enabled) revertAllChanges()
                        },
                    textStyle = style,
                    keyboardOptions = options,
                    onKeyboardAction = { onSubmit() },
                    cursorBrush = SolidColor(Palette.Text),
                    textObfuscationMode =
                        if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                    decorator = decorator,
                )
            else
                BasicTextField(
                    state,
                    fieldModifier,
                    enabled = enabled || tv,
                    inputTransformation =
                        androidx.compose.foundation.text.input.InputTransformation {
                            if (!enabled) revertAllChanges()
                        },
                    textStyle = style,
                    keyboardOptions = options,
                    onKeyboardAction = { onSubmit() },
                    cursorBrush = SolidColor(Palette.Text),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    decorator = decorator,
                )
            var eyeFocused by remember { mutableStateOf(false) }
            if (secret)
                Box(
                    visibilityModifier
                        .size(48.dp)
                        .onFocusChanged { eyeFocused = it.isFocused }
                        .border(
                            1.dp,
                            if (eyeFocused) Palette.Text
                            else androidx.compose.ui.graphics.Color.Transparent,
                            shape,
                        )
                        .focusTarget("auth:password-visibility", enabled || tv)
                        .semantics {
                            contentDescription = if (revealed) "隐藏密码" else "显示密码"
                            stateDescription = if (revealed) "密码可见" else "密码隐藏"
                        }
                        .stableAction(enabled) { revealed = !revealed },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val u = size.width / 24f
                        val path =
                            Path().apply {
                                moveTo(2 * u, 12 * u)
                                cubicTo(7 * u, 4 * u, 17 * u, 4 * u, 22 * u, 12 * u)
                                cubicTo(17 * u, 20 * u, 7 * u, 20 * u, 2 * u, 12 * u)
                            }
                        drawPath(
                            path,
                            if (eyeFocused) Palette.Text else Palette.Muted,
                            style = Stroke(1.2.dp.toPx()),
                        )
                        drawCircle(Palette.Muted, 3 * u, style = Stroke(1.2.dp.toPx()))
                        if (revealed)
                            drawLine(
                                Palette.Text,
                                androidx.compose.ui.geometry.Offset(3 * u, 3 * u),
                                androidx.compose.ui.geometry.Offset(21 * u, 21 * u),
                                1.2.dp.toPx(),
                            )
                    }
                }
        }
        if (showErrorText) error?.let { EntryError(it) }
    }
}
