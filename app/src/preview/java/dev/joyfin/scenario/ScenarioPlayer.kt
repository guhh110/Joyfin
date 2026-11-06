package dev.joyfin.scenario

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.joyfin.R
import dev.joyfin.data.Media
import dev.joyfin.data.VideoScaleMode
import dev.joyfin.playback.*
import dev.joyfin.ui.features.player.PlayerRoute
import dev.joyfin.ui.features.player.components.PlayerPanel
import kotlinx.coroutines.delay

/** Local presentation fixture: no player, server, playback reports or real preferences. */
@Composable
internal fun ScenarioPlayer(scenario: Scenario, initialPanel: PlayerPanel?, skipOffer: String? = null, onClose: () -> Unit) {
    var ui by remember(scenario, skipOffer) { mutableStateOf(playerFixture(scenario, skipOffer)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seek = remember {
        SeekPreviewController(scope,
            { SeekEnvironment(ui.positionMs, ui.durationMs, ui.canSeek && ui.error == null && !ui.loading) },
            { ui = ui.copy(seekPreview = it) },
            { ui = ui.copy(positionMs = it, seekPreview = null) })
    }
    DisposableEffect(seek) { onDispose { seek.cancel() } }
    LaunchedEffect(ui.playing) {
        while (ui.playing) {
            delay(1000)
            val position = (ui.positionMs + (1000 * ui.speed).toLong()).coerceAtMost(ui.durationMs)
            ui = ui.copy(positionMs = position, playing = position < ui.durationMs)
        }
    }
    fun jump(position: Long) { ui = ui.copy(positionMs = position.coerceIn(0, ui.durationMs)) }
    val actions = PlayerActions(
        toggle = { ui = ui.copy(playing = !ui.playing) },
        preview = seek.actions,
        seek = ::jump,
        selectEpisode = { episode -> ui = ui.copy(itemId = episode.id, subtitle = "${episode.label} · 本地模拟", positionMs = 0) },
        selectSeason = { season ->
            val number = if (season == "season-2") 2 else 1
            ui = ui.copy(selectedSeasonId = season, episodes = fixtureEpisodes(number))
        },
        selectAudio = { ui = ui.copy(audioIndex = it) },
        selectSubtitle = { ui = ui.copy(subtitleIndex = it, localSubtitle = null) },
        selectBitrate = { ui = ui.copy(bitrate = it) },
        speed = { ui = ui.copy(speed = it) },
        selectVersion = { ui = ui.copy(sourceId = it) },
        compatible = { ui = ui.copy(compatible = true, method = "Transcode", error = null) },
        selectCompatibility = { ui = ui.copy(compatible = it, method = if (it) "Transcode" else "DirectPlay") },
        retry = { ui = playerFixture(Scenario.NORMAL, skipOffer) },
        cancelNext = { ui = ui.copy(nextSeconds = 0) },
        nextNow = { ui = ui.copy(nextSeconds = 0, positionMs = 0) },
        close = onClose,
        searchSubtitles = { android.widget.Toast.makeText(context, "本地演示：请在字幕列表中选择模拟字幕", android.widget.Toast.LENGTH_SHORT).show() },
        setSubtitleSize = { ui = ui.copy(subtitleSize = it) },
        setSubtitleBackground = { ui = ui.copy(subtitleBackground = it) },
        setAudioOutputMode = { ui = ui.copy(audioOutputMode = it) },
        selectChapter = ::jump,
        setVideoScaleMode = { ui = ui.copy(videoScaleMode = it) },
        setToolbarPreferences = { ui = ui.copy(toolbar = it) },
        setSkipPreferences = { ui = ui.copy(navigation = ui.navigation.copy(preferences = it)) },
        dismissSkip = { ui = ui.copy(navigation = ui.navigation.copy(offer = null, offerSeconds = 0, offerAutomatic = false)) },
        skipSegment = {
            val range = ui.navigation.offer
            if (range != null) ui = ui.copy(positionMs = range.endMs,
                navigation = ui.navigation.copy(offer = null, offerSeconds = 0, offerAutomatic = false))
        },
    )
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(painterResource(R.drawable.entry_harbor), contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (ui.videoScaleMode == VideoScaleMode.FIT) ContentScale.Fit else ContentScale.Crop)
        PlayerRoute(ui, actions, initialPanel = initialPanel)
    }
}

private fun fixtureEpisodes(season: Int): List<Media> = listOf(
    "出发之前", "沿着海岸线", "风从山谷来", "抵达无人知晓的港湾：在漫长旅途中重新发现生活", "潮汐之间", "明日的方向",
).mapIndexed { index, title ->
    Media("episode-$season-${index + 1}", title, "Episode", seriesId = "preview-series",
        season = season, episode = index + 1, played = season == 1 && index < 2)
}

private fun playerFixture(scenario: Scenario, skipOffer: String? = null): PlayerUiState {
    val empty = scenario == Scenario.EMPTY
    val fixture = PlayerUiState(
        title = "山海之间",
        subtitle = "S1 E3 · 风从山谷来 · 本地模拟",
        canSeek = true,
        hasRenderedFrame = true,
        positionMs = 1_122_000,
        durationMs = 2_880_000,
        bufferedMs = 1_800_000,
        method = "DirectPlay",
        error = if (scenario == Scenario.ERROR) "模拟播放失败，请重试" else null,
        audio = if (empty) emptyList() else listOf(
            TrackChoice(1, "普通话 · 原声", "EAC3 · 5.1 声道"),
            TrackChoice(2, "English · 英语", "AAC · 2 声道"),
            TrackChoice(3, "导演评论音轨 · 关于这段旅程背后的故事", "AAC · 2 声道")),
        audioIndex = 1,
        subtitles = if (empty) emptyList() else listOf(
            TrackChoice(4, "简体中文", "SRT · 默认"),
            TrackChoice(5, "繁體中文", "ASS · 内封字幕"),
            TrackChoice(6, "简体中文 / English · 双语字幕", "ASS · 内封字幕")),
        subtitleIndex = if (empty) -1 else 4,
        episodes = if (empty) emptyList() else fixtureEpisodes(1),
        seasons = if (empty) emptyList() else listOf(
            Media("season-1", "第 1 季", "Season"), Media("season-2", "第 2 季", "Season")),
        selectedSeasonId = "season-1",
        itemId = "episode-1-3",
        versions = if (empty) emptyList() else listOf(
            VersionChoice("original", "4K · HEVC · 原始版本"), VersionChoice("hd", "1080p · H.264 · 兼容版本")),
        sourceId = "original",
        navigation = PlaybackNavigationState(
            chapters = if (empty) emptyList() else listOf(
                ChapterChoice(0, "序幕 · 山海之间"), ChapterChoice(90_000, "沿着海岸线"),
                ChapterChoice(900_000, "风从山谷来"), ChapterChoice(1_800_000, "日落前的港湾"),
                ChapterChoice(2_760_000, "片尾")),
            ranges = listOf(SkipRange(0, 90_000, "片头"), SkipRange(2_760_000, 2_880_000, "片尾")),
            canConfigure = !empty,
            preferenceScope = "演示剧集 · 第 1 季 · 仅本次场景",
        ),
    )
    if (scenario != Scenario.NORMAL) return fixture
    val offer = when (skipOffer) {
        "auto-outro", "outro" -> fixture.navigation.ranges.last()
        "intro" -> fixture.navigation.ranges.first()
        else -> return fixture
    }
    // Freeze the presentation so the prompt remains available for visual review.
    return fixture.copy(
        positionMs = if (skipOffer == "auto-outro") offer.startMs - 3000 else offer.startMs,
        navigation = fixture.navigation.copy(offer = offer,
            preferences = fixture.navigation.preferences.copy(automatic = skipOffer == "auto-outro"),
            offerSeconds = if (skipOffer == "auto-outro") 3 else 0,
            offerAutomatic = skipOffer == "auto-outro"),
    )
}
