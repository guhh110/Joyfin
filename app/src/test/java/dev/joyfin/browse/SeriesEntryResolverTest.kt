package dev.joyfin.browse

import dev.joyfin.data.Media
import org.junit.Assert.*
import org.junit.Test

class SeriesEntryResolverTest {
    @Test
    fun seasonPreviewUsesParentIdentityAndDoesNotLeakSeasonMetadata() {
        val season =
            Media(
                "s2",
                "季 2",
                "Season",
                seriesId = "show",
                seriesName = "冰海战记",
                seriesImageTag = "poster",
                overview = "Season-only summary",
                year = 2023,
                rating = 9.0,
                ticks = 123L,
                resumeTicks = 42L,
                favorite = true,
                childCount = 24,
                backdropId = "show",
                backdropTag = "parent-backdrop",
            )
        val preview = requireNotNull(season.detailPreview())
        assertEquals("show", preview.id)
        assertEquals("冰海战记", preview.name)
        assertEquals("Series", preview.type)
        assertEquals("poster", preview.imageTag)
        assertEquals("parent-backdrop", preview.backdropTag)
        assertEquals("", preview.overview)
        assertEquals(0, preview.year)
        assertNull(preview.rating)
        assertEquals(0L, preview.ticks)
        assertEquals(0L, preview.resumeTicks)
        assertFalse(preview.favorite)
        assertEquals(0, preview.childCount)
    }

    @Test
    fun unknownParentDoesNotUseSeasonOrEpisodeIdentity() {
        for (type in listOf("Season", "Episode")) {
            val entry =
                Media(
                    "entry",
                    "Entry title",
                    type,
                    parentId = "unverified-parent",
                    seriesName = "Show",
                )
            assertNull(entry.detailPreview())
        }
    }

    @Test
    fun episodeNameAndArtworkNeverStandInForUnknownSeriesFields() {
        val episode =
            Media(
                "e1",
                "Episode title",
                "Episode",
                seriesId = "show",
                imageTag = "episode-still",
                backdropId = "e1",
                backdropTag = "episode-backdrop",
            )
        val preview = requireNotNull(episode.detailPreview())
        assertEquals("show", preview.id)
        assertEquals("", preview.name)
        assertEquals("show", preview.imageId)
        assertEquals("", preview.imageTag)
        assertEquals("show", preview.backdropId)
        assertEquals("", preview.backdropTag)
    }
}
