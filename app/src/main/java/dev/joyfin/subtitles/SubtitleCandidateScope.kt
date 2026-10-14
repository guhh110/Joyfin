package dev.joyfin.subtitles

import dev.joyfin.data.Media
import dev.joyfin.data.subtitles.ManagedSubtitle
import dev.joyfin.data.subtitles.ManagedSubtitleFile

/** Inference always sees the complete source package, before deduplication or episode filtering. */
internal class SubtitleCandidateScope(source: ManagedSubtitle, private val media: List<Media>) {
    private val inferred: Map<String, Int?> = buildMap {
        val episodes = SubtitleEpisodeMatcher.episodes(source.files.map { it.name }, media.map { it.episode }.toSet())
        source.files.forEachIndexed { index, file ->
            if (!containsKey(file.fileId)) put(file.fileId, SubtitleEpisodeMatcher.explicitEpisode(file.name) ?: episodes[index])
        }
    }

    /** Called only when explicitly changing source/filter; the returned order survives toggles. */
    fun candidates(
        draft: ManagedSubtitle,
        source: ManagedSubtitle,
        item: Media,
        versionId: String,
        showAll: Boolean,
    ): List<ManagedSubtitleFile> {
        val current = draft.files.distinctBy { it.fileId }.associateBy { it.fileId }
        val sameContext = source.workId == draft.workId && source.seasonId == draft.seasonId
        val associated = draft.files.filter { it.mapping?.itemId == item.id && it.mapping?.versionId == versionId }
            .distinctBy { it.fileId }
        val sourceIds = source.files.map { it.fileId }.toSet()
        val related = source.files.distinctBy { it.fileId }.filter { file ->
            val row = current[file.fileId] ?: file.takeIf { sameContext }
            val mapping = row?.mapping
            val mappedItem = mapping?.let { target -> media.firstOrNull { it.id == target.itemId } }
            val relatedToItem = when {
                mappedItem != null -> mappedItem.id == item.id
                row?.manualMapping == true && mapping != null -> true
                else -> inferred[file.fileId].let { it == null || it == item.episode }
            }
            showAll || item.type == "Movie" || associated.any { it.fileId == file.fileId } ||
                relatedToItem
        }.map { current[it.fileId] ?: it }
        return (associated.filter { it.fileId !in sourceIds } + related).distinctBy { it.fileId }
    }
}
