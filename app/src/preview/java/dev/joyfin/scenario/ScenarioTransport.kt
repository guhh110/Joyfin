package dev.joyfin.scenario

import dev.joyfin.data.*
import java.io.IOException
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

internal enum class Scenario { NORMAL, EMPTY, ERROR }

/** Synthetic HTTP responses still go through Retrofit's converter and normal controllers. */
internal class ScenarioTransport(
    @Volatile var scenario: Scenario,
    private val delayMs: Long,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Never forward even an unexpected request, including mutations, to a real server.
        if (request.url.host != "ui-scenario.invalid") throw IOException("模拟入口不允许访问真实服务器")
        var remaining = delayMs.coerceIn(0, 5_000)
        while (remaining > 0) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val step = minOf(remaining, 50)
            try { Thread.sleep(step) } catch (_: InterruptedException) { throw IOException("Canceled") }
            remaining -= step
        }
        val path = request.url.encodedPath
        val infrastructure = path == "/System/Info/Public" || path == "/Users/preview" ||
            path == "/Users/AuthenticateByName" || path == "/Sessions/Logout"
        val failed = !infrastructure && scenario == Scenario.ERROR
        val payload = if (failed) "" else when {
            path == "/System/Info/Public" -> AppJson.encodeToString(ServerInfo("UI 演示服务器"))
            path == "/Users/preview" -> AppJson.encodeToString(UserDto("preview", "演示账号"))
            path == "/Users/AuthenticateByName" -> AppJson.encodeToString(LoginResponse("demo-token", UserDto("preview", "演示账号")))
            request.method != "GET" -> "{}"
            ScenarioMedia.all.any { path == "/Items/${it.id}" } -> {
                val id = path.substringAfterLast('/')
                AppJson.encodeToString(ScenarioMedia.all.firstOrNull { it.id == id } ?: ScenarioMedia.series)
            }
            path.startsWith("/Persons/") -> AppJson.encodeToString(ItemDto("person", "演示演员", "Person"))
            else -> {
                val rows = if (scenario == Scenario.EMPTY) emptyList() else when {
                    path == "/UserViews" -> listOf(ScenarioMedia.library)
                    path == "/UserItems/Resume" -> ScenarioMedia.episodes.take(4)
                    path == "/Items/Latest" -> listOf(ScenarioMedia.series) + ScenarioMedia.movies
                    path.endsWith("/Seasons") -> ScenarioMedia.seasons
                    path.endsWith("/Episodes") -> {
                        val season = request.url.queryParameter("SeasonId")
                        ScenarioMedia.episodes.filter { season == null || it.seasonId == season }
                    }
                    path.endsWith("/Similar") -> ScenarioMedia.movies
                    path == "/Items" -> listOf(ScenarioMedia.series) + ScenarioMedia.movies
                    else -> emptyList()
                }
                if (path == "/Items/Latest") AppJson.encodeToString(rows)
                else AppJson.encodeToString(ItemsResponse(rows, rows.size))
            }
        }
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (failed) 503 else 200).message(if (failed) "UI scenario failure" else "OK")
            .body(payload.toResponseBody("application/json".toMediaType())).build()
    }
}

internal object ScenarioMedia {
    val series = ItemDto("series", "群山之间：漫长旅途与归来的故事（演示）", "Series",
        overview = "这是一部用于界面检查的演示作品。旅人穿越群山与海港，在漫长旅途中寻找回家的方向。长标题、完整简介与多季剧集用于检查布局和遥控焦点。",
        year = 2025, genres = listOf("剧情", "冒险"), rating = 8.6, childCount = 2,
        people = listOf(PersonDto("person", "演示演员", "旅人", "Actor")))
    val library = ItemDto("library", "影片与剧集（演示）", "CollectionFolder", collectionType = "tvshows")
    val seasons = (1..2).map { ItemDto("season-$it", "第 $it 季", "Season", seriesId = series.id, season = it, episode = it, childCount = 12) }
    val episodes = (1..2).flatMap { season -> (1..12).map { episode ->
        ItemDto("episode-$season-$episode", if (episode == 4) "一封很长的信：穿越群山与海港，直到我们再次相见" else "旅途的第 $episode 站", "Episode",
            overview = series.overview, seriesId = series.id, seriesName = series.name,
            seasonId = "season-$season", season = season, episode = episode, ticks = 14_400_000_000,
            userData = UserDataDto(positionTicks = if (episode == 1) 3_600_000_000 else 0))
    } }
    val movies = (1..8).map { ItemDto("movie-$it", "海港来信 $it（演示）", "Movie", overview = series.overview, year = 2024, ticks = 54_000_000_000) }
    val all = listOf(series, library) + seasons + episodes + movies
}
