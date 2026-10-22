package dev.joyfin

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joyfin.app.AppViewModel
import dev.joyfin.data.Media
import dev.joyfin.ui.AppContent
import dev.joyfin.ui.components.ConfirmDialog
import dev.joyfin.ui.device.isTelevision
import dev.joyfin.ui.features.entry.EntryActions
import dev.joyfin.ui.features.entry.NetworkPermissionRoute
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.BoundlessTheme
import kotlinx.coroutines.flow.first

private const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Platform lifecycle and dependency wiring only; no page layouts or business implementation. */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation =
            if (resources.configuration.isTelevision()) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // The cinema UI is always dark, even when Android uses its light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val allowed =
            mutableStateOf(
                Build.VERSION.SDK_INT < 37 ||
                    checkSelfPermission(PERMISSION_ACCESS_LOCAL_NETWORK) ==
                        PackageManager.PERMISSION_GRANTED
            )
        val permissionDenied = mutableStateOf(false)
        val permission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                allowed.value = it
                permissionDenied.value = !it
            }
        setContent {
            BoundlessTheme {
                if (allowed.value) BrowseHost(vm)
                else
                    NetworkPermissionRoute(
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= 37)
                                permission.launch(PERMISSION_ACCESS_LOCAL_NETWORK)
                        },
                        denied = permissionDenied.value,
                    )
            }
        }
        if (resources.configuration.isTelevision())
            window.decorView.post {
                WindowCompat.getInsetsController(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }
    }
}

@Composable
internal fun BrowseHost(
    vm: AppViewModel,
    imagesOverride: ArtworkSource? = null,
    onPlayOverride: ((Media, Boolean, String?) -> Unit)? = null,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val audioOutputMode by vm.audioOutputMode.collectAsStateWithLifecycle()
    val homeLayout by vm.homeLayout.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var exit by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var autoNext by remember { mutableStateOf(vm.store.autoNext) }
    val player =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.getBooleanExtra("authExpired", false) == true) vm.expireSession()
            else vm.refreshAfterPlayback()
        }
    val play: (Media, Boolean, String?) -> Unit =
        remember(context, player, onPlayOverride) {
            { item, fromStart, source ->
                if (onPlayOverride != null) onPlayOverride(item, fromStart, source)
                else player.launch(
                    Intent(context, PlayerActivity::class.java)
                        .putExtra("itemId", item.id)
                        .putExtra("startTicks", if (fromStart) 0L else item.resumeTicks)
                        .putExtra("sourceId", source)
                )
            }
        }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val readyEpisode = ui.detail.series?.playback as? dev.joyfin.browse.EpisodePlaybackState.Ready
    LaunchedEffect(readyEpisode?.intent?.sequence) {
        readyEpisode?.let { ready ->
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (
                (vm.state.value.detail.series?.playback
                        as? dev.joyfin.browse.EpisodePlaybackState.Ready)
                    ?.intent
                    ?.sequence != ready.intent.sequence
            )
                return@let
            vm.consumeEpisodePlayback(ready.intent.sequence)
            play(ready.media, ready.intent.fromStart, ready.intent.sourceId)
        }
    }
    val back: () -> Unit = remember(vm) { { if (!vm.back()) exit = true } }
    BackHandler(onBack = back)
    val actions =
        remember(vm, back, play) {
            BrowseActions(
                back = back,
                home = vm::home,
                open = vm::open,
                openPerson = vm::openPerson,
                selectSeason = vm::season,
                selectEpisode = vm::selectEpisode,
                retrySeriesSeasons = vm::retrySeriesSeasons,
                retrySeriesSeason = vm::retrySeriesSeason,
                retrySeriesEpisode = vm::retrySeriesEpisode,
                retrySeriesRecommendation = vm::retrySeriesRecommendation,
                prepareEpisode = vm::prepareEpisode,
                cancelEpisodePlayback = vm::cancelEpisodePlayback,
                retryEpisodePlayback = vm::retryEpisodePlayback,
                retryRelated = vm::retryRelated,
                retryHome = vm::retryHome,
                retryLibrary = vm::retryLibrary,
                library = vm::library,
                search = vm::search,
                submitSearch = vm::submitSearch,
                loadMore = { vm.loadItems(true) },
                settings = vm::settings,
                subtitles = vm::openSubtitles,
                subtitleManager = vm::openSubtitleManager,
                favorite = vm::toggleFavorite,
                play = play,
                retry = vm::retry,
                logout = { logout = true },
                setAutoNext = {
                    autoNext = it
                    vm.store.autoNext = it
                },
                setAudioOutputMode = vm::setAudioOutputMode,
                setHomeLayout = vm::setHomeLayout,
            )
        }
    val images =
        remember(vm, ui.entry.userName, ui.entry.server, vm.api.session) {
            ArtworkSource(
                poster = vm.api::image,
                backdrop = vm.api::backdrop,
                episode = vm.api::episodeImage,
                person = vm.api::personImage,
                authorization = if (vm.api.session != null) vm.api.authorization() else "",
                logo = vm.api::logo,
                cacheNamespace = dev.joyfin.artwork.ArtworkCacheIdentity.namespace(vm.api.session),
            )
        }
    val entryActions =
        remember(vm) {
            EntryActions(vm::connect, vm::login, vm::changeServer, vm::cancelEntry, vm::retry)
        }
    AppContent(ui, actions, imagesOverride ?: images, autoNext, entryActions, vm.subtitlePage, audioOutputMode, homeLayout)
    if (exit)
        ConfirmDialog("退出 Joyfin？", { exit = false }, { (context as? ComponentActivity)?.finish() },
            confirmInitiallyFocused = true)
    if (logout)
        ConfirmDialog(
            "退出当前账号？",
            { logout = false },
            {
                logout = false
                vm.logout()
            },
        )
}
