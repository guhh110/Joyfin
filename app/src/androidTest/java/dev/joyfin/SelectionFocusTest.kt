package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.data.Media
import dev.joyfin.playback.*
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.components.SeasonPicker
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionFocusTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val seasons = (1..30).map { Media("s$it", "第 $it 季", "Season") }
    private val episodes = (1..30).map { Media("e$it", "第 $it 集", "Episode", episode = it) }
    private val actions =
        PlayerActions(
            toggle = {},
            preview = SeekPreviewActions({}, {}, {}, {}, {}, {}),
            seek = {},
            selectEpisode = {},
            selectSeason = {},
            selectAudio = {},
            selectSubtitle = {},
            selectBitrate = {},
            speed = {},
            selectVersion = {},
            compatible = {},
            selectCompatibility = {},
            retry = {},
            cancelNext = {},
            nextNow = {},
            close = {},
        )

    private fun mount(content: @Composable () -> Unit): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(true, 960)
                        ) {
                            content()
                        }
                    }
                }
            }
        }

    @Test
    fun allPlayerSelectorsOpenOnCurrentValueAndScrollItIntoView() {
        var panel by mutableStateOf(PlayerPanel.Quality)
        var open by mutableStateOf(true)
        val ui =
            PlayerUiState(
                bitrate = 4_000_000,
                speed = 1.5f,
                audio = listOf(TrackChoice(2, "国语", ""), TrackChoice(7, "日语", "")),
                audioIndex = 7,
                subtitles = listOf(TrackChoice(3, "简体", ""), TrackChoice(9, "繁体", "")),
                subtitleIndex = 9,
                seasons = seasons,
                selectedSeasonId = "s30",
                episodes = episodes,
                itemId = "e30",
                versions = listOf(VersionChoice("v1", "版本一"), VersionChoice("v2", "版本二")),
                sourceId = "v2",
            )
        val scenario = mount {
            if (open) PlayerSettingsSheet(panel, ui, actions, { panel = it }, { open = false })
        }
        try {
            for ((next, value) in
                listOf(
                    PlayerPanel.Quality to "4000000",
                    PlayerPanel.Audio to "7",
                    PlayerPanel.Subtitle to "9",
                    PlayerPanel.Speed to "1.5",
                    PlayerPanel.Episodes to "e30",
                    PlayerPanel.Seasons to "s30",
                    PlayerPanel.Versions to "v2",
                )) {
                compose.runOnIdle { panel = next }
                compose.onNodeWithTag(next.choiceKey(value)).assertIsFocused().assertIsDisplayed()
            }
            compose.runOnIdle { open = false }
            compose.waitForIdle()
            compose.runOnIdle { open = true }
            compose.onNodeWithTag(PlayerPanel.Versions.choiceKey("v2")).assertIsFocused()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun currentEpisodeReceivesFocusWhenListArrivesWithoutStealingFocusOnProgressUpdates() {
        var ui by mutableStateOf(PlayerUiState(itemId = "e30"))
        val scenario = mount { PlayerSettingsSheet(PlayerPanel.Episodes, ui, actions, {}, {}) }
        try {
            compose.onNodeWithTag("player:episode-season").assertIsFocused()
            compose.runOnIdle { ui = ui.copy(episodes = episodes) }
            compose
                .onNodeWithTag(PlayerPanel.Episodes.choiceKey("e30"))
                .assertIsFocused()
                .assertIsDisplayed()
            val previous = compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e29"))
            previous.performSemanticsAction(SemanticsActions.RequestFocus)
            previous.assertIsFocused()
            compose.runOnIdle { ui = ui.copy(positionMs = 1234) }
            previous.assertIsFocused()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun detailSeasonPickerFocusesCurrentSeasonAndHandlesMissingOrEmptySelection() {
        var selected by mutableStateOf<String?>("s30")
        var available by mutableStateOf(seasons)
        val scenario = mount { SeasonPicker(available, selected, {}, {}) }
        try {
            compose.onNodeWithTag("season-choice:s30").assertIsFocused().assertIsDisplayed()
            compose.runOnIdle { selected = "removed" }
            compose.onNodeWithTag("season-choice:s1").assertIsFocused().assertIsDisplayed()
            compose.runOnIdle { available = emptyList() }
            compose.onNodeWithTag("sheet:close").assertIsFocused()
        } finally {
            scenario.close()
        }
    }
}
