package dev.joyfin.scenario

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.joyfin.BrowseHost
import dev.joyfin.app.AppViewModel
import dev.joyfin.app.Page
import dev.joyfin.data.*
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.isTelevision
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.BoundlessTheme
import dev.joyfin.ui.features.player.components.PlayerPanel
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import coil.Coil
import coil.ImageLoader
import java.net.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ScenarioRuntime(application: android.app.Application, mode: Scenario, delay: Long, stress: Boolean, payloadStress: Boolean) : ViewModel() {
    val context = ScenarioContext(application)
    val store = SessionStore(context).apply {
        save(Session("http://ui-scenario.invalid", "demo-token", "preview", "演示账号"))
    }
    val transport = ScenarioTransport(mode, delay, stress, payloadStress)
    val api = JellyfinApi(store.deviceId, OkHttpClient.Builder().addInterceptor(transport).build())
}

/** ADB-only preview entry. No launcher, real sessions, production flags or duplicate UI. */
class UiScenarioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = Scenario.entries.firstOrNull { it.name.equals(intent.getStringExtra("scenario"), true) } ?: Scenario.NORMAL
        val runtimeFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScenarioRuntime(application, mode, intent.getLongExtra("delayMs", 450), intent.getBooleanExtra("stress", false), intent.getBooleanExtra("payloadStress", false)) as T
        }
        val runtime = ViewModelProvider(this, runtimeFactory)[ScenarioRuntime::class.java]
        val transport = runtime.transport
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(application, runtime.store, runtime.api, runtime.context,
                    discoverLocalServers = { kotlinx.coroutines.flow.emptyFlow() }) as T
        }
        val vm = ViewModelProvider(this, factory)[AppViewModel::class.java]
        val tv = resources.configuration.isTelevision()
        val playerPage = intent.getStringExtra("page") in setOf("player", "player-sidebar")
        requestedOrientation = if (tv || playerPage) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val image = "android.resource://$packageName/drawable/entry_harbor"
        // Optional host-side image fixture, reachable through adb reverse. Never use real accounts.
        val imageBase = intent.getStringExtra("artworkBase")?.trimEnd('/')?.takeIf {
            it.toHttpUrlOrNull()?.host in setOf("127.0.0.1", "localhost", "10.0.2.2")
        }
        if (imageBase != null) Coil.setImageLoader {
            ImageLoader.Builder(applicationContext)
                .okHttpClient { OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build() }
                .bitmapFactoryMaxParallelism(intent.getIntExtra("decodeParallelism", 4).coerceIn(1, 4))
                .apply {
                    if (intent.hasExtra("decodeParallelism")) eventListenerFactory { ScenarioImageProfile() }
                }
                .build()
        }
        fun fixture(url: String): String = url.replace("http://ui-scenario.invalid", imageBase.orEmpty())
        val artwork = if (imageBase == null) ArtworkSource({ image }, { image }, { null }, { null }, "")
        else ArtworkSource({ fixture(runtime.api.image(it)) }, { fixture(runtime.api.backdrop(it)) },
            { runtime.api.episodeImage(it)?.let(::fixture) }, { runtime.api.personImage(it)?.let(::fixture) }, "",
            thumbnail = { fixture(runtime.api.thumbnail(it)) })
        val skeletonStyle = CardSkeletonStyle.entries.firstOrNull { it.name.equals(intent.getStringExtra("skeleton"), true) }
        val profileRun = intent.getStringExtra("profileRun")
        setContent {
          CompositionLocalProvider(LocalCardSkeletonOverride provides
              if (skeletonStyle == CardSkeletonStyle.Plain || skeletonStyle == CardSkeletonStyle.Drawn) {
                  { modifier, wide, visible, showSubtitle ->
                      ScenarioSkeleton(modifier, wide, visible, showSubtitle, skeletonStyle == CardSkeletonStyle.Plain)
                  }
              } else null) {
            BoundlessTheme {
                val state by vm.state.collectAsState()
                var scenario by remember { mutableStateOf(transport.scenario) }
                var entered by rememberSaveable { mutableStateOf(false) }
                var showPlayer by rememberSaveable { mutableStateOf(playerPage) }
                var reloadIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("payloadProfile", false)) {
                        vm.state.first { it.page == Page.LIBRARY && !it.collection.busy && it.collection.media.isNotEmpty() }
                        delay(1800)
                        withContext(Dispatchers.Default) { ScenarioPayloadProfile.run() }
                    }
                }
                LaunchedEffect(state.page, state.collection.busy, state.collection.media.size, reloadIndex) {
                    if (profileRun != null) {
                        val phase = if (state.page != Page.LIBRARY) "startup"
                            else if (state.collection.media.isNotEmpty()) "content"
                            else if (state.collection.busy) "loading" else "idle"
                        Log.i("JoyfinUiProfile", "run=$profileRun round=$reloadIndex phase=$phase ns=${System.nanoTime()}")
                    }
                }
                LaunchedEffect(profileRun) {
                    if (profileRun != null && intent.getStringExtra("page") == "library-grid") {
                        repeat(intent.getIntExtra("profileReloads", 0).coerceIn(0, 5)) {
                            vm.state.first { it.page == Page.LIBRARY && !it.collection.busy && it.collection.media.isNotEmpty() }
                            delay(1800)
                            reloadIndex++
                            vm.library(ScenarioMedia.library.id, ScenarioMedia.library.name)
                        }
                    }
                }
                val initialPanel = remember {
                    PlayerPanel.entries.firstOrNull { it.name.equals(intent.getStringExtra("panel"), true) }
                        ?: if (intent.getStringExtra("page") == "player-sidebar") PlayerPanel.Settings else null
                }
                LaunchedEffect(showPlayer) {
                    if (!tv) requestedOrientation = if (showPlayer) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                LaunchedEffect(state.page) {
                    if (!entered && state.page == Page.HOME) {
                        entered = true
                        when (intent.getStringExtra("page")) {
                            "detail" -> vm.open(Media.from(ScenarioMedia.series))
                            "movie" -> vm.open(Media.from(ScenarioMedia.movies.first()))
                            "library" -> vm.library()
                            "library-grid" -> vm.library(ScenarioMedia.library.id, ScenarioMedia.library.name)
                            "search" -> { vm.search(); vm.submitSearch("群山") }
                            "subtitles" -> vm.openSubtitleManager()
                            "settings" -> vm.settings()
                            "audit" -> vm.openLibraryAudit(null, "全部电影与剧集")
                            "person" -> vm.openPerson(Media.from(ScenarioMedia.series).people.first())
                        }
                    }
                }
                if (showPlayer) {
                    ScenarioPlayer(scenario, initialPanel, intent.getStringExtra("skipOffer")) { showPlayer = false }
                } else Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (intent.getStringExtra("page") != "audit") Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Caption("UI 演示 · 本地模拟")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Scenario.entries.forEach { value ->
                            ActionButton(when (value) { Scenario.NORMAL -> "正常"; Scenario.EMPTY -> "空数据"; Scenario.ERROR -> "错误" }, {
                                scenario = value
                                transport.scenario = value
                                when (state.page) {
                                    Page.HOME -> vm.refreshHome()
                                    Page.DETAIL -> { vm.retry(); vm.retrySeriesSeasons(); vm.retrySeriesRecommendation(); vm.retryRelated() }
                                    Page.LIBRARY -> vm.library()
                                    Page.SEARCH -> vm.submitSearch(state.collection.query.ifBlank { "群山" })
                                    Page.PERSON -> state.collection.person?.let(vm::openPerson) ?: vm.retry()
                                    else -> vm.retry()
                                }
                            }, selected = scenario == value, quiet = true, maxLines = 1)
                        }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        BrowseHost(vm, artwork, updates = null) { _, _, _ -> showPlayer = true }
                    }
                }
            }
          }
        }
        if (tv) window.decorView.post { WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
    }
}
