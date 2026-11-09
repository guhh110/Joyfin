package dev.joyfin.ui.features.home.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.InputMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.joyfin.data.Home
import dev.joyfin.data.Media
import dev.joyfin.data.groupLatestVideo
import dev.joyfin.browse.TrackingUiState
import dev.joyfin.ui.components.rememberArtworkRequest
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal data class CarouselFrame(val item: Media, val request: ImageRequest?)

@Stable
internal class HomeCarouselState(initialId: String, initialItems: List<Media>) {
    var items by mutableStateOf(initialItems)
    var currentId by mutableStateOf(initialId)
    var targetId by mutableStateOf(initialItems.firstOrNull { it.id == initialId }?.id ?: initialItems.firstOrNull()?.id ?: initialId)
    var frame by mutableStateOf<CarouselFrame?>(null)
    var loading by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var focused by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var direction by mutableIntStateOf(1)
    var manualRequest by mutableStateOf(false)
    var tracking by mutableStateOf<TrackingUiState?>(null)
    val current: Media? get() = frame?.item ?: items.firstOrNull { it.id == currentId } ?: items.firstOrNull()
    val index: Int get() = items.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)

    fun update(values: List<Media>) {
        if (items == values) return
        items = values
        frame = frame?.let { old -> values.firstOrNull { it.id == old.item.id }?.let { old.copy(item = it) } }
        if (values.isEmpty()) {
            frame = null
            currentId = ""
            loading = false
        }
        if (values.none { it.id == targetId }) targetId = values.firstOrNull { it.id == currentId }?.id ?: values.firstOrNull()?.id.orEmpty()
    }

    fun step(delta: Int, manual: Boolean = true) {
        if (items.size < 2) return
        val from = items.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: index
        targetId = items[(from + delta + items.size) % items.size].id
        direction = delta
        manualRequest = manual
        loading = frame?.item?.id != targetId
        if (manual) revision++
    }

}

/** Keep one current image and at most one image ahead, including shared in-flight requests. */
@Composable
internal fun rememberHomeCarousel(home: Home, images: ArtworkSource, tracking: TrackingUiState? = null): HomeCarouselState {
    val candidates = remember(home.latest, home.resume, home.rails, tracking) {
        when {
            tracking == null -> groupLatestVideo(home.latest.ifEmpty { home.resume.ifEmpty { home.rails.flatMap { it.items } } }).take(6)
            tracking.empty -> groupLatestVideo(home.latest).take(6)
            else -> tracking.carousel.map { it.series }
        }
    }
    val saver = Saver<HomeCarouselState, String>(save = { it.currentId }, restore = { HomeCarouselState(it, candidates) })
    val state = rememberSaveable(saver = saver) { HomeCarouselState("", candidates) }
    SideEffect { state.update(candidates); state.tracking = tracking }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val active = resumed && windowFocused && state.visible
    val focus = LocalScreenFocus.current
    val television = LocalDeviceLayout.current.television
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(windowFocused, television) {
        if (windowFocused && television && focus?.target?.invoke() == "header:home") {
            inputMode.requestInputMode(InputMode.Keyboard)
            withFrameNanos {}
            if (focus.target() == "header:home") focus.requesters["header:home"]?.requestFocus()
        }
    }
    val loader = LocalContext.current.imageLoader
    val scope = rememberCoroutineScope()
    val jobs = remember(loader, images.authorization, images.cacheNamespace) { mutableMapOf<String, Deferred<ImageRequest?>>() }
    val size = LocalWindowInfo.current.containerSize
    val target = state.items.firstOrNull { it.id == state.targetId }
    val targetRequest = rememberArtworkRequest(target?.let(images.backdrop), images)
    val next = state.items.takeIf { it.size > 1 }?.let { values ->
        values[(values.indexOfFirst { it.id == state.targetId }.coerceAtLeast(0) + 1) % values.size]
    }
    val nextRequest = rememberArtworkRequest(next?.let(images.backdrop), images)

    suspend fun prepare(request: ImageRequest): ImageRequest? {
        val key = request.diskCacheKey ?: request.data.toString()
        return jobs.getOrPut(key) {
            scope.async {
                val sized = request.newBuilder().size(size.width.coerceIn(1, 1920), size.height.coerceIn(1, 1080)).build()
                if (loader.execute(sized) is SuccessResult) sized else null
            }
        }.await().also { if (it == null) jobs.remove(key) }
    }

    LaunchedEffect(active, target, targetRequest, state.manualRequest) {
        if (!active || target == null) return@LaunchedEffect
        state.loading = true
        val ready = prepare(targetRequest)
        ensureActive()
        if (ready != null || state.manualRequest) {
            state.currentId = target.id
            state.frame = CarouselFrame(target, ready ?: state.frame?.request)
        }
        state.loading = false
    }
    LaunchedEffect(active, state.frame, nextRequest, state.loading) {
        if (active && !state.loading && state.frame != null && next != null) prepare(nextRequest)
    }
    val keep = setOfNotNull(targetRequest.diskCacheKey, nextRequest.diskCacheKey)
    LaunchedEffect(active, keep) {
        jobs.keys.toList().filter { !active || it !in keep }.forEach { key -> jobs.remove(key)?.cancel() }
        if (!active) state.loading = false
    }
    DisposableEffect(jobs) { onDispose { jobs.values.forEach { it.cancel() }; jobs.clear() } }
    LaunchedEffect(active, state.focused, state.dragging, state.loading, state.currentId, state.targetId, state.revision, state.items.size) {
        if (active && !state.focused && !state.dragging && !state.loading && state.items.size > 1) {
            delay(8000)
            state.step(1, manual = false)
        }
    }
    return state
}
