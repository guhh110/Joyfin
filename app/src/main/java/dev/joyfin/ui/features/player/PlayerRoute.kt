package dev.joyfin.ui.features.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.playback.SeekInput
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.features.player.mobile.MobilePlayerLayout
import dev.joyfin.ui.features.player.tv.TvPlayerLayout
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.RememberSelectionFocus
import kotlinx.coroutines.delay

/** Presentation only: never creates a player, requests an API or submits playback reports. */
@Composable
fun PlayerRoute(ui: PlayerUiState, actions: PlayerActions) {
    var visible by remember { mutableStateOf(true) }
    var focusReturn by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    val tv = LocalDeviceLayout.current.television
    fun touch() {
        interaction++
        visible = true
    }
    LaunchedEffect(
        ui.playing,
        panel,
        interaction,
        ui.loading,
        ui.error,
        ui.nextSeconds > 0,
        ui.seekPreview,
    ) {
        if (
            ui.playing &&
                panel == null &&
                !ui.loading &&
                ui.error == null &&
                ui.nextSeconds == 0 &&
                ui.seekPreview?.active != true
        ) {
            delay(4500)
            visible = false
        }
    }
    LaunchedEffect(ui.error) { if (tv && ui.error != null) touch() }
    BackHandler(
        enabled = panel != null || ui.nextSeconds > 0 || visible || ui.seekPreview?.active == true
    ) {
        when {
            ui.seekPreview?.active == true -> actions.preview.cancel()
            panel != null -> {
                panel = null
                focusReturn++
            }
            ui.nextSeconds > 0 -> actions.cancelNext()
            else -> visible = false
        }
    }
    RememberScreenFocus("player:toggle") {
        val focusMemory = LocalScreenFocus.current
        LaunchedEffect(visible) {
            if (tv && visible) {
                focusMemory?.rememberTarget?.invoke("player:toggle")
                withFrameNanos {}
                focusMemory?.requesters?.get("player:toggle")?.requestFocus()
            }
        }
        LaunchedEffect(ui.error) {
            if (
                tv &&
                    ui.error == null &&
                    focusMemory?.target?.invoke() in setOf("player:retry", "player:compatible")
            ) {
                withFrameNanos {}
                focusMemory?.requesters?.get("player:toggle")?.requestFocus()
            }
        }
        LaunchedEffect(focusReturn) {
            if (focusReturn > 0) {
                withFrameNanos {}
                focusMemory?.restore()
            }
        }
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && ui.seekPreview?.active == true) {
                    when (event.key) {
                        Key.DirectionLeft,
                        Key.DirectionRight ->
                            if (
                                ui.seekPreview.input == SeekInput.REMOTE &&
                                    (!visible || focusMemory?.target?.invoke() == "player:progress")
                            ) {
                                actions.preview.adjust(
                                    if (event.key == Key.DirectionLeft) -10_000 else 10_000
                                )
                                if (visible) touch()
                                return@onPreviewKeyEvent true
                            }
                        Key.DirectionUp,
                        Key.DirectionDown -> actions.preview.cancel()
                    }
                }
                if (visible && event.type == KeyEventType.KeyDown) touch()
                false
            }
        ) {
            VideoGestureSurface(
                visible,
                ui.canSeek && !ui.loading && ui.error == null && panel == null,
                actions.preview,
                { touch() },
                {
                    visible = !visible
                    interaction++
                },
                onConfirm = {
                    if (!ui.loading && ui.error == null && ui.nextSeconds == 0) actions.toggle()
                },
            )
            val showControls = visible || panel != null || ui.error != null || ui.nextSeconds > 0
            PlayerControlsExitHost(visible = showControls) {
                val onPanel: (PlayerPanel) -> Unit = {
                    touch()
                    actions.preview.cancel()
                    panel = it
                }
                if (tv) TvPlayerLayout(ui, actions, panel, onPanel, ::touch)
                else MobilePlayerLayout(ui, actions, panel, onPanel, ::touch)
            }
            ui.seekPreview?.let { preview ->
                SeekTooltip(preview, Modifier.align(Alignment.Center).offset(y = (-100).dp))
            }
            if (ui.nextSeconds > 0 && ui.nextItem != null)
                Popup(
                    alignment = Alignment.BottomEnd,
                    onDismissRequest = actions.cancelNext,
                    properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
                ) {
                    RememberSelectionFocus("player:confirm-next") {
                        NextEpisodeCard(
                            ui.nextItem,
                            ui.nextSeconds,
                            actions.nextNow,
                            actions.cancelNext,
                            Modifier.padding(24.dp),
                        )
                    }
                }
            panel?.let { current ->
                PlayerSettingsSheet(
                    current,
                    ui,
                    actions,
                    {
                        panel = it
                        touch()
                    },
                    {
                        panel = null
                        focusReturn++
                        touch()
                    },
                )
            }
        }
    }
}
