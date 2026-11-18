package dev.joyfin.scenario

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.*
import dev.joyfin.data.subtitles.assrt.AssrtAccount
import dev.joyfin.data.subtitles.opensubtitles.OpenSubtitlesAccount
import dev.joyfin.subtitles.SubtitlePageController
import dev.joyfin.ui.features.subtitles.*
import dev.joyfin.ui.components.cinemaBackground
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** Local-only requests exercise the production controllers and saved-file workflow. */
@Composable
internal fun ScenarioSubtitles(context: Context, page: String, scenario: Scenario, images: ArtworkSource, exit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val transport = remember { SubtitleScenarioTransport(scenario) }
    val client = remember { OkHttpClient.Builder().addInterceptor(transport).build() }
    val api = remember { JellyfinApi("subtitle-preview", client).apply { session = Session("http://subtitle-scenario.invalid", "preview", "preview", "演示账号") } }
    val controller = remember { SubtitlePageController(context, scope, api, {}, client) }
    var ready by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(page.substringAfter("subtitles-", "")) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            controller.assrtAccounts.save(AssrtAccount(true, "a".repeat(32)))
            controller.openSubtitlesAccounts.save(OpenSubtitlesAccount(enabled = true, apiKey = "preview-only"))
            val store = LocalSubtitleStore.get(context)
            val owner = LocalSubtitleStore.owner(api.session!!.server, "preview")
            repeat(5) { index ->
                val rows = (1..8).map { episode ->
                    val language = when (episode % 3) { 0 -> "繁體中文.cht"; 2 -> "English.eng"; else -> "简体中文与英文双语.chs.eng" }
                    val name = "Mountains.Beyond.The.Harbor.2025.S01E${episode.toString().padStart(2, '0')}.2160p.WEB-DL.DDP5.1.HDR.H265-LongReleaseGroup.$language.srt"
                    val file = store.save("preview-pack-$index", name, "本地模拟", "chi", subtitleText(index * 10 + episode))
                    ManagedSubtitleFile(file.id, name,
                        if (index > 0 && episode < 6) {
                            val alternate = episode % 2 == 0
                            SubtitleMapping("ep-$episode", "第 $episode 集 · 海港来信", "ep-$episode${if (alternate) "-alt" else ""}",
                                if (alternate) "1080p 发行版" else "原画 4K", "/preview/episode-$episode${if (alternate) "-alt" else ""}.mkv", episode)
                        } else null,
                        upload = if (index == 2 && episode == 2) "failed" else if (index == 3 && episode < 4) "done" else "",
                        manualMapping = index > 0 && episode >= 6,
                        uploadError = if (index == 2 && episode == 2) "演示错误：服务器拒绝回传，请核对字幕管理权限。原有字幕文件与本地关联均已保留，可修正后重试。" else null)
                }
                val pack = ManagedSubtitle("layout-preview-$index", owner, "群山之间 第一季 · 原盘匹配与网络发行版本 · 简繁英双语字幕包 $index.zip",
                    if (index % 2 == 0) "ASSRT · 本地模拟" else "OpenSubtitles · 本地模拟", 100L - index, rows,
                    workId = if (index > 0) "series" else null, workName = if (index > 0) "群山之间：漫长旅途与归来" else "", seasonId = "season-1", seasonName = "第一季")
                store.putPackage(pack)
                if (index == 3) store.bindPackage(pack, api.session!!.server, "preview")
            }
        }
        controller.open()
        controller.state.first { it.packagesLoaded }
        if (page == "subtitles-search") { controller.query("Mountains Beyond"); controller.showSearch() }
        if (page in setOf("subtitles-organize", "subtitles-files", "subtitles-upload")) {
            controller.organize("layout-preview-2")
            controller.organizer.state.first { !it.loading && it.media.isNotEmpty() }
        }
        ready = true
    }
    DisposableEffect(Unit) { onDispose { controller.close() } }
    val actions = remember {
        BrowseActions(back = exit, home = exit, open = {}, openPerson = {}, selectEpisode = {}, retryLibrary = {},
            selectSeason = {}, library = { _, _ -> exit() }, search = exit, submitSearch = {}, loadMore = {}, settings = exit,
            favorite = {}, play = { _, _, _ -> }, retry = {}, logout = {}, setAutoNext = {}, tracking = exit)
    }
    if (ready) Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        .then(if (LocalDeviceLayout.current.television) Modifier.cinemaBackground() else Modifier)) {
        SubtitleManagementRoute(controller, images, exit, actions)
        val editor by controller.organizer.state.collectAsState()
        if (overlay == "files") editor.media.firstOrNull()?.let { media ->
            SubtitleEpisodeFilesSheet(controller.organizer, media, media.sources.first().id) { overlay = "" }
        }
        if (overlay == "upload") SubtitleUploadConfirmation(controller, { overlay = "" }) { overlay = ""; controller.uploader.upload() }
    }
}

private fun subtitleText(id: Int) = "1\n00:00:01,000 --> 00:00:04,000\n模拟字幕 $id · 海港来信\n"

private class SubtitleScenarioTransport(private val scenario: Scenario) : Interceptor {
    private val uploads = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        var code = 200
        var mime = "application/json"
        val json = when (request.url.host) {
            "api.assrt.net" -> if (path.endsWith("search")) JSONObject().put("status", 0).put("sub", JSONObject().put("subs", JSONArray().apply {
                if (scenario != Scenario.EMPTY) repeat(8) { i -> put(JSONObject().put("id", i + 1).put("native_name", "群山之间 第一季 · 简英双语 ${i + 1}")
                    .put("videoname", "Mountains.Beyond.The.Harbor.S01.2160p.WEB-DL.H265.HDR.DDP5.1-ReleaseGroup.$i.zip")
                    .put("subtype", "SRT").put("lang", JSONObject().put("desc", "简体中文 / 英语"))) }
            })).toString() else JSONObject().put("status", 0).put("sub", JSONObject().put("subs", JSONArray().put(JSONObject().put("id", 1)
                .put("native_name", "群山之间 第一季").put("filelist", JSONArray().apply {
                    repeat(4) { i -> put(JSONObject().put("f", "Mountains.Beyond.S01E0${i + 1}.chs.eng.srt").put("url", "https://subtitle-files.invalid/${i + 1}.srt")) }
                })))).toString()
            "api.opensubtitles.com" -> if (path.endsWith("download")) """{"link":"https://subtitle-files.invalid/1.srt","file_name":"Mountains.Beyond.S01E01.chs.srt","remaining":19}"""
                else JSONObject().put("total_pages", 2).put("data", JSONArray().apply {
                    if (scenario != Scenario.EMPTY) repeat(8) { i -> put(JSONObject().put("id", "$i").put("attributes", JSONObject().put("language", "zh-cn")
                        .put("release", "Mountains.Beyond.S01E0${i + 1}.2160p.HDR.HEVC.DDP5.1.WEB-DL-Release.srt")
                        .put("feature_details", JSONObject().put("title", "群山之间 第 ${i + 1} 集"))
                        .put("files", JSONArray().put(JSONObject().put("file_id", i + 1).put("file_name", "Mountains.Beyond.S01E0${i + 1}.chs.srt"))))) }
                }).toString()
            "subtitle-files.invalid" -> { mime = "application/octet-stream"; subtitleText(path.filter(Char::isDigit).toIntOrNull() ?: 1) }
            "subtitle-scenario.invalid" -> when {
                request.method == "POST" && path.contains("Subtitles") -> {
                    val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    val content = String(android.util.Base64.decode(JSONObject(body).getString("Data"), android.util.Base64.DEFAULT))
                    uploads.getOrPut(path.split('/')[2]) { mutableListOf() }.add(content)
                    "{}"
                }
                path.contains("/Subtitles/") && path.endsWith("Stream.srt") -> {
                    mime = "application/octet-stream"
                    uploads[path.split('/')[2]]?.getOrNull((path.split('/').dropWhile { it != "Subtitles" }.getOrNull(1)?.toIntOrNull() ?: 100) - 100) ?: subtitleText(1)
                }
                path == "/Items/series" -> media("series", "群山之间：漫长旅途与归来", "Series").toString()
                path.endsWith("/Seasons") -> items((1..2).map { media("season-$it", "第${it}季", "Season").put("IndexNumber", it) })
                path.endsWith("/Episodes") || path == "/Items" -> {
                    val season = if (request.url.queryParameter("SeasonId") == "season-2") 2 else 1
                    items((1..8).map { episode(it, season = season) })
                }
                path.startsWith("/Items/ep-") -> episode(path.substringAfter("ep-").filter(Char::isDigit).toIntOrNull() ?: 1, path.endsWith("-alt")).toString()
                else -> "{}"
            }
            else -> throw java.io.IOException("字幕演示禁止连接真实服务")
        }
        if (scenario == Scenario.ERROR && request.url.host in setOf("api.assrt.net", "api.opensubtitles.com")) code = 503
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Local subtitle scenario")
            .body(json.toResponseBody(mime.toMediaType())).build()
    }
    private fun media(id: String, name: String, type: String) = JSONObject().put("Id", id).put("Name", name).put("Type", type)
    private fun items(rows: List<JSONObject>) = JSONObject().put("Items", JSONArray(rows)).put("TotalRecordCount", rows.size).toString()
    private fun episode(number: Int, alternate: Boolean = false, season: Int = 1): JSONObject {
        val id = "ep-$number${if (alternate) "-alt" else ""}"
        val streams = JSONArray().apply {
            uploads[id]?.forEachIndexed { index, _ -> put(JSONObject().put("Type", "Subtitle").put("Index", 100 + index).put("IsExternal", true)
                .put("Codec", "srt").put("Path", "/preview/upload-$number-$index.srt").put("DisplayTitle", "简体中文")) }
        }
        return media(id, "第 $number 集 · ${if (number % 2 == 0) "风从山谷来" else "海港的漫长来信"}", "Episode")
            .put("IndexNumber", number).put("ParentIndexNumber", season).put("SeriesId", "series").put("SeasonId", "season-$season")
            .put("Path", "/preview/episode-$number${if (alternate) "-alt" else ""}.mkv").put("MediaSources", JSONArray().apply {
                put(JSONObject().put("Id", id).put("Name", if (alternate) "1080p 发行版" else "原画 4K")
                    .put("Path", "/preview/episode-$number${if (alternate) "-alt" else ""}.mkv").put("Container", "mkv").put("MediaStreams", streams))
                if (!alternate && number % 2 == 0) put(JSONObject().put("Id", "ep-$number-alt").put("Name", "1080p 发行版")
                    .put("Path", "/preview/episode-$number-alt.mkv").put("Container", "mkv"))
            })
    }
}
