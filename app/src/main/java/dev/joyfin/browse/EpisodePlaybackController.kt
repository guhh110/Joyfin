package dev.joyfin.browse

import dev.joyfin.data.ApiException
import dev.joyfin.data.Media
import kotlinx.coroutines.*

data class EpisodePlaybackIntent(
    val sequence: Long,
    val target: Media,
    val fromStart: Boolean,
    val sourceId: String?,
    val trigger: String,
) {
    val episodeId: String
        get() = target.id

    val label: String
        get() = target.label
}

sealed interface EpisodePlaybackState {
    data object Idle : EpisodePlaybackState

    data class Preparing(val intent: EpisodePlaybackIntent) : EpisodePlaybackState

    data class Failed(val intent: EpisodePlaybackIntent, val message: String) : EpisodePlaybackState

    data class Ready(val intent: EpisodePlaybackIntent, val media: Media) : EpisodePlaybackState
}

/** Metadata preflight only. Actual source negotiation and session ownership stay in the player. */
class EpisodePlaybackController(
    private val scope: CoroutineScope,
    private val item: suspend (String) -> Media,
    private val publish: (EpisodePlaybackState) -> Unit,
    private val expired: () -> Unit,
) {
    private var job: Job? = null
    private var generation = 0L
    private var state: EpisodePlaybackState = EpisodePlaybackState.Idle

    private fun update(next: EpisodePlaybackState) {
        state = next
        publish(next)
    }

    fun cancel() {
        generation++
        job?.cancel()
        update(EpisodePlaybackState.Idle)
    }

    fun play(media: Media, fromStart: Boolean, sourceId: String?, trigger: String) {
        if (state is EpisodePlaybackState.Preparing || state is EpisodePlaybackState.Ready) return
        begin(EpisodePlaybackIntent(++generation, media, fromStart, sourceId, trigger))
    }

    fun retry() {
        val failed = state as? EpisodePlaybackState.Failed ?: return
        begin(failed.intent.copy(sequence = ++generation))
    }

    fun consumed(sequence: Long) {
        if ((state as? EpisodePlaybackState.Ready)?.intent?.sequence == sequence)
            update(EpisodePlaybackState.Idle)
    }

    private fun begin(intent: EpisodePlaybackIntent) {
        job?.cancel()
        update(EpisodePlaybackState.Preparing(intent))
        job =
            scope.launch {
                try {
                    val media = item(intent.episodeId)
                    ensureActive()
                    if (generation != intent.sequence) return@launch
                    require(media.id == intent.episodeId && media.type == "Episode") { "本集已不可播放" }
                    require(
                        intent.sourceId == null ||
                            media.sources.any { it.optString("Id") == intent.sourceId }
                    ) {
                        "所选版本已不可用，请重新选择"
                    }
                    update(EpisodePlaybackState.Ready(intent, media))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    if (generation != intent.sequence) return@launch
                    if (e is ApiException && e.status == 401) expired()
                    else update(EpisodePlaybackState.Failed(intent, e.message ?: "准备播放失败"))
                }
            }
    }
}
