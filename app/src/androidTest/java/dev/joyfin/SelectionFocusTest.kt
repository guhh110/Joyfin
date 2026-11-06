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

    private fun mount(television: Boolean = true, content: @Composable () -> Unit): ActivityScenario<MainActivity> {
        if (television) androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressDPadUp()
        return ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(television, 960)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun episodeGroupsAppearOnlyAboveFiftyAndJumpWithoutPlayingOnTv() {
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressDPadUp()
        var ui by mutableStateOf(PlayerUiState(episodes = episodes(50), itemId = "e30", selectedSeasonId = "s1"))
        var picked = 0
        val scenario = mount { PlayerSettingsSheet(PlayerPanel.Episodes, ui,
            actions.copy(selectEpisode = { picked++ }), {}, {}) }
        try {
            compose.onNodeWithTag("player:episode-groups").assertDoesNotExist()
            compose.runOnIdle { ui = ui.copy(episodes = episodes(51)) }
            compose.onNodeWithTag("player:episode-groups").assertIsDisplayed().performClick()
            compose.onNodeWithTag("player:episode-group:1").assertIsDisplayed().performClick()
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e51")).assertIsDisplayed().assertIsFocused()
            compose.runOnIdle { org.junit.Assert.assertEquals(0, picked) }
            compose.onNodeWithTag("player:episode-groups").performClick()
            compose.onNodeWithTag("player:episode-groups-back").performClick()
            compose.onNodeWithTag("player:episode-groups").assertIsFocused()
            compose.onNodeWithTag("player:episode-list").performScrollToNode(hasTestTag(PlayerPanel.Episodes.choiceKey("e49")))
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e49")).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.runOnIdle { ui = ui.copy(episodes = ui.episodes.map { it.copy(played = true) }) }
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e49")).assertIsFocused()
        } finally { scenario.close() }
    }

    private fun episodes(count: Int) = (1..count).map { Media("e$it", "第 $it 集", "Episode", episode = it) }

    @Test
    fun phoneGroupNavigationAndLoadingErrorRetryStatesAreDistinct() {
        var ui by mutableStateOf(PlayerUiState(episodesLoading = true, selectedSeasonId = "s1"))
        var retries = 0
        val scenario = mount(television = false) { PlayerSettingsSheet(PlayerPanel.Episodes, ui,
            actions.copy(retryEpisodes = { retries++ }), {}, {}) }
        try {
            compose.onNodeWithText("正在读取剧集…").assertIsDisplayed()
            compose.onNodeWithText("本季暂无可选剧集").assertDoesNotExist()
            compose.runOnIdle { ui = ui.copy(episodesLoading = false, episodeError = "网络连接失败") }
            compose.onNodeWithText("网络连接失败").assertIsDisplayed()
            compose.onNodeWithText("重试加载").performClick()
            compose.runOnIdle {
                org.junit.Assert.assertEquals(1, retries)
                ui = ui.copy(episodeError = null)
            }
            compose.onNodeWithText("本季暂无可选剧集").assertIsDisplayed()
            compose.runOnIdle { ui = ui.copy(episodes = episodes(123)) }
            compose.onNodeWithTag("player:episode-groups").performClick()
            compose.onNodeWithTag("player:episode-group:2").performClick()
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e101")).assertIsDisplayed().performClick()
        } finally { scenario.close() }
    }

    @Test
    fun thousandEpisodeTvListComposesOnlyViewportAndKeepsFocusWhileScrolling() {
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressDPadUp()
        val longEpisodes = (1..1000).map { Media("e$it", "第 $it 集", "Episode", episode = it) }
        var ui by mutableStateOf(PlayerUiState(episodes = longEpisodes, itemId = "e950", selectedSeasonId = "s1"))
        var picked: Media? = null
        val scenario = mount { PlayerSettingsSheet(PlayerPanel.Episodes, ui,
            actions.copy(selectEpisode = { picked = it }), {}, {}) }
        try {
            val current = compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e950"))
            current.assertIsDisplayed().assertIsFocused()
            val episodeRows = compose.onAllNodes(SemanticsMatcher("episode row") { node ->
                node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                    .startsWith("player-choice:Episodes:")
            }).fetchSemanticsNodes()
            org.junit.Assert.assertTrue("Only the viewport should be composed, got ${episodeRows.size}",
                episodeRows.size in 1..30)
            repeat(12) {
                compose.onNode(isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
            }
            val later = compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e962"))
            later.assertIsFocused().assertIsDisplayed()
            compose.runOnIdle { ui = ui.copy(positionMs = 1234) }
            later.assertIsFocused().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            compose.runOnIdle { org.junit.Assert.assertEquals("e962", picked?.id) }
            compose.onNodeWithTag("player:episode-season").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun thousandEpisodePhoneListOpensAtCurrentEpisodeAndCanChooseAfterScrolling() {
        val longEpisodes = (1..1000).map { Media("e$it", "第 $it 集", "Episode", episode = it) }
        val ui = PlayerUiState(episodes = longEpisodes, itemId = "e950", selectedSeasonId = "s1")
        var picked: Media? = null
        val scenario = mount(television = false) { PlayerSettingsSheet(PlayerPanel.Episodes, ui,
            actions.copy(selectEpisode = { picked = it }), {}, {}) }
        try {
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e950")).assertIsDisplayed()
            compose.onNodeWithTag("player:episode-list").performScrollToNode(hasTestTag(PlayerPanel.Episodes.choiceKey("e980")))
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e980")).assertIsDisplayed().performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals("e980", picked?.id) }
            compose.onNodeWithTag("player:episode-season").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun switchingSeasonResetsLongListAndLateRowsDoNotStealDeliberatelyMovedFocus() {
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressDPadUp()
        val longEpisodes = (1..1000).map { Media("e$it", "第 $it 集", "Episode", episode = it) }
        var ui by mutableStateOf(PlayerUiState(episodes = longEpisodes, itemId = "e950", selectedSeasonId = "s1"))
        val scenario = mount { PlayerSettingsSheet(PlayerPanel.Episodes, ui, actions, {}, {}) }
        try {
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("e950")).assertIsDisplayed().assertIsFocused()
            compose.runOnIdle { ui = ui.copy(selectedSeasonId = "s2", episodes = emptyList()) }
            compose.onNodeWithTag("player:episode-season").assertIsFocused()
            val secondSeason = listOf(Media("s2e1", "新的季", "Episode", season = 2, episode = 1))
            compose.runOnIdle { ui = ui.copy(episodes = secondSeason) }
            compose.onNodeWithTag(PlayerPanel.Episodes.choiceKey("s2e1")).assertIsDisplayed().assertIsFocused()
            compose.runOnIdle { ui = ui.copy(selectedSeasonId = "s1", episodes = emptyList()) }
            compose.onNodeWithTag("player:episode-season").assertIsFocused()
            compose.onNodeWithTag("sheet:close").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.runOnIdle { ui = ui.copy(episodes = longEpisodes) }
            compose.onNodeWithTag("sheet:close").assertIsFocused()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun repeatedSkipButtonConfirmationsAccumulateAndAllowDirectionChanges() {
        var ui by
            mutableStateOf(PlayerUiState(canSeek = true, positionMs = 50_000, durationMs = 500_000))
        var commits = 0
        val preview =
            SeekPreviewActions(
                { delta ->
                    val target = (ui.seekPreview?.targetMs ?: ui.positionMs) + delta
                    ui = ui.copy(seekPreview = SeekPreviewState(50_000, target, SeekInput.REMOTE))
                },
                {},
                {},
                {},
                { commits++ },
                {},
            )
        val scenario = mount {
            dev.joyfin.ui.features.player.PlayerRoute(ui, actions.copy(preview = preview))
        }
        try {
            val forward = compose.onNodeWithTag("player:forward")
            forward.performSemanticsAction(SemanticsActions.RequestFocus)
            repeat(3) {
                forward.performKeyInput {
                    pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)
                }
                forward.assertIsFocused()
            }
            compose.runOnIdle {
                org.junit.Assert.assertEquals(80_000L, ui.seekPreview?.targetMs)
                org.junit.Assert.assertEquals(0, commits)
            }
            forward.performKeyInput {
                pressKey(androidx.compose.ui.input.key.Key.DirectionLeft)
                pressKey(androidx.compose.ui.input.key.Key.DirectionLeft)
            }
            val rewind = compose.onNodeWithTag("player:rewind")
            rewind.assertIsFocused()
            repeat(2) {
                rewind.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
                rewind.assertIsFocused()
            }
            compose.runOnIdle {
                org.junit.Assert.assertEquals(60_000L, ui.seekPreview?.targetMs)
                org.junit.Assert.assertEquals(0, commits)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun nextEpisodePopupStartsOnConfirmAndKeepsDirectionalFocusInside() {
        var ui by
            mutableStateOf(
                PlayerUiState(nextSeconds = 10, nextItem = Media("next", "下一集", "Episode"))
            )
        var confirmed = 0
        var cancelled = 0
        val scenario = mount {
            dev.joyfin.ui.features.player.PlayerRoute(
                ui,
                actions.copy(nextNow = { confirmed++ }, cancelNext = { cancelled++ }),
            )
        }
        try {
            val confirm = compose.onNodeWithTag("player:confirm-next")
            val cancel = compose.onNodeWithTag("player:cancel-next")
            confirm.assertIsDisplayed().assertIsFocused()
            for (key in
                listOf(
                    androidx.compose.ui.input.key.Key.DirectionLeft,
                    androidx.compose.ui.input.key.Key.DirectionUp,
                    androidx.compose.ui.input.key.Key.DirectionDown,
                )) {
                confirm.performKeyInput { pressKey(key) }
                confirm.assertIsFocused()
            }
            confirm.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
            cancel.assertIsFocused()
            compose.runOnIdle { ui = ui.copy(nextSeconds = 9) }
            cancel.assertIsFocused()
            for (key in
                listOf(
                    androidx.compose.ui.input.key.Key.DirectionRight,
                    androidx.compose.ui.input.key.Key.DirectionUp,
                    androidx.compose.ui.input.key.Key.DirectionDown,
                )) {
                cancel.performKeyInput { pressKey(key) }
                cancel.assertIsFocused()
            }
            cancel.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
            confirm.assertIsFocused().performKeyInput {
                pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)
            }
            compose.runOnIdle {
                org.junit.Assert.assertEquals(1, confirmed)
                org.junit.Assert.assertEquals(0, cancelled)
            }
            cancel.performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals(1, cancelled) }
        } finally {
            scenario.close()
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
