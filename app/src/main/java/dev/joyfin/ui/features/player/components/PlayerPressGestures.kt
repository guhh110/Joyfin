package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import dev.joyfin.playback.SpeedBoostActions
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** The long press owns its release token, including cancellation and pointer-input disposal. */
internal suspend fun PointerInputScope.detectPlayerPressGestures(
    boost: SpeedBoostActions?,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onTap: () -> Unit,
) {
    if (boost == null && onDoubleTap == null) {
        detectTapGestures { onTap() }
        return
    }
    var lastDoubleTapTime = 0L
    var lastDoubleTapX = 0f
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val pressed = awaitLongPressOrCancellation(down.id)
        if (pressed != null && pressed.id == down.id && currentEvent.changes.count { it.pressed } == 1 && boost != null) {
            lastDoubleTapTime = 0L
            val token = boost.begin()
            try {
                // Consume subsequent motion so a held boost cannot turn into a seek/level swipe.
                currentEvent.changes.forEach { it.consume() }
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    val canceled = event.changes.any { it.isConsumed } ||
                        pointer == null || pointer.isOutOfBounds(size, extendedTouchPadding)
                    event.changes.forEach { it.consume() }
                    if (canceled || !pointer.pressed || event.changes.count { it.pressed } > 1) break
                }
            } finally {
                if (token != null) boost.end(token)
            }
            return@awaitEachGesture
        }

        val up = currentEvent.changes.firstOrNull { it.id == down.id && it.changedToUp() }
        if (up == null) return@awaitEachGesture
        up.consume()

        if (onDoubleTap == null) {
            onTap()
            return@awaitEachGesture
        }

        val now = System.currentTimeMillis()
        if (now - lastDoubleTapTime < 500L && abs(up.position.x - lastDoubleTapX) < size.width * 0.35f) {
            lastDoubleTapTime = now
            lastDoubleTapX = up.position.x
            onDoubleTap(up.position)
            return@awaitEachGesture
        }

        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            val second = awaitFirstDown(requireUnconsumed = false)
            if ((second.position - up.position).getDistance() < viewConfiguration.touchSlop * 3f) {
                second
            } else {
                null
            }
        }

        if (secondDown == null) {
            lastDoubleTapTime = 0L
            onTap()
        } else {
            secondDown.consume()
            val secondPressed = awaitLongPressOrCancellation(secondDown.id)
            if (secondPressed != null && boost != null) {
                lastDoubleTapTime = 0L
                val token = boost.begin()
                try {
                    currentEvent.changes.forEach { it.consume() }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == secondDown.id }
                        val canceled = event.changes.any { it.isConsumed } ||
                            pointer == null || pointer.isOutOfBounds(size, extendedTouchPadding)
                        event.changes.forEach { it.consume() }
                        if (canceled || !pointer.pressed || event.changes.count { it.pressed } > 1) break
                    }
                } finally {
                    if (token != null) boost.end(token)
                }
            } else {
                val secondUp = currentEvent.changes.firstOrNull { it.id == secondDown.id && it.changedToUp() }
                if (secondUp != null) {
                    secondUp.consume()
                    lastDoubleTapTime = System.currentTimeMillis()
                    lastDoubleTapX = secondDown.position.x
                    onDoubleTap(secondDown.position)
                }
            }
        }
    }
}
