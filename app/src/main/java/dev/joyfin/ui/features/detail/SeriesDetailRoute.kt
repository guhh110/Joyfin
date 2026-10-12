package dev.joyfin.ui.features.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.joyfin.browse.*
import dev.joyfin.browse.DetailUiState
import dev.joyfin.data.Media
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.components.*
import dev.joyfin.ui.features.detail.mobile.*
import dev.joyfin.ui.features.detail.tv.*
import dev.joyfin.ui.interaction.SeriesFocusCoordinator
import dev.joyfin.ui.interaction.seriesPersonFocusKey
import dev.joyfin.ui.model.*
import kotlinx.coroutines.flow.first

/** One series page. Episode selection opens a bottom sheet without replacing the page. */
@Composable
fun SeriesDetailRoute(ui: DetailUiState, actions: BrowseActions, images: ArtworkSource) {
    val tv = LocalDeviceLayout.current.television
    var focused by rememberSaveable(ui.visit) { mutableStateOf("") }
    val focus = remember(ui.visit) { SeriesFocusCoordinator({ focused }) { focused = it } }
    var modal by rememberSaveable(ui.visit) { mutableStateOf<String?>(null) }
    var modalReturn by rememberSaveable(ui.visit) { mutableStateOf("header:back") }
    var modalEpisodeId by rememberSaveable(ui.visit) { mutableStateOf<String?>(null) }
    val overviewScroll = key(ui.visit) { rememberScrollState() }
    val state = ui.series
    val seasonId = state?.seasonId
    val entries = state?.episodes?.data.orEmpty()
    val ids = entries.map { it.id }
    val previewList = key(ui.visit, seasonId) { rememberLazyListState() }
    val peopleList = key(ui.visit) { rememberLazyListState() }
    val relatedList = key(ui.visit) { rememberLazyListState() }
    val keys = buildList {
        addAll(listOf("header:back", "detail:play", "favorite", "series:season"))
        addAll(entries.map { "series:preview:${it.id}" })
        addAll(ui.item?.people.orEmpty().map(::seriesPersonFocusKey))
        addAll(ui.related.map { "series:related:${it.id}" })
        if (state?.seasons?.error != null || state?.seasons?.loading == true)
            add("series:seasons:retry")
        if (state?.episodes?.error != null || state?.episodes?.loading == true)
            add("series:preview:retry")
        if (ui.error != null || ui.busy) add("detail:retry")
        if (state?.recommendation?.error != null || state?.recommendation?.loading == true)
            add("recommendation:retry")
        if (ui.relatedLoad.error != null || ui.relatedLoad.loading) add("series:related:retry")
    }
    LaunchedEffect(ids, modalEpisodeId, modal) {
        if (
            modal in listOf("episode", "media") &&
                state?.episodes?.data != null &&
                modalEpisodeId !in ids
        ) {
            actions.cancelEpisodePlayback()
            modal = null
            focus.move("series:season")
        }
    }
    val entryFocus = remember(ui.visit) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(ui.visit) {
        withFrameNanos {}
        if (tv && focused.isBlank()) entryFocus.requestFocus()
        else if (!tv) focusManager.clearFocus(force = true)
    }
    LaunchedEffect(focus.pending, seasonId, ids, modal, keys) {
        if (!tv || modal != null) return@LaunchedEffect
        val requested = focus.pending ?: return@LaunchedEffect
        val target = requested.takeIf { it in keys } ?: "series:season"
        val previewIndex = entries.indexOfFirst { "series:preview:${it.id}" == target }
        if (
            previewIndex >= 0 &&
                previewList.layoutInfo.visibleItemsInfo.none { it.index == previewIndex }
        )
            previewList.scrollToItem(previewIndex)
        val personIndex =
            ui.item?.people.orEmpty().indexOfFirst { seriesPersonFocusKey(it) == target }
        if (
            personIndex >= 0 &&
                peopleList.layoutInfo.visibleItemsInfo.none { it.index == personIndex }
        )
            peopleList.scrollToItem(personIndex)
        val relatedIndex = ui.related.indexOfFirst { "series:related:${it.id}" == target }
        if (
            relatedIndex >= 0 &&
                relatedList.layoutInfo.visibleItemsInfo.none { it.index == relatedIndex }
        )
            relatedList.scrollToItem(relatedIndex)
        val requester = snapshotFlow { focus.attached[target] }.first { it != null }
        withFrameNanos {}
        if (focus.pending == requested && requester?.requestFocus() == true) focus.focused(target)
    }
    var previousKeys by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(keys, modal) {
        if (modal == null && focused in previousKeys && focused !in keys)
            focus.move("series:season")
        previousKeys = keys
    }
    val latestPlayback by rememberUpdatedState(state?.playback)
    val latestModal by rememberUpdatedState(modal)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, focus) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_STOP && latestPlayback is EpisodePlaybackState.Preparing
            )
                actions.cancelEpisodePlayback()
            if (event == Lifecycle.Event.ON_PAUSE) paused = true
            if (event == Lifecycle.Event.ON_RESUME && paused) {
                paused = false
                if (latestModal == null && focus.target.isNotBlank()) focus.move(focus.target)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun dismiss() {
        val playback = state?.playback
        if (
            playback is EpisodePlaybackState.Preparing &&
                playback.intent.trigger.startsWith("sheet:")
        )
            actions.cancelEpisodePlayback()
        modal = null
        focus.move(modalReturn)
    }
    fun show(type: String, trigger: String) {
        if (state?.playback is EpisodePlaybackState.Preparing) actions.cancelEpisodePlayback()
        modalReturn = trigger
        modal = type
    }
    fun back() {
        when {
            modal != null -> dismiss()
            state?.playback is EpisodePlaybackState.Preparing -> actions.cancelEpisodePlayback()
            else -> actions.back()
        }
    }
    BackHandler(modal == null && state?.playback is EpisodePlaybackState.Preparing) { back() }

    fun matches(trigger: String, media: Media?): Boolean =
        when (val playback = state?.playback) {
            is EpisodePlaybackState.Preparing ->
                playback.intent.trigger == trigger && playback.intent.episodeId == media?.id
            is EpisodePlaybackState.Failed ->
                playback.intent.trigger == trigger && playback.intent.episodeId == media?.id
            else -> false
        }
    fun label(media: Media?, trigger: String): String =
        when {
            matches(trigger, media) && state?.playback is EpisodePlaybackState.Failed ->
                "重试 S${media?.season} E${media?.episode}"
            media == null -> "播放本集"
            media.resumeTicks > 0 -> "继续 S${media.season} E${media.episode}"
            else -> "播放 S${media.season} E${media.episode}"
        }
    fun canPlay(media: Media?, trigger: String): Boolean =
        media != null &&
            when (state?.playback) {
                is EpisodePlaybackState.Preparing -> matches(trigger, media)
                is EpisodePlaybackState.Ready -> false
                else -> true
            }
    fun play(media: Media, fromStart: Boolean, source: String?, trigger: String) {
        when {
            matches(trigger, media) && state?.playback is EpisodePlaybackState.Preparing ->
                actions.cancelEpisodePlayback()
            matches(trigger, media) && state?.playback is EpisodePlaybackState.Failed ->
                actions.retryEpisodePlayback()
            canPlay(media, trigger) -> actions.prepareEpisode(media, fromStart, source, trigger)
        }
    }
    val root =
        Modifier.fillMaxSize().testTag("series-detail").onPreviewKeyEvent { event ->
            if (
                !tv ||
                    modal != null ||
                    event.type != KeyEventType.KeyDown ||
                    overviewScroll.value != 0
            )
                false
            else {
                // Only the introduction has explicit navigation; the rest uses native focus/scroll.
                val key = focus.pending ?: focus.target
                val next =
                    when {
                        key.isBlank() && event.key == Key.DirectionUp -> "header:back"
                        key.isBlank() &&
                            event.key in
                                setOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) ->
                            "detail:play"
                        key == "header:back" && event.key == Key.DirectionDown -> "detail:play"
                        key == "detail:play" && event.key == Key.DirectionRight -> "favorite"
                        key == "favorite" && event.key == Key.DirectionLeft -> "detail:play"
                        key in setOf("detail:play", "favorite") && event.key == Key.DirectionUp ->
                            "header:back"
                        else -> null
                    }
                if (next != null) {
                    focus.move(next)
                    true
                } else false
            }
        }
    Box(root) {
        // A non-action focus sink prevents automatic child focus and accidental activation on
        // entry.
        if (tv)
            Box(Modifier.size(1.dp).focusRequester(entryFocus).focusable().testTag("series:entry"))
        val content =
            SeriesOverviewContent(
                ui,
                images,
                actions.copy(back = ::back),
                focus,
                overviewScroll,
                previewList,
                peopleList,
                relatedList,
                { show("season", "series:season") },
                { episode ->
                    modalEpisodeId = episode.id
                    actions.selectEpisode(episode)
                    show("episode", "series:preview:${episode.id}")
                },
                ::label,
                ::canPlay,
                ::play,
            )
        if (tv) TvSeriesDetailLayout(content) else MobileSeriesDetailLayout(content)
    }
    when (modal) {
        "season" ->
            SeasonPicker(
                state?.seasons?.data.orEmpty(),
                seasonId,
                { id ->
                    dismiss()
                    if (id != seasonId) {
                        actions.cancelEpisodePlayback()
                        actions.selectSeason(id)
                    }
                },
                ::dismiss,
            )
        "episode",
        "media" ->
            entries
                .find { it.id == modalEpisodeId }
                ?.let { fallback ->
                    val episode =
                        state?.episodeDetails?.data?.takeIf { it.id == modalEpisodeId } ?: fallback
                    EpisodeDetails(
                        episode,
                        state?.episodeDetails ?: SeriesResource.Idle,
                        images,
                        modal == "media",
                        ::dismiss,
                        actions.retrySeriesEpisode,
                        { media, fromStart, source ->
                            val trigger = "sheet:${media.id}:${if (fromStart) "start" else source}"
                            play(media, fromStart, source, trigger)
                        },
                        { modal = "media" },
                        {
                            if (state?.playback is EpisodePlaybackState.Preparing)
                                actions.cancelEpisodePlayback()
                            modal = "episode"
                        },
                        when (val playback = state?.playback) {
                            is EpisodePlaybackState.Failed ->
                                "${playback.intent.label} · ${playback.message}"
                            else -> null
                        },
                        { media, trigger -> label(media, trigger) },
                        { media, trigger -> canPlay(media, trigger) },
                        { media, trigger ->
                            matches(trigger, media) &&
                                state?.playback is EpisodePlaybackState.Preparing
                        },
                    )
                }
    }
}
