package dev.joyfin.audit

import dev.joyfin.data.AuditItemDto
import dev.joyfin.data.AuditSourceDto
import dev.joyfin.data.AuditStreamDto
import java.time.LocalDate
import java.util.Locale

/** Pure, evidence-based rules. Missing metadata never implies a damaged or absent file. */
object LibraryAuditRules {
    fun target(item: AuditItemDto) = AuditTarget(item.id,
        if (item.type == "Episode") "${item.seriesName.orEmpty()} · S${item.season ?: "?"}E${item.episode ?: "?"} · ${item.name.orEmpty()}"
        else item.name.orEmpty().ifBlank { "未命名作品" }, item.type.orEmpty(), item.seriesId, item.season, item.episode)

    fun item(item: AuditItemDto, local: (String, String) -> Boolean?): List<AuditFinding> = buildList {
        val target = target(item)
        val missing = buildList {
            if (item.name.isNullOrBlank()) add("名称未提供")
            if (item.type in setOf("Movie", "Series")) {
                if (item.overview.isNullOrBlank()) add("简介未提供")
                if ((item.year ?: 0) <= 0) add("年份未提供")
                if (item.images == null) add("海报信息未提供")
                else if (item.images["Primary"].isNullOrBlank()) add("未发现作品海报")
            }
            if (item.type == "Episode") {
                if (item.season == null || item.season < 0) add("季号未提供或无效")
                if (item.episode == null || item.episode < 0) add("集号未提供或无效")
                if (item.episodeEnd != null && item.episode != null && item.episodeEnd < item.episode)
                    add("合并集结束编号小于起始编号")
            }
        }
        if (missing.isNotEmpty()) add(AuditFinding("metadata", AuditCategory.METADATA, target,
            "作品资料不完整", missing, AuditConfidence.OBSERVED))
        if (item.type !in setOf("Movie", "Episode") || item.location == "Virtual" || item.placeholder == true) return@buildList
        val sources = item.sources
        if (sources.isNullOrEmpty()) {
            add(AuditFinding("sources-unknown", AuditCategory.METADATA, target, "媒体版本信息未提供",
                listOf("服务器未提供可检查的媒体版本，无法判断文件及字幕状态"), AuditConfidence.UNKNOWN))
            return@buildList
        }
        sources.forEachIndexed { index, source ->
            val id = source.id?.takeIf { it.isNotBlank() }
            val identity = id ?: "unknown-$index"
            val label = source.name?.takeIf { it.isNotBlank() } ?: "版本 ${index + 1}"
            val streams = source.streams ?: item.streams?.takeIf { sources.size == 1 }
            val technical = buildList {
                if (id == null) add("版本身份未提供，无法核对本机字幕关联")
                if ((source.ticks ?: item.ticks ?: 0) <= 0) add("片长未提供")
                if (source.container.isNullOrBlank()) add("容器信息未提供")
                if (streams == null) add("音视频轨信息未提供")
                else {
                    val video = streams.firstOrNull { it.type == "Video" }
                    if (video == null) add("未提供视频轨记录")
                    else {
                        if (video.codec.isNullOrBlank()) add("视频编码未提供")
                        if ((video.width ?: 0) <= 0 || (video.height ?: 0) <= 0) add("视频分辨率未提供")
                    }
                }
            }
            if (technical.isNotEmpty()) add(AuditFinding("technical", AuditCategory.METADATA, target,
                "$label · 技术信息不完整", technical, AuditConfidence.UNKNOWN, identity))
            val localAvailable = id?.let { local(item.id, it) }
            if (streams?.any { it.type == "Subtitle" } != true && localAvailable != true) {
                val known = streams != null && localAvailable == false
                add(AuditFinding("subtitles", AuditCategory.SUBTITLES, target,
                    "$label · ${if (known) "未发现可选字幕" else "字幕状态待确认"}",
                    listOf(if (streams == null) "服务器未提供完整轨道列表" else "已返回轨道中没有字幕记录",
                        if (localAvailable == false) "本机没有此作品、此版本的有效字幕关联" else "本机字幕关联未能核对",
                        "画面内自带的字幕无法通过轨道信息识别"),
                    if (known) AuditConfidence.OBSERVED else AuditConfidence.UNKNOWN, identity))
            }
        }
        if (sources.size > 1) {
            val summaries = sources.mapIndexed { index, source -> sourceSummary(source, index) }
            add(AuditFinding("multiple-versions", AuditCategory.VERSIONS, target, "存在 ${sources.size} 个媒体版本",
                summaries + "多版本可能是不同画质或剪辑，不代表重复文件", AuditConfidence.OBSERVED))
            val similar = sources.filter { signature(it) != null }.groupBy { signature(it) }.values.filter { it.size > 1 }
            if (similar.isNotEmpty()) add(AuditFinding("similar-versions", AuditCategory.VERSIONS, target,
                "部分版本格式与大小相同", summaries + "仅比较已提供的格式、大小和片长，未校验文件内容",
                AuditConfidence.REVIEW))
        }
    }

    private fun signature(source: AuditSourceDto): String? {
        val video = source.streams?.firstOrNull { it.type == "Video" } ?: return null
        if (source.container.isNullOrBlank() || (source.size ?: 0) <= 0 || (source.ticks ?: 0) <= 0 || video.codec.isNullOrBlank() ||
            (video.width ?: 0) <= 0 || (video.height ?: 0) <= 0) return null
        return listOf(source.container, source.size, source.ticks, video.codec, video.width, video.height).joinToString(":")
    }

    private fun sourceSummary(source: AuditSourceDto, index: Int): String {
        val video = source.streams?.firstOrNull { it.type == "Video" }
        return listOf(source.name?.takeIf { it.isNotBlank() } ?: "版本 ${index + 1}", source.container,
            video?.codec, video?.width?.let { "$it×${video.height ?: "?"}" },
            source.size?.takeIf { it > 0 }?.let { "${it / 1024 / 1024} MiB" }).filterNotNull().joinToString(" · ")
    }

    fun future(item: AuditItemDto, today: LocalDate): Boolean =
        runCatching { LocalDate.parse(item.premiere?.take(10)).isAfter(today) }.getOrDefault(false)

    private fun interval(item: AuditItemDto): IntRange? {
        val start = item.episode?.takeIf { it > 0 } ?: return null
        val end = item.episodeEnd ?: start
        return (start..end).takeIf { end >= start }
    }

    private fun date(item: AuditItemDto): LocalDate? = runCatching { LocalDate.parse(item.premiere?.take(10)) }.getOrNull()

    /** Interval subtraction handles merged episodes and huge numbering gaps without enumerating numbers. */
    private fun subtract(range: IntRange, blocks: List<IntRange>): List<IntRange> = buildList {
        var cursor = range.first.toLong()
        val end = range.last.toLong()
        blocks.sortedBy { it.first }.forEach { block ->
            if (block.last.toLong() >= cursor && block.first.toLong() <= end) {
                if (block.first.toLong() > cursor) add(cursor.toInt()..minOf(end, block.first.toLong() - 1).toInt())
                cursor = maxOf(cursor, block.last.toLong() + 1)
            }
        }
        if (cursor <= end) add(cursor.toInt()..end.toInt())
    }

    fun episodes(series: AuditItemDto, actual: List<AuditItemDto>, missing: List<AuditItemDto>, today: LocalDate): List<AuditFinding> = buildList {
        val real = actual.filter { it.location != "Virtual" && it.placeholder != true }
        val coverage = real.filter { it.season != null && it.season >= 0 }.groupBy { it.season!! }
        val futureRanges = missing.filter { future(it, today) }.groupBy { it.season }
            .mapValues { (_, episodes) -> episodes.mapNotNull(::interval) }
        val serverMissing = mutableMapOf<Int, MutableList<IntRange>>()
        missing.filterNot { future(it, today) }.forEach { episode ->
            val season = episode.season ?: return@forEach
            if (season < 0) return@forEach
            val range = interval(episode) ?: return@forEach
            val alreadyPresent = coverage[season].orEmpty().mapNotNull(::interval)
            val uncovered = subtract(range, alreadyPresent)
            uncovered.forEach { absent ->
                val previous = serverMissing.getOrPut(season) { mutableListOf() }
                subtract(absent, previous).forEach { part ->
                    val label = "S${season}E${part.first}" + if (part.last > part.first) "–E${part.last}" else ""
                    add(AuditFinding("server-missing:$season:${part.first}:${part.last}", AuditCategory.EPISODES, target(series).copy(season = season),
                        "$label · 服务器标记缺失", listOf("来自服务器缺失集查询", episode.name.orEmpty(),
                            date(episode)?.let { "播出日期：$it" } ?: "播出日期未提供或无效，请人工确认"),
                        if (date(episode) == null) AuditConfidence.REVIEW else AuditConfidence.SERVER))
                }
                previous += absent
            }
        }
        coverage.filterKeys { it > 0 }.toSortedMap().forEach { (season, entries) ->
            val ranges = entries.mapNotNull(::interval).sortedBy { it.first }
            var end = ranges.firstOrNull()?.last ?: return@forEach
            ranges.drop(1).forEach { next ->
                if (next.first.toLong() > end.toLong() + 1) {
                    subtract((end + 1)..(next.first - 1), serverMissing[season].orEmpty() + futureRanges[season].orEmpty()).forEach { gap ->
                        val label = "E${gap.first}" + if (gap.last > gap.first) "–E${gap.last}" else ""
                        add(AuditFinding("gap:$season:${gap.first}:${gap.last}", AuditCategory.EPISODES, target(series).copy(season = season),
                            "第 $season 季 · 疑似缺少 $label",
                            listOf("当前收录的 E$end 与 E${next.first} 之间存在编号缺口", "已考虑合并集覆盖范围",
                                "仅依据当前账号可见的收录情况；分季方式和编号可能需要人工确认"), AuditConfidence.REVIEW))
                    }
                }
                end = maxOf(end, next.last)
            }
        }
    }

    private fun providerKeys(item: AuditItemDto): Set<String> = item.providers.orEmpty().mapNotNull { (provider, id) ->
        id?.takeIf { it.isNotBlank() }?.let { "${item.type}:${provider.lowercase(Locale.ROOT)}:${it.lowercase(Locale.ROOT)}" }
    }.toSet()

    fun duplicates(items: List<AuditItemDto>): List<AuditFinding> {
        val unique = items.distinctBy { it.id }
        val parents = unique.indices.toMutableList()
        fun root(index: Int): Int {
            var node = index
            while (parents[node] != node) { parents[node] = parents[parents[node]]; node = parents[node] }
            return node
        }
        val identities = mutableMapOf<String, Int>()
        val series = unique.filter { it.type == "Series" }.associateBy { it.id }
        unique.forEachIndexed { index, item ->
            val keys = when {
                item.type == "Episode" && item.season != null && item.season >= 0 && interval(item) != null -> {
                    val parent = series[item.seriesId]
                    val seriesKeys = parent?.let(::providerKeys).orEmpty().ifEmpty { setOf("series:${item.seriesId ?: item.id}") }
                    seriesKeys.map { "$it:S${item.season}:E${item.episode}-${item.episodeEnd ?: item.episode}" }.toSet()
                }
                else -> providerKeys(item)
            }
            val effective = keys.ifEmpty {
                if (item.type in setOf("Movie", "Series") && !item.name.isNullOrBlank() && (item.year ?: 0) > 0)
                    setOf("name:${item.type}:${item.name.trim().lowercase(Locale.ROOT)}:${item.year}") else emptySet()
            }
            effective.forEach { key -> identities.putIfAbsent(key, index)?.let { parents[root(index)] = root(it) } }
        }
        return unique.indices.groupBy(::root).values.filter { it.size > 1 }.map { group ->
            val members = group.map { unique[it] }.sortedBy { it.id }
            val first = members.first()
            AuditFinding("duplicate-entries", AuditCategory.VERSIONS, target(first), "存在 ${members.size} 条相同作品或编号的收录",
                members.map { target(it).name } + "依据作品标识、同剧季集编号或无标识时的同名同年份归组；不代表文件内容相同",
                AuditConfidence.REVIEW, relatedIds = members.map { it.id })
        }
    }
}
