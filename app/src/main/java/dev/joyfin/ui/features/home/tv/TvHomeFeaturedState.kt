package dev.joyfin.ui.features.home.tv

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.joyfin.ui.components.rememberArtworkRequest
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.data.Media
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Stable
internal class TvHomeFeaturedState(initialKey: String = "", initialIndex: Int = 0) {
    var items by mutableStateOf(emptyList<TvHomeFeaturedItem>())
        private set
    var selectedKey by mutableStateOf(initialKey)
        private set
    var selectedIndex by mutableIntStateOf(initialIndex)
        private set
    var interacted by mutableStateOf(false)
        private set
    var artwork by mutableStateOf<ImageRequest?>(null)
    var artworkKey by mutableStateOf<String?>(null)
    var landscape by mutableStateOf(false)
    val current: TvHomeFeaturedItem? get() = items.firstOrNull { it.key == selectedKey }

    fun update(values: List<TvHomeFeaturedItem>) {
        if (items.isEmpty() && values.isEmpty()) return
        val index = values.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 }
            ?: selectedIndex.coerceIn(0, values.lastIndex.coerceAtLeast(0))
        items = values
        selectedIndex = index
        selectedKey = values.getOrNull(index)?.key.orEmpty()
    }

    fun select(key: String) {
        val index = items.indexOfFirst { it.key == key }
        if (index < 0) return
        selectedKey = key
        selectedIndex = index
    }

    fun onInput() { interacted = true }

    suspend fun advanceAfterIdle(key: String) {
        delay(10_000)
        if (!interacted && current?.key == key && items.size > 1)
            select(items[(selectedIndex + 1) % items.size].key)
    }
}

@Composable
internal fun rememberTvHomeFeaturedState(
    resume: List<Media>, tracking: TrackingUiState?, discovery: List<Media>,
    images: ArtworkSource, active: Boolean, blocked: Boolean,
): TvHomeFeaturedState {
    val saver = listSaver<TvHomeFeaturedState, Any>(
        save = { listOf(it.selectedKey, it.selectedIndex) },
        restore = { TvHomeFeaturedState(it[0] as String, it[1] as Int) },
    )
    val state = rememberSaveable(images.cacheNamespace, saver = saver) { TvHomeFeaturedState() }
    val items = remember(resume, tracking, discovery, state.selectedKey) {
        tvHomeFeaturedItems(resume, tracking, discovery, state.selectedKey)
    }
    SideEffect { state.update(items) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val window = LocalWindowInfo.current
    val running = active && !blocked && window.isWindowFocused && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val loader = LocalContext.current.imageLoader
    val current = state.current
    val landscape = current?.media?.backdropTag?.isNotBlank() == true
    val request = rememberArtworkRequest(current?.media?.let { if (landscape) images.backdrop(it) else images.poster(it) }, images)
    val size = window.containerSize
    var readyRequest by remember { mutableStateOf<ImageRequest?>(null) }
    LaunchedEffect(running, current?.key, request) {
        if (!running || current == null) return@LaunchedEffect
        val sized = request.newBuilder().size(
            if (landscape) size.width.coerceIn(1, 1920) else 640,
            if (landscape) size.height.coerceIn(1, 1080) else 960,
        ).build()
        val loaded = if (current.media.id.isBlank() || request.data.toString().isBlank()) null
            else (loader.execute(sized) as? SuccessResult)?.let { sized }
        ensureActive()
        state.artwork = loaded
        state.landscape = landscape
        state.artworkKey = current.key
        readyRequest = request
    }
    val next = state.items.takeIf { it.size > 1 }?.get((state.selectedIndex + 1) % state.items.size)
    val nextLandscape = next?.media?.backdropTag?.isNotBlank() == true
    val nextRequest = rememberArtworkRequest(next?.media?.let { if (nextLandscape) images.backdrop(it) else images.poster(it) }, images)
    LaunchedEffect(running, readyRequest, nextRequest) {
        if (running && readyRequest == request && next != null && nextRequest.data.toString().isNotBlank())
            loader.execute(nextRequest.newBuilder().size(
                if (nextLandscape) size.width.coerceIn(1, 1920) else 640,
                if (nextLandscape) size.height.coerceIn(1, 1080) else 960,
            ).build())
    }
    LaunchedEffect(running, state.interacted, readyRequest, current?.key) {
        if (running && !state.interacted && current != null && readyRequest == request && state.items.size > 1)
            state.advanceAfterIdle(current.key)
    }
    return state
}
