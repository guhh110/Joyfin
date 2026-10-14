package dev.joyfin.subtitles

/** Selected season is authoritative; numeric columns are compared within naming families. */
internal object SubtitleEpisodeMatcher {
    private val seasonEpisode = Regex("(?i)(?<![\\p{L}\\p{N}])S\\d{1,3}[ ._-]*E(\\d{1,3})(?!\\d)")
    private val episodeToken = Regex("(?i)(?<![\\p{L}\\p{N}])(?:EPISODE|EP|E)[ ._-]*(\\d{1,3})(?!\\d)")
    private val chinese = Regex("第?[ \\t]*(\\d{1,3})[ \\t]*[集话話]")
    private val number = Regex("(?<![\\p{L}\\p{N}])(\\d{1,3})(?![\\p{L}\\p{N}])")
    private val allNumbers = Regex("\\d+")
    private val continuation = Regex("(?i)^[ ._]*(?:(?:[-~～至到&+、,，][ ._]*(?:(?:EPISODE|EP|E)[ ._-]*)?)|(?:EPISODE|EP|E)[ ._-]*)\\d")
    private val rangePrefix = Regex("(?i)\\d[ ._]*[-~～至到&+、,，][ ._]*(?:(?:EPISODE|EP|E)[ ._-]*)?$")
    private val numericRange = Regex("\\d(?:[-~～至到&+、,，])\\d")

    private data class FileNumbers(val base: String, val columns: List<Int?>, val explicit: Int?, val blocked: Boolean)
    private data class Candidate(val values: List<Int?>, val score: Double)

    /** Results align with input indices, including duplicate episode files in different languages. */
    fun episodes(names: List<String>, seasonEpisodes: Set<Int>): List<Int?> {
        val files = names.map(::analyse)
        val results = files.map { it.explicit?.takeIf(seasonEpisodes::contains) }.toMutableList()
        val compared = BooleanArray(files.size)
        val families = files.indices.filter { !files[it].blocked }.groupBy { index ->
            // Constant text and numeric positions define a family; folders never contribute digits.
            allNumbers.replace(files[index].base.lowercase(java.util.Locale.ROOT), "#")
        }
        families.values.forEach { indices ->
            if (indices.size >= 2) indices.forEach { compared[it] = true }
            val count = indices.maxOfOrNull { files[it].columns.size } ?: 0
            val candidates = (0 until count).mapNotNull { column ->
                val values = indices.map { files[it].columns.getOrNull(column) }
                val valid = values.filterNotNull().filter(seasonEpisodes::contains)
                val ordered = valid.distinct().sorted()
                if (ordered.size < 2 || valid.size.toDouble() / indices.size < .75) return@mapNotNull null
                val steps = ordered.zipWithNext().count { (a, b) -> b - a == 1 }
                if (steps == 0 || steps.toDouble() / (ordered.size - 1) < .5) return@mapNotNull null
                val labelled = indices.indices.filter { files[indices[it]].explicit != null }
                // A numeric column contradicting an explicit episode marker cannot win.
                if (labelled.any { values[it] != files[indices[it]].explicit }) return@mapNotNull null
                val coverage = valid.size.toDouble() / indices.size
                val progression = steps.toDouble() / (ordered.size - 1)
                val density = ordered.size.toDouble() / (ordered.last() - ordered.first() + 1)
                val seasonCoverage = ordered.size.toDouble() / seasonEpisodes.size.coerceAtLeast(1)
                Candidate(values, 40 * coverage + 30 * progression + 20 * density + 10 * seasonCoverage + if (labelled.isEmpty()) 0 else 20)
            }.distinctBy { it.values }.sortedByDescending { it.score }
            val best = candidates.firstOrNull()
            val runnerUp = candidates.getOrNull(1)
            if (best != null && (runnerUp == null || best.score - runnerUp.score >= 10)) {
                indices.forEachIndexed { position, index ->
                    if (files[index].explicit == null) results[index] = best.values[position]?.takeIf(seasonEpisodes::contains)
                }
            }
        }
        // Small or irregular packages retain the conservative single-file fallback.
        files.forEachIndexed { index, file ->
            if (results[index] == null && !compared[index] && !file.blocked && file.explicit == null) {
                results[index] = number.findAll(file.base).mapNotNull { it.value.toIntOrNull() }.distinct().singleOrNull()?.takeIf(seasonEpisodes::contains)
            }
        }
        return results
    }

    private fun analyse(name: String): FileNumbers {
        val base = name.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
        val marked = (seasonEpisode.findAll(base) + episodeToken.findAll(base) + chinese.findAll(base)).toList()
        val values = marked.mapNotNull { it.groupValues[1].toIntOrNull() }.distinct()
        val blocked = if (marked.isEmpty()) numericRange.containsMatchIn(base) else values.size != 1 || marked.any {
            continuation.containsMatchIn(base.substring(it.range.last + 1)) || rangePrefix.containsMatchIn(base.substring(0, it.range.first))
        }
        return FileNumbers(base, allNumbers.findAll(base).map { it.value.toIntOrNull() }.toList(), values.singleOrNull().takeUnless { blocked }, blocked)
    }
}
