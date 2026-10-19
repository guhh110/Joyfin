package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import dev.joyfin.playback.SeekPreviewActions
import dev.joyfin.ui.device.LocalDeviceLayout

/** Behind the controls: only empty video areas receive these gestures. */
@Composable
fun BoxScope.VideoGestureSurface(
    visible: Boolean,
    seekEnabled: Boolean,
    actions: SeekPreviewActions,
    onWake: () -> Unit,
    onTap: () -> Unit,
    onConfirm: () -> Unit,
    locked: Boolean = false,
    onLockedTap: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    var width by remember { mutableIntStateOf(1) }
    val currentActions by rememberUpdatedState(actions)
    val wake by rememberUpdatedState(onWake)
    val tap by rememberUpdatedState(onTap)
    val confirm by rememberUpdatedState(onConfirm)
    val lockedTap by rememberUpdatedState(onLockedTap)
    val tv = LocalDeviceLayout.current.television
    LaunchedEffect(visible, locked) { if (!visible && !locked && tv) focus.requestFocus() }
    Box(
        Modifier.matchParentSize()
            .testTag("player:surface")
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (locked) return@onPreviewKeyEvent false
                if (
                    !visible &&
                        it.type == KeyEventType.KeyDown &&
                        it.key in
                            listOf(
                                Key.DirectionLeft,
                                Key.DirectionRight,
                                Key.DirectionUp,
                                Key.DirectionDown,
                                Key.DirectionCenter,
                                Key.Enter,
                            )
                ) {
                    if (it.key == Key.DirectionLeft || it.key == Key.DirectionRight) {
                        if (seekEnabled)
                            currentActions.adjust(
                                if (it.key == Key.DirectionLeft) -10_000 else 10_000
                            )
                    } else if (it.key == Key.DirectionCenter || it.key == Key.Enter) {
                        if (it.nativeKeyEvent.repeatCount == 0) confirm()
                    } else wake()
                    true
                } else false
            }
            .focusable(enabled = !visible && !locked)
            .pointerInput(seekEnabled, locked) {
                if (seekEnabled && !locked) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            distance = 0f
                            currentActions.beginSwipe()
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            distance += delta
                            currentActions.swipeOffset((distance / width * 120_000).toLong())
                        },
                        onDragEnd = { currentActions.commit() },
                        onDragCancel = { currentActions.cancel() },
                    )
                }
            }
            .pointerInput(locked) {
                detectTapGestures {
                    if (locked) lockedTap() else tap()
                }
            }
    )
}
