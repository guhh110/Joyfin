package dev.joyfin.scenario

import android.util.Log
import dev.joyfin.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Synthetic payload comparison. Does not measure a real server or compressed HTTP bytes. */
internal object ScenarioPayloadProfile {
    private val people = (1..24).map { PersonDto("actor-$it", "演示演员 $it", "角色 $it", "Actor") }
    val fullRows by lazy {
        ScenarioMedia.stressMovies.map { it.copy(
            overview = "旅人沿海岸寻找归途，途经群山与海港。".repeat(60),
            people = people, locations = listOf("演示地区"), originalTitle = "Harbor Letters",
            providerIds = mapOf("demo" to it.id), path = "/demo/library/${it.id}.mkv",
            genres = listOf("剧情"),
            sources = listOf(MediaSource(id = "source-${it.id}", container = "mkv",
                streams = listOf(MediaStream(type = "Video", width = 3840, height = 2160)))),
        ) }
    }

    fun project(row: ItemDto, fields: Set<String>): ItemDto = row.copy(
        overview = row.overview.takeIf { "Overview" in fields }.orEmpty(),
        people = row.people.takeIf { "People" in fields }.orEmpty(),
        locations = row.locations.takeIf { "ProductionLocations" in fields }.orEmpty(),
        originalTitle = row.originalTitle.takeIf { "OriginalTitle" in fields }.orEmpty(),
        providerIds = row.providerIds.takeIf { "ProviderIds" in fields }.orEmpty(),
        path = row.path.takeIf { "Path" in fields }.orEmpty(),
    )

    fun run() {
        val variants = listOf(
            "full" to fullRows.take(40),
            "browse" to fullRows.take(40).map { project(it, setOf("Genres", "MediaSources", "ChildCount", "SeasonUserData")) },
            "home" to fullRows.take(40).map { project(it, setOf("Genres", "MediaSources", "ChildCount", "SeasonUserData", "Overview", "OriginalTitle")) },
        )
        val payloads = variants.map { (name, rows) -> name to AppJson.encodeToString(ItemsResponse(rows, 160)) }
        repeat(6) { payloads.forEach { (_, text) -> AppJson.decodeFromString<ItemsResponse>(text).toMediaPage() } }
        repeat(3) { round ->
            payloads.forEach { (name, text) ->
                val samples = List(20) {
                    val start = System.nanoTime()
                    val page = AppJson.decodeFromString<ItemsResponse>(text).toMediaPage()
                    check(page.items.size == 40 && page.items.first().sources.first().streams.first().width == 3840)
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
                Log.i("JoyfinPayloadProfile", "variant=$name round=$round bytes=${text.toByteArray().size} medianMs=${samples[10]} p95Ms=${samples[19]}")
            }
        }
    }
}
