@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wait for a playable item and a supported local text track before committing a choice. */
internal suspend fun ExoPlayer.prepareLocalSubtitle(item: MediaItem, position: Long, speed: Float) {
    val ready = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : Player.Listener {
                fun ready() {
                    if (!continuation.isActive || playbackState != Player.STATE_READY) return
                    val local = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
                        (0 until group.length).filter { LocalSubtitlePlayback.matches(group.getTrackFormat(it).id) }
                            .map { group to it }
                    }
                    if (local.isEmpty()) return
                    val supported = local.any { (group, index) -> group.isTrackSupported(index) }
                    if (supported && local.none { (group, index) -> group.isTrackSelected(index) }) return
                    removeListener(this)
                    if (supported) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException("本地字幕轨道不可用"))
                }
                override fun onPlaybackStateChanged(playbackState: Int) = ready()
                override fun onTracksChanged(tracks: Tracks) = ready()
                override fun onPlayerError(error: PlaybackException) {
                    removeListener(this)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            addListener(listener)
            continuation.invokeOnCancellation { removeListener(listener) }
            try {
                setMediaItem(item, position)
                prepare()
                setPlaybackSpeed(speed)
                playWhenReady = false
            } catch (e: Exception) {
                removeListener(listener)
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        true
    }
    check(ready == true) { "应用字幕超时，请重试" }
}
