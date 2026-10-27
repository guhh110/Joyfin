package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import dev.joyfin.playback.SeekPreviewActions
import dev.joyfin.playback.PlayerDeviceControl
import dev.joyfin.playback.SpeedBoostActions
import dev.joyfin.ui.device.LocalDeviceLayout
import kotlin.math.abs

internal data class PlayerVerticalGestureActions(
    val begin: (PlayerDeviceControl) -> Unit,
    val dragBy: (Float) -> Unit,
    val end: () -> Unit,
)

/** Behind the controls: only empty video areas receive these gestures. */
@Composable
internal fun BoxScope.VideoGestureSurface(
    visible: Boolean,
    seekEnabled: Boolean,
    actions: SeekPreviewActions,
    onWake: () -> Unit,
    onTap: () -> Unit,
    onConfirm: () -> Unit,
    locked: Boolean = false,
    onLockedTap: () -> Unit = {},
    verticalActions: PlayerVerticalGestureActions? = null,
    speedBoost: SpeedBoostActions? = null,
    gestureKey: Pair<String?, String?> = null to null,
) {
    val focus = remember { FocusRequester() }
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
            .pointerInput(seekEnabled, locked, verticalActions) {
                if (locked || (!seekEnabled && verticalActions == null)) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val vertical = verticalActions
                    var horizontal = false
                    var started = false
                    var distance = 0f
                    // Lock both the axis and the side at gesture start, even across the midpoint.
                    val control = if (down.position.x < size.width / 2f)
                        PlayerDeviceControl.BRIGHTNESS else PlayerDeviceControl.VOLUME
                    fun move(delta: Offset) {
                        if (!started) return
                        if (horizontal) {
                            distance += delta.x
                            currentActions.swipeOffset((distance / size.width.coerceAtLeast(1) * 120_000).toLong())
                        } else {
                            vertical?.dragBy?.invoke(-delta.y / (size.height.coerceAtLeast(1) * .7f))
                        }
                    }
                    try {
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            horizontal = abs(over.x) > abs(over.y)
                            change.consume()
                            if (horizontal && seekEnabled) {
                                started = true
                                currentActions.beginSwipe()
                            } else if (!horizontal && vertical != null) {
                                started = true
                                vertical.begin(control)
                            }
                            move(over)
                        }
                        if (slop != null) {
                            val completed = drag(slop.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                move(delta)
                            }
                            if (started && horizontal) {
                                started = false
                                if (completed) currentActions.commit() else currentActions.cancel()
                            }
                        }
                    } finally {
                        if (started) {
                            if (horizontal) currentActions.cancel() else vertical?.end?.invoke()
                        }
                    }
                }
            }
            .pointerInput(locked, speedBoost, seekEnabled, gestureKey) {
                detectPlayerPressGestures(
                    speedBoost.takeUnless { locked },
                    onDoubleTap = if (!locked && !tv) { offset ->
                        val width = size.width.toFloat()
                        if (seekEnabled && offset.x < width * 0.42f) {
                            currentActions.adjust(-10_000)
                        } else if (seekEnabled && offset.x > width * 0.58f) {
                            currentActions.adjust(10_000)
                        } else {
                            confirm()
                        }
                    } else null,
                ) {
                    if (locked) lockedTap() else tap()
                }
            }
    )
}
