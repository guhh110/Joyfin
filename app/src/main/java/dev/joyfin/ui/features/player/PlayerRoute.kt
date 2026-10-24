package dev.joyfin.ui.features.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerDeviceControls
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.playback.SeekInput
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.features.player.mobile.MobilePlayerLayout
import dev.joyfin.ui.features.player.mobile.PlayerDeviceGestureFeedback
import dev.joyfin.ui.features.player.mobile.PlayerSpeedBoostFeedback
import dev.joyfin.ui.features.player.mobile.rememberPlayerDeviceGestures
import dev.joyfin.ui.features.player.tv.TvPlayerLayout
import dev.joyfin.ui.features.player.tv.TvSkipOffer
import dev.joyfin.ui.features.player.mobile.MobileSkipOffer
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.RememberSelectionFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Presentation only: never creates a player, requests an API or submits playback reports. */
@Composable
fun PlayerRoute(
    ui: PlayerUiState,
    actions: PlayerActions,
    deviceControls: PlayerDeviceControls? = null,
    imageAuthorization: String = "",
    imageCacheNamespace: String = "",
) {
    var visible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lockControlsVisible by remember { mutableStateOf(false) }
    var focusReturn by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var subtitleSearchReturn by remember { mutableStateOf(false) }
    var lastSubtitleApplication by remember { mutableIntStateOf(ui.subtitleSearchApplied) }
    var diagnosticsParent by remember { mutableStateOf<PlayerPanel?>(null) }
    var settingsDiagnosticsReturn by remember { mutableStateOf(false) }
    LaunchedEffect(ui.subtitleSearchOpen) {
        if (ui.subtitleSearchOpen) subtitleSearchReturn = true
    }
    LaunchedEffect(ui.subtitleSearchApplied) {
        if (ui.subtitleSearchApplied > 0) {
            panel = null
            subtitleSearchReturn = false
            focusReturn++
            lastSubtitleApplication = ui.subtitleSearchApplied
        }
    }
    val tv = LocalDeviceLayout.current.television
    val deviceGesturesEnabled = !tv && deviceControls != null && !locked && panel == null &&
        !ui.subtitleSearchOpen && ui.error == null && ui.nextSeconds == 0
    val deviceGestures = rememberPlayerDeviceGestures(deviceControls, deviceGesturesEnabled)
    val verticalActions = remember(deviceGestures) {
        PlayerVerticalGestureActions(deviceGestures::begin, deviceGestures::dragBy, deviceGestures::end)
    }
    val speedBoostEnabled = !tv && !locked && panel == null && !ui.subtitleSearchOpen &&
        !ui.subtitleApplying && ui.error == null && ui.nextSeconds == 0 && (ui.playing || ui.speedBoosting)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    DisposableEffect(panel, actions.setNavigationBlocked) {
        actions.setNavigationBlocked(panel != null)
        onDispose { actions.setNavigationBlocked(false) }
    }
    fun touch() {
        interaction++
        visible = true
    }
    LaunchedEffect(locked, lockControlsVisible) {
        if (locked && lockControlsVisible) {
            delay(3000)
            lockControlsVisible = false
        }
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
        enabled = locked || panel != null || ui.nextSeconds > 0 || visible || ui.seekPreview?.active == true
    ) {
        when {
            locked -> lockControlsVisible = true
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
        val window = LocalWindowInfo.current
        val skipVisible = ui.navigation.offer != null || ui.navigation.undoPositionMs != null
        LaunchedEffect(skipVisible, ui.navigation.undoPositionMs != null) {
            if (tv && (!skipVisible && focusMemory?.target?.invoke()?.startsWith("player:skip-") == true ||
                    ui.navigation.undoPositionMs != null && focusMemory?.target?.invoke() == "player:skip-dismiss")) {
                focusMemory?.rememberTarget?.invoke(if (skipVisible) "player:skip-action" else "player:toggle")
                withFrameNanos {}
                focusMemory?.restore()
            }
        }
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
                snapshotFlow { window.isWindowFocused }.first { it }
                withFrameNanos {}
                focusMemory?.restore()
            }
        }
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (locked) return@onPreviewKeyEvent false
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
                ui.canSeek && !ui.loading && ui.error == null && panel == null &&
                    !ui.subtitleSearchOpen && ui.nextSeconds == 0,
                actions.preview,
                { touch() },
                {
                    visible = !visible
                    interaction++
                },
                onConfirm = {
                    if (!ui.loading && ui.error == null && ui.nextSeconds == 0) actions.toggle()
                },
                locked = locked,
                onLockedTap = {
                    lockControlsVisible = true
                },
                verticalActions = verticalActions.takeIf { deviceGesturesEnabled },
                speedBoost = actions.speedBoost.takeIf { speedBoostEnabled },
                gestureKey = ui.itemId to ui.sourceId,
            )
            val showControls = !locked && (visible || panel != null || ui.error != null || ui.nextSeconds > 0)
            PlayerControlsExitHost(visible = showControls) {
                val onPanel: (PlayerPanel) -> Unit = {
                    touch()
                    actions.preview.cancel()
                    if (it == PlayerPanel.Diagnostics) diagnosticsParent = panel
                    panel = it
                }
                if (tv) TvPlayerLayout(ui, actions, panel, onPanel, ::touch)
                else MobilePlayerLayout(ui, actions, panel, onPanel, ::touch)
                if (skipVisible && panel == null && ui.error == null && !ui.loading && !ui.subtitleApplying &&
                    ui.canSeek && ui.nextSeconds == 0) {
                    val skipModifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding()
                        .padding(end = if (tv) 40.dp else 22.dp, bottom = (if (tv) 112.dp else 100.dp) * fontScale)
                    if (tv) TvSkipOffer(ui.navigation, actions, skipModifier)
                    else MobileSkipOffer(ui.navigation, actions, skipModifier)
                }
            }
            val showLockButton = !tv && ((!locked && showControls && panel == null && ui.nextSeconds == 0) || (locked && lockControlsVisible))
            AnimatedVisibility(
                visible = showLockButton,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)),
                modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 24.dp),
            ) {
                LockFloatingButton(
                    locked = locked,
                    onToggleLock = {
                        if (locked) {
                            locked = false
                            lockControlsVisible = false
                            visible = true
                            interaction++
                        } else {
                            locked = true
                            visible = false
                            lockControlsVisible = true
                        }
                    },
                )
            }
            ui.seekPreview?.takeUnless { locked }?.let { preview ->
                SeekTooltip(preview, Modifier.align(Alignment.Center).offset(y = (-100).dp))
            }
            deviceGestures.level?.takeIf { deviceGesturesEnabled }?.let { level ->
                PlayerDeviceGestureFeedback(level, Modifier.align(Alignment.Center).safeDrawingPadding())
            }
            if (ui.speedBoosting && speedBoostEnabled)
                PlayerSpeedBoostFeedback(ui.speed, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 72.dp))
            if (ui.nextSeconds > 0 && ui.nextItem != null && !locked)
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
            panel?.takeUnless { locked || ui.subtitleSearchOpen || ui.subtitleSearchApplied != lastSubtitleApplication }?.let { current ->
                PlayerSettingsSheet(
                    current,
                    ui,
                    actions,
                    {
                        if (it == PlayerPanel.Diagnostics) diagnosticsParent = current
                        settingsDiagnosticsReturn = false
                        panel = it
                        subtitleSearchReturn = false
                        touch()
                    },
                    {
                        if (current == PlayerPanel.Diagnostics && diagnosticsParent == PlayerPanel.Settings) {
                            panel = PlayerPanel.Settings
                            settingsDiagnosticsReturn = true
                        } else {
                            panel = null
                            settingsDiagnosticsReturn = false
                            focusReturn++
                        }
                        subtitleSearchReturn = false
                        touch()
                    },
                    initialFocus = when {
                        current == PlayerPanel.Subtitle && subtitleSearchReturn -> "player:subtitle-search"
                        current == PlayerPanel.Settings && settingsDiagnosticsReturn -> "player:settings-diagnostics"
                        else -> current.initialFocus(ui)
                    },
                    imageAuthorization = imageAuthorization,
                    imageCacheNamespace = imageCacheNamespace,
                )
            }
        }
    }
}

@Composable
private fun LockFloatingButton(
    locked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("player:lock-button")
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onToggleLock),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            glyph = if (locked) Glyph.Lock else Glyph.Unlock,
            modifier = Modifier.size(20.dp),
            color = if (locked) Color(0xFFFFD54F) else Color.White,
        )
    }
}
