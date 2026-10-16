package dev.joyfin.ui.features.player.components

import dev.joyfin.playback.PlayerUiState

internal val qualityChoices =
    listOf(
        dev.joyfin.data.PlaybackQuality.ORIGINAL to "原画",
        80_000_000 to "高码率",
        20_000_000 to "高清优先",
        8_000_000 to "更稳定",
        4_000_000 to "节省流量",
        1_500_000 to "低带宽",
    )
internal val speedChoices = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)

internal fun PlayerPanel.choiceKey(value: Any) = "player-choice:$name:$value"

/** Only resolve to a row that exists; a missing selection falls back to the first choice. */
internal fun PlayerPanel.initialFocus(ui: PlayerUiState): String =
    when (this) {
        PlayerPanel.Settings -> "sheet:close"
        PlayerPanel.Diagnostics -> "diagnostics:copy"
        PlayerPanel.AudioOutput -> choiceKey(ui.audioOutputMode.name)
        PlayerPanel.Quality ->
            choiceKey(
                qualityChoices.firstOrNull { it.first == ui.bitrate }?.first
                    ?: qualityChoices.first().first
            )
        PlayerPanel.Speed ->
            choiceKey(speedChoices.firstOrNull { it == ui.speed } ?: speedChoices.first())
        PlayerPanel.Audio ->
            (ui.audio.firstOrNull { it.index == ui.audioIndex } ?: ui.audio.firstOrNull())?.let {
                choiceKey(it.index)
            } ?: "sheet:close"
        PlayerPanel.Subtitle ->
            if (ui.localSubtitle != null) "player:local-subtitle"
            else choiceKey(ui.subtitles.firstOrNull { it.index == ui.subtitleIndex }?.index ?: -1)
        PlayerPanel.SubtitleOffset -> "player:offset-reset"
        PlayerPanel.Episodes ->
            (ui.episodes.firstOrNull { it.id == ui.itemId } ?: ui.episodes.firstOrNull())?.let {
                choiceKey(it.id)
            } ?: "player:episode-season"
        PlayerPanel.Seasons ->
            (ui.seasons.firstOrNull { it.id == ui.selectedSeasonId } ?: ui.seasons.firstOrNull())
                ?.let { choiceKey(it.id) } ?: "sheet:close"
        PlayerPanel.Versions ->
            (ui.versions.firstOrNull { it.id == ui.sourceId } ?: ui.versions.firstOrNull())?.let {
                choiceKey(it.id)
            } ?: "sheet:close"
    }
