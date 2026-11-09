package dev.joyfin.scenario

import dev.joyfin.data.ItemDto
import dev.joyfin.data.UserDataDto

/** Repeatable tracking examples; restarting the preview restores the original favorites. */
internal object ScenarioTrackingMedia {
    private fun show(id: String, name: String, overview: String) = ItemDto(
        id, "$name（演示）", "Series", overview = overview, year = 2026,
    )

    val series = listOf(
        show("tracking-resume", "灯塔值班室", "半集续播：第 1 集已看完，第 2 集看到一半，还有 3 集未看。"),
        show("tracking-next-season", "星轨之旅", "跨季续播：第 1 季已经看完，下一集应为第 2 季第 1 集。"),
        show("tracking-waiting", "周末去海边", "尚未开看：3 集正片和 1 集特别篇，默认从正片开始。"),
        show("tracking-caught", "港湾日记", "已追平：全部 4 集正片都已看完，等待后续入库。"),
        show("tracking-skipped", "雨夜来信", "旧集欠账：跳过第 2 集，但已经看完最新的第 4 集，仍应显示已追平。"),
        show("tracking-unavailable", "即将抵达", "只有虚拟待入库剧集，应显示暂无可播放剧集。"),
    )

    private fun episode(showIndex: Int, season: Int, number: Int, data: UserDataDto = UserDataDto(), virtual: Boolean = false): ItemDto {
        val show = series[showIndex]
        return ItemDto(
            "${show.id}-$season-$number", if (season == 0) "海边特别篇" else "第 $number 站", "Episode",
            overview = show.overview, seriesId = show.id, seriesName = show.name,
            seasonId = "${show.id}-season-$season", season = season, episode = number,
            ticks = 14_400_000_000, userData = data, virtual = virtual,
        )
    }

    val episodes = buildList {
        (1..4).forEach { number ->
            add(episode(0, 1, number, when (number) {
                1 -> UserDataDto(played = true, lastPlayedDate = "2026-11-08T11:00:00Z")
                2 -> UserDataDto(positionTicks = 7_200_000_000, lastPlayedDate = "2026-11-09T01:00:00Z")
                else -> UserDataDto()
            }))
        }
        (1..2).forEach { season -> (1..3).forEach { number ->
            add(episode(1, season, number, if (season == 1) UserDataDto(
                played = true, lastPlayedDate = "2026-11-08T10:0${number}:00Z",
            ) else UserDataDto()))
        } }
        add(episode(2, 0, 1))
        (1..3).forEach { add(episode(2, 1, it)) }
        (1..4).forEach { number ->
            add(episode(3, 1, number, UserDataDto(played = true, lastPlayedDate = "2026-11-07T10:0${number}:00Z")))
            add(episode(4, 1, number, if (number == 2) UserDataDto() else
                UserDataDto(played = true, lastPlayedDate = "2026-11-06T10:0${number}:00Z")))
        }
        add(episode(5, 1, 1, virtual = true))
    }

    val seasons = episodes.distinctBy { it.seasonId }.map { episode ->
        ItemDto(episode.seasonId, if (episode.season == 0) "特别篇" else "第 ${episode.season} 季", "Season",
            seriesId = episode.seriesId, season = episode.season, episode = episode.season,
            childCount = episodes.count { it.seasonId == episode.seasonId })
    }
}
