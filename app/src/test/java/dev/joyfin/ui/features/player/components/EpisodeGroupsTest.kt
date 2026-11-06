package dev.joyfin.ui.features.player.components

import dev.joyfin.data.Media
import org.junit.Assert.*
import org.junit.Test

class EpisodeGroupsTest {
    private fun episodes(count: Int) = (1..count).map { Media("e$it", "Episode", "Episode", episode = it) }

    @Test fun groupsAppearOnlyAboveFiftyEpisodesAndKeepTheLastPartialRange() {
        for (count in listOf(0, 1, 49, 50)) assertTrue(episodeGroups(episodes(count)).isEmpty())
        assertEquals(listOf(EpisodeGroup(0, 50, "1–50 集"), EpisodeGroup(50, 51, "51 集")), episodeGroups(episodes(51)))
        assertEquals(EpisodeGroup(100, 123, "101–123 集"), episodeGroups(episodes(123)).last())
        assertEquals(20, episodeGroups(episodes(1000)).size)
    }

    @Test fun rangesFollowServerOrderAndDisplayActualNumbersForSpecialsAndMissingEpisodes() {
        val rows = episodes(51).mapIndexed { i, row -> row.copy(episode = i * 2 + 101) }
        assertEquals("101–199 集", episodeGroups(rows).first().label)
        assertEquals(50, episodeGroups(rows).last().start)
    }
}
