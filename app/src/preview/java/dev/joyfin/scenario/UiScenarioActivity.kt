package dev.joyfin.scenario

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
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
import okhttp3.OkHttpClient

private class ScenarioRuntime(application: android.app.Application, mode: Scenario, delay: Long) : ViewModel() {
    val context = ScenarioContext(application)
    val store = SessionStore(context).apply {
        save(Session("http://ui-scenario.invalid", "demo-token", "preview", "演示账号"))
    }
    val transport = ScenarioTransport(mode, delay)
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
                ScenarioRuntime(application, mode, intent.getLongExtra("delayMs", 450)) as T
        }
        val runtime = ViewModelProvider(this, runtimeFactory)[ScenarioRuntime::class.java]
        val transport = runtime.transport
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(application, runtime.store, runtime.api, runtime.context) as T
        }
        val vm = ViewModelProvider(this, factory)[AppViewModel::class.java]
        val tv = resources.configuration.isTelevision()
        requestedOrientation = if (tv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val image = "android.resource://$packageName/drawable/entry_harbor"
        val artwork = ArtworkSource({ image }, { image }, { null }, { null }, "")
        setContent {
            BoundlessTheme {
                val state by vm.state.collectAsState()
                var scenario by remember { mutableStateOf(transport.scenario) }
                var entered by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.page) {
                    if (!entered && state.page == Page.HOME) {
                        entered = true
                        when (intent.getStringExtra("page")) {
                            "detail" -> vm.open(Media.from(ScenarioMedia.series))
                            "movie" -> vm.open(Media.from(ScenarioMedia.movies.first()))
                            "library" -> vm.library()
                            "search" -> { vm.search(); vm.submitSearch("群山") }
                            "subtitles" -> vm.openSubtitleManager()
                            "person" -> vm.openPerson(Media.from(ScenarioMedia.series).people.first())
                        }
                    }
                }
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                        BrowseHost(vm, artwork) { _, _, _ -> Toast.makeText(this@UiScenarioActivity, "演示入口不启动实际播放", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
        if (tv) window.decorView.post { WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
    }
}
