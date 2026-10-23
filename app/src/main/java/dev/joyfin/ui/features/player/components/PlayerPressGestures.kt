package dev.joyfin.ui.features.player.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import dev.joyfin.playback.SpeedBoostActions

/** The long press owns its release token, including cancellation and pointer-input disposal. */
internal suspend fun PointerInputScope.detectPlayerPressGestures(
    boost: SpeedBoostActions?,
    onTap: () -> Unit,
) {
    if (boost == null) {
        detectTapGestures { onTap() }
        return
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val pressed = awaitLongPressOrCancellation(down.id)
        if (pressed == null) {
            currentEvent.changes.firstOrNull { it.id == down.id && it.changedToUp() }?.let {
                it.consume()
                onTap()
            }
        } else if (pressed.id == down.id && currentEvent.changes.count { it.pressed } == 1) {
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
        }
    }
}
