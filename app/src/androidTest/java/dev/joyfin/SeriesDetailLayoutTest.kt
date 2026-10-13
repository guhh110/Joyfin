package dev.joyfin

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.joyfin.browse.*
import dev.joyfin.data.Media
import dev.joyfin.ui.components.MediaRail
import dev.joyfin.ui.device.DeviceLayout
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.detail.DetailRoute
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.model.BrowseActions
import dev.joyfin.ui.theme.BoundlessTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesDetailLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val images = ArtworkSource({ "" }, { "" }, { null }, { null }, "")
    private val episodes =
        (1..80).map {
            Media(
                "e$it",
                "Episode $it",
                "Episode",
                seriesId = "show",
                seasonId = "s1",
                season = 1,
                episode = it,
                overview = "本集简介。".repeat(300),
            )
        }

    private fun ready(specific: Boolean = false) =
        DetailUiState(
            ready = true,
            visit = 1,
            entryType = if (specific) "Episode" else "Series",
            item = Media("show", "Show", "Series", overview = "整剧简介"),
            series =
                SeriesDetailState(
                    "show",
                    entrySeasonId = if (specific) "s1" else null,
                    entryEpisodeId = if (specific) "e4" else null,
                    seasons =
                        SeriesResource.Ready(
                            listOf(Media("s1", "第 1 季", "Season"), Media("s2", "第 2 季", "Season"))
                        ),
                    seasonId = "s1",
                    episodes = SeriesResource.Ready(episodes),
                    inspectedEpisodeId = if (specific) "e4" else "e1",
                    recommendation = SeriesResource.Ready(episodes[3]),
                ),
        )

    private fun mount(
        state: State<DetailUiState>,
        tv: Boolean,
        actions: BrowseActions,
        viewportHeight: State<Dp>? = null,
    ): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                BoundlessTheme {
                    CompositionLocalProvider(
                        LocalDeviceLayout provides DeviceLayout(tv, if (tv) 960 else 360)
                    ) {
                        if (viewportHeight == null) DetailRoute(state.value, actions, images)
                        else
                            Box(Modifier.fillMaxWidth().height(viewportHeight.value)) {
                                DetailRoute(state.value, actions, images)
                            }
                    }
                }
            }
        }
        return scenario
    }

    private fun assertEpisodeActionsFit() {
        val panel = compose.onNodeWithTag("episode:sheet").getUnclippedBoundsInRoot()
        val buttons =
            listOf("episode:sheet-play", "episode:sheet-start", "episode:sheet-media").map {
                compose.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot()
            }
        buttons.forEach {
            assertTrue(it.top >= panel.top && it.bottom <= panel.bottom)
            assertEquals(buttons.first().top, it.top)
        }
    }

    @Test
    fun ordinaryTvEntryHoldsHeroWhenMovingToPlayAndFavorite() {
        val state = mutableStateOf(ready())
        val scenario = mount(state, true, emptyActions())
        try {
            compose.onNodeWithTag("series:entry").assertIsFocused()
            compose.onNodeWithTag("header:back").assertIsNotFocused()
            compose.onNodeWithTag("detail:play").assertIsNotFocused()
            val hero = compose.onNodeWithTag("detail:hero").getUnclippedBoundsInRoot()
            compose.onNodeWithTag("series:entry").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("detail:play").assertIsFocused().assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(hero, compose.onNodeWithTag("detail:hero").getUnclippedBoundsInRoot())
            compose.onNodeWithTag("detail:play").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("favorite").assertIsFocused()
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(hero, compose.onNodeWithTag("detail:hero").getUnclippedBoundsInRoot())
            compose.onNodeWithText("选择一集").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun allTvEntryTypesStartWithoutAnActionFocusOrEpisodeSelection() {
        val state = mutableStateOf(ready())
        var selections = 0
        val scenario = mount(state, true, emptyActions().copy(selectEpisode = { selections++ }))
        try {
            listOf("Series", "Season", "Episode").forEachIndexed { index, type ->
                compose.runOnIdle {
                    state.value = ready(true).copy(visit = index.toLong() + 10, entryType = type)
                }
                compose.onNodeWithTag("series:entry").assertIsFocused()
                compose.onNodeWithTag("detail:play").assertIsDisplayed().assertIsNotFocused()
                compose.onNodeWithText("本集详情").assertDoesNotExist()
                compose.onNodeWithTag("series:entry").performKeyInput { pressKey(Key.Enter) }
                compose.onNodeWithText("本集详情").assertDoesNotExist()
                assertEquals(0, selections)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvPreviewOpensBottomSheetOnTheSamePageAndCloseRestoresItsCard() {
        val state = mutableStateOf(ready())
        var played: String? = null
        val actions =
            emptyActions()
                .copy(
                    selectEpisode = { episode ->
                        state.value =
                            state.value.copy(
                                series = state.value.series!!.copy(inspectedEpisodeId = episode.id)
                            )
                    },
                    prepareEpisode = { episode, _, _, _ -> played = episode.id },
                )
        val scenario = mount(state, true, actions)
        try {
            compose.onNodeWithTag("series:preview:e4").performSemanticsAction(
                SemanticsActions.RequestFocus
            ) {
                it()
            }
            compose.onNodeWithTag("series:preview:e4").assertIsFocused().assertIsDisplayed()
            val hero = compose.onNodeWithTag("detail:hero").getUnclippedBoundsInRoot()
            compose.onNodeWithTag("series:preview:e4").performClick()
            compose.onNodeWithText("本集详情").assertExists()
            compose.onNodeWithTag("episode:sheet").assertIsDisplayed()
            assertEpisodeActionsFit()
            compose.onNodeWithTag("episode:sheet-play").assertIsFocused()
            compose
                .onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("episode:sheet")))
                .assertCountEquals(0)
            compose.onNodeWithTag("series-detail").assertExists()
            compose.onNodeWithTag("series-episodes").assertDoesNotExist()
            assertNull(played)
            compose.onNodeWithTag("episode:sheet-play").assertIsDisplayed().performClick()
            assertEquals("e4", played)
            compose.onNodeWithTag("sheet:close").performClick()
            compose.onNodeWithTag("series:preview:e4").assertIsFocused().assertIsDisplayed()
            assertEquals(hero, compose.onNodeWithTag("detail:hero").getUnclippedBoundsInRoot())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvMediaVersionsReplaceContentWithoutScrollingAndPlayTheVisibleVersion() {
        val episode =
            episodes[3].copy(
                sources =
                    (1..3).map {
                        dev.joyfin.data.MediaSource(
                            id = "source$it",
                            name = "Version $it",
                            streams =
                                (1..30).map { track ->
                                    dev.joyfin.data.MediaStream(
                                        type = "Audio",
                                        displayTitle = "Audio $track",
                                    )
                                },
                        )
                    }
            )
        val initial = ready()
        val state =
            mutableStateOf(
                initial.copy(
                    series = initial.series!!.copy(episodeDetails = SeriesResource.Ready(episode))
                )
            )
        var playedSource: String? = null
        val scenario =
            mount(
                state,
                true,
                emptyActions().copy(prepareEpisode = { _, _, source, _ -> playedSource = source }),
            )
        try {
            compose.onNodeWithTag("series:preview:e4").performScrollTo().performClick()
            compose.onNodeWithTag("episode:sheet-media").performClick()
            compose.onNodeWithText("版本 1 / 3 · Version 1").assertIsDisplayed()
            compose.onNodeWithTag("episode:version-prev").assertIsNotEnabled()
            compose.onNodeWithTag("episode:version-next").performClick()
            compose.onNodeWithText("版本 2 / 3 · Version 2").assertIsDisplayed()
            compose
                .onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("episode:sheet")))
                .assertCountEquals(0)
            compose.onNodeWithTag("episode:sheet-play").assertIsDisplayed().performClick()
            assertEquals("source2", playedSource)
            compose.onNodeWithTag("episode:sheet-details").performClick()
            compose.onNodeWithText("本集详情").assertIsDisplayed()
            assertEpisodeActionsFit()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun phoneIntroductionUsesContentHeightInsteadOfViewportHeight() {
        val state = mutableStateOf(ready())
        val height = mutableStateOf(720.dp)
        val scenario = mount(state, false, emptyActions(), height)
        try {
            val introduction = compose.onNodeWithTag("detail:hero")
            val initial = introduction.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            compose.runOnIdle { height.value = 520.dp }
            assertEquals(
                initial,
                introduction.getUnclippedBoundsInRoot().let { it.bottom - it.top },
            )
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        item =
                            state.value.item!!.copy(
                                name = "长剧名".repeat(16),
                                overview = "完整剧情简介。".repeat(60),
                            )
                    )
            }
            assertTrue(introduction.getUnclippedBoundsInRoot().let { it.bottom - it.top } > initial)
            compose.onNodeWithTag("series:season").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("series:preview:e4").performScrollTo().assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun phoneEpisodeEntryStaysOnHeroUntilACardIsClicked() {
        val state = mutableStateOf(ready(true))
        var played: String? = null
        val scenario =
            mount(
                state,
                false,
                emptyActions().copy(prepareEpisode = { episode, _, _, _ -> played = episode.id }),
            )
        try {
            compose.onNodeWithText("本集详情").assertDoesNotExist()
            compose.onNodeWithTag("detail:play").assertIsDisplayed().assertIsNotFocused()
            compose.onNodeWithTag("series:preview:e4").performScrollTo().performClick()
            compose.onNodeWithText("本集详情").assertExists()
            assertEpisodeActionsFit()
            compose
                .onNode(hasText("Episode 4") and hasAnyAncestor(hasTestTag("episode:sheet")))
                .assertExists()
            compose.onNodeWithTag("series-detail").assertExists()
            compose.onNodeWithTag("series-episodes").assertDoesNotExist()
            assertNull(played)
            compose.onNodeWithTag("episode:sheet-play").assertIsDisplayed().performClick()
            assertEquals("e4", played)
            compose.onNodeWithTag("sheet:close").performClick()
            compose.onNodeWithText("本集详情").assertDoesNotExist()
            compose.onNodeWithTag("detail:play").assertExists()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun failedSupplementKeepsEpisodePlaybackAndRetryAvailableInTheSheet() {
        val initial = ready(true)
        val state =
            mutableStateOf(
                initial.copy(
                    series =
                        initial.series!!.copy(episodeDetails = SeriesResource.Failed("offline"))
                )
            )
        var retries = 0
        val scenario = mount(state, true, emptyActions().copy(retrySeriesEpisode = { retries++ }))
        try {
            compose.onNodeWithText("本集详情").assertDoesNotExist()
            compose.onNodeWithTag("series:preview:e4").performScrollTo().performClick()
            compose.onNodeWithTag("episode:sheet-play").assertIsEnabled()
            compose.onNodeWithTag("episode:sheet-retry").assertIsDisplayed().performClick()
            assertEquals(1, retries)
            compose.onNodeWithTag("series-episodes").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun tvEpisodeSkeletonSurvivesSeasonDiscoveryAndEpisodeRequest() =
        assertSeasonDiscoverySkeleton(true)

    @Test
    fun phoneEpisodeSkeletonSurvivesSeasonDiscoveryAndEpisodeRequest() =
        assertSeasonDiscoverySkeleton(false)

    private fun assertSeasonDiscoverySkeleton(tv: Boolean) {
        val initial = ready()
        val state =
            mutableStateOf(
                initial.copy(
                    series =
                        initial.series!!.copy(
                            seasons = SeriesResource.Loading,
                            seasonId = null,
                            episodes = SeriesResource.Idle,
                        )
                )
            )
        val scenario = mount(state, tv, emptyActions())
        try {
            val skeleton = compose.onNodeWithTag("series:preview-skeleton")
            skeleton.performScrollTo().assertIsDisplayed()
            val height = skeleton.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        series = state.value.series!!.copy(seasons = initial.series.seasons)
                    )
            }
            // The season is known, but its entry resolution/request has not completed yet.
            skeleton.assertExists()
            assertEquals(height, skeleton.getUnclippedBoundsInRoot().let { it.bottom - it.top })
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        series =
                            state.value.series!!.copy(
                                seasonId = "s1",
                                episodes = SeriesResource.Loading,
                            )
                    )
            }
            skeleton.assertExists()
            assertEquals(height, skeleton.getUnclippedBoundsInRoot().let { it.bottom - it.top })
            compose.runOnIdle { state.value = initial }
            skeleton.assertDoesNotExist()
            compose.onNodeWithTag("series:preview:e1").assertExists()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun failedSeasonDiscoveryEndsEpisodeSkeletonAndOffersRetry() {
        val initial = ready()
        val state =
            mutableStateOf(
                initial.copy(
                    series =
                        initial.series!!.copy(
                            seasons = SeriesResource.Loading,
                            seasonId = null,
                            episodes = SeriesResource.Idle,
                        )
                )
            )
        val scenario = mount(state, false, emptyActions())
        try {
            compose.onNodeWithTag("series:preview-skeleton").performScrollTo().assertExists()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        series =
                            state.value.series!!.copy(seasons = SeriesResource.Failed("offline"))
                    )
            }
            compose.onNodeWithTag("series:preview-skeleton").assertDoesNotExist()
            compose.onNodeWithTag("series:seasons:retry").assertExists().assertIsEnabled()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun seasonHeaderRetainsFocusThroughColdLoadingAndReadyData() {
        val state = mutableStateOf(ready())
        val scenario =
            mount(
                state,
                true,
                emptyActions()
                    .copy(
                        selectSeason = { id ->
                            state.value =
                                state.value.copy(
                                    series =
                                        state.value.series!!.copy(
                                            seasonId = id,
                                            episodes = SeriesResource.Loading,
                                            inspectedEpisodeId = null,
                                        )
                                )
                        }
                    ),
            )
        try {
            compose.onNodeWithTag("series:season").performSemanticsAction(
                SemanticsActions.RequestFocus
            ) {
                it()
            }
            compose.onNodeWithTag("series:season").performClick()
            compose.onNodeWithTag("season-choice:s2").performClick()
            compose.onNodeWithTag("series:season").assertIsFocused().assertTextContains("第 2 季")
            compose.onNodeWithTag("series:preview:e4").assertDoesNotExist()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        series =
                            state.value.series!!.copy(
                                episodes =
                                    SeriesResource.Ready(
                                        listOf(episodes[0].copy(seasonId = "s2", season = 2))
                                    )
                            )
                    )
            }
            compose.onNodeWithTag("series:season").assertIsFocused()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sheetPreparationUsesTheSameActionToCancelAndClosingCancelsIt() {
        val state = mutableStateOf(ready(true))
        var cancellations = 0
        val actions =
            emptyActions()
                .copy(
                    prepareEpisode = { episode, fromStart, source, trigger ->
                        state.value =
                            state.value.copy(
                                series =
                                    state.value.series!!.copy(
                                        playback =
                                            EpisodePlaybackState.Preparing(
                                                EpisodePlaybackIntent(
                                                    1,
                                                    episode,
                                                    fromStart,
                                                    source,
                                                    trigger,
                                                )
                                            )
                                    )
                            )
                    },
                    cancelEpisodePlayback = {
                        cancellations++
                        state.value =
                            state.value.copy(
                                series =
                                    state.value.series!!.copy(playback = EpisodePlaybackState.Idle)
                            )
                    },
                )
        val scenario = mount(state, false, actions)
        try {
            compose.onNodeWithTag("series:preview:e4").performScrollTo().performClick()
            compose.onNodeWithTag("episode:sheet-play").assertIsDisplayed().performClick()
            compose
                .onNodeWithTag("episode:sheet-play")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在准备，点击取消")
                )
                .performClick()
            assertEquals(1, cancellations)
            compose.onNodeWithTag("episode:sheet-play").performClick()
            compose.onNodeWithTag("sheet:close").performClick()
            assertEquals(2, cancellations)
            compose.onNodeWithTag("series-detail").assertExists()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun missingParentIsAnErrorAndRemovingAnOpenEpisodeNeverReplacesIt() {
        val state = mutableStateOf(ready(true))
        val scenario = mount(state, false, emptyActions())
        try {
            compose.onNodeWithText("本集详情").assertDoesNotExist()
            compose.onNodeWithTag("detail:play").assertIsDisplayed().assertIsNotFocused()
            compose.onNodeWithTag("series:preview:e4").performScrollTo().performClick()
            compose.onNodeWithText("本集详情").assertExists()
            assertEpisodeActionsFit()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        series =
                            state.value.series!!.copy(
                                episodes = SeriesResource.Ready(episodes.filter { it.id != "e4" }),
                                inspectedEpisodeId = "e5",
                            )
                    )
            }
            compose.onNodeWithText("本集详情").assertDoesNotExist()
            compose.onNodeWithTag("series:season").assertExists()
            compose.runOnIdle {
                state.value = DetailUiState(entryType = "Episode", visit = 2, error = "父剧解析失败")
            }
            compose.onNodeWithText("父剧解析失败").assertExists()
            compose.onNodeWithText("本季暂无剧集").assertDoesNotExist()
            compose.onNodeWithTag("detail:retry").assertExists()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun overviewRecommendationRefreshCannotReplaceThePreparingTargetOrItsCancelAction() {
        val initial = ready()
        val intent = EpisodePlaybackIntent(1, episodes[3], false, null, "detail:play")
        val state =
            mutableStateOf(
                initial.copy(
                    series =
                        initial.series!!.copy(
                            recommendation = SeriesResource.Ready(episodes[4]),
                            playback = EpisodePlaybackState.Preparing(intent),
                        )
                )
            )
        var canceled = false
        val scenario =
            mount(state, true, emptyActions().copy(cancelEpisodePlayback = { canceled = true }))
        try {
            compose
                .onNodeWithTag("detail:play")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在准备，点击取消")
                )
                .assertIsEnabled()
                .performClick()
            assertTrue(canceled)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun collectionFailureIsNotEmptyAndAppendKeepsExistingCardBounds() {
        val state = mutableStateOf(CollectionUiState(busy = true))
        var retried = 0
        val actions = emptyActions().copy(retry = { retried++ })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(false, 360)
                        ) {
                            dev.joyfin.ui.features.collection.MediaCollection(
                                state.value,
                                actions,
                                ArtworkSource({ "" }, { "" }, { null }, { null }, ""),
                            ) {
                                dev.joyfin.ui.components.SectionTitle("Library")
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("暂无内容").assertDoesNotExist()
            compose.runOnIdle { state.value = state.value.copy(busy = false, error = "offline") }
            compose.onNodeWithText("offline").assertExists()
            compose.onNodeWithText("暂无内容").assertDoesNotExist()
            compose.onNodeWithText("重试").performClick()
            assertEquals(1, retried)
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        error = null,
                        media = listOf(Media("a", "Alpha", "Movie"), Media("b", "Beta", "Movie")),
                        total = 4,
                        nextOffset = 2,
                    )
            }
            val card = compose.onNodeWithTag("collection:a")
            val before = card.getUnclippedBoundsInRoot()
            compose.runOnIdle { state.value = state.value.copy(busy = true, appending = true) }
            assertEquals(before, card.getUnclippedBoundsInRoot())
            compose
                .onNodeWithTag("collection:more")
                .assertIsNotEnabled()
                .assertContentDescriptionEquals("加载更多 · 2 / 4")
            compose.runOnIdle {
                state.value = state.value.copy(busy = false, error = "page failed")
            }
            assertEquals(before, card.getUnclippedBoundsInRoot())
            compose.onNodeWithText("暂无内容").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun homeRailKeepsHeightAcrossMixedCardsAndLongMetadata() {
        val media =
            (1..30).map { index ->
                Media(
                    "card$index",
                    "很长的媒体标题".repeat(index),
                    if (index % 2 == 0) "Episode" else "Movie",
                    episode = index,
                    seriesName = "剧名",
                    genres = listOf("非常长的分类".repeat(index)),
                )
            }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    BoundlessTheme {
                        CompositionLocalProvider(
                            LocalDeviceLayout provides DeviceLayout(false, 360)
                        ) {
                            MediaRail(
                                "横排回归",
                                media,
                                ArtworkSource({ "" }, { "" }, { null }, { null }, ""),
                                {},
                            )
                        }
                    }
                }
            }
            val rail = compose.onNodeWithTag("rail:横排回归")
            val height = rail.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            rail.performScrollToIndex(20)
            compose.waitForIdle()
            assertEquals(height, rail.getUnclippedBoundsInRoot().let { it.bottom - it.top })
            rail.performScrollToIndex(29)
            compose.waitForIdle()
            assertEquals(height, rail.getUnclippedBoundsInRoot().let { it.bottom - it.top })
        } finally {
            scenario.close()
        }
    }

    private fun emptyActions() =
        BrowseActions(
            back = {},
            home = {},
            open = {},
            openPerson = {},
            selectEpisode = {},
            retrySeriesEpisode = {},
            retryLibrary = {},
            selectSeason = {},
            library = { _, _ -> },
            search = {},
            submitSearch = {},
            loadMore = {},
            settings = {},
            favorite = {},
            play = { _, _, _ -> },
            retry = {},
            logout = {},
            setAutoNext = {},
        )
}
