package dev.joyfin.ui.features.player

import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.joyfin.playback.PlayerActions
import dev.joyfin.playback.PlayerDeviceControls
import dev.joyfin.playback.PlayerUiState
import dev.joyfin.playback.SeekInput
import dev.joyfin.ui.components.*
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.features.player.components.*
import dev.joyfin.ui.features.player.mobile.MobilePlayerLayout
import dev.joyfin.ui.features.player.mobile.MobilePlayerLockedInfo
import dev.joyfin.ui.features.player.mobile.PlayerDeviceGestureFeedback
import dev.joyfin.ui.features.player.mobile.PlayerSpeedBoostFeedback
import dev.joyfin.ui.features.player.mobile.rememberPlayerDeviceGestures
import dev.joyfin.ui.features.player.tv.TvPlayerLayout
import dev.joyfin.ui.features.player.tv.TvSkipOffer
import dev.joyfin.ui.features.player.mobile.MobileSkipOffer
import dev.joyfin.ui.interaction.LocalScreenFocus
import dev.joyfin.ui.interaction.RememberScreenFocus
import dev.joyfin.ui.interaction.RememberSelectionFocus
import dev.joyfin.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val ExitConfirmationWindowMs = 2_000L

/** Presentation only: never creates a player, requests an API or submits playback reports. */
@Composable
fun PlayerRoute(
    ui: PlayerUiState,
    actions: PlayerActions,
    deviceControls: PlayerDeviceControls? = null,
    imageAuthorization: String = "",
    imageCacheNamespace: String = "",
    initialPanel: PlayerPanel? = null,
) {
    var visible by remember { mutableStateOf(true) }
    var exitRequestedAt by remember(ui.itemId, ui.sourceId) { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val exitHint = remember(context) {
        Toast.makeText(context, "再按一次返回退出播放", Toast.LENGTH_SHORT)
    }
    var locked by remember { mutableStateOf(false) }
    var lockControlsVisible by remember { mutableStateOf(false) }
    var lockInteraction by remember { mutableIntStateOf(0) }
    var focusReturn by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(initialPanel) }
    var panelHistory by remember { mutableStateOf(emptyList<Pair<PlayerPanel, String>>()) }
    var panelReturnFocus by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var subtitleSearchReturn by remember { mutableStateOf(false) }
    var lastSubtitleApplication by remember { mutableIntStateOf(ui.subtitleSearchApplied) }
    LaunchedEffect(ui.subtitleSearchOpen) {
        if (ui.subtitleSearchOpen) subtitleSearchReturn = true
    }
    LaunchedEffect(ui.subtitleSearchApplied) {
        if (ui.subtitleSearchApplied > 0) {
            panel = null
            panelHistory = emptyList()
            panelReturnFocus = null
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
    fun openPanel(next: PlayerPanel) {
        actions.preview.cancel()
        panelHistory = emptyList()
        panelReturnFocus = null
        subtitleSearchReturn = false
        panel = next
        touch()
    }
    fun navigatePanel(next: PlayerPanel) {
        val current = panel
        val parent = panelHistory.lastOrNull()
        if (parent?.first == next) {
            panelHistory = panelHistory.dropLast(1)
            panelReturnFocus = parent.second
        } else {
            val returnFocus = current?.returnFocusFor(next)
            if (current != null && returnFocus != null) panelHistory = panelHistory + (current to returnFocus)
            panelReturnFocus = null
        }
        subtitleSearchReturn = false
        panel = next
        touch()
    }
    fun dismissPanel() {
        val parent = panelHistory.lastOrNull()
        if (parent != null) {
            panelHistory = panelHistory.dropLast(1)
            panel = parent.first
            panelReturnFocus = parent.second
        } else {
            panel = null
            panelReturnFocus = null
            focusReturn++
        }
        subtitleSearchReturn = false
        touch()
    }
    fun wakeLockControls() {
        lockControlsVisible = true
        lockInteraction++
    }
    fun requestExit() {
        val now = SystemClock.elapsedRealtime()
        val previous = exitRequestedAt
        if (previous != null && now - previous in 0L until ExitConfirmationWindowMs) {
            exitRequestedAt = null
            exitHint.cancel()
            actions.close()
        } else {
            exitRequestedAt = now
        }
    }
    val guardedActions = actions.copy(close = ::requestExit)
    LaunchedEffect(exitRequestedAt) {
        if (exitRequestedAt != null) {
            exitHint.show()
            delay(ExitConfirmationWindowMs)
            exitRequestedAt = null
        } else exitHint.cancel()
    }
    DisposableEffect(exitHint) { onDispose { exitHint.cancel() } }
    LaunchedEffect(locked, panel, ui.subtitleSearchOpen, ui.nextSeconds > 0, ui.seekPreview?.active) {
        if (locked || panel != null || ui.subtitleSearchOpen || ui.nextSeconds > 0 || ui.seekPreview?.active == true)
            exitRequestedAt = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exitRequestedAt = null
        exitHint.cancel()
    }
    LaunchedEffect(locked, lockControlsVisible, lockInteraction) {
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
    BackHandler {
        when {
            locked -> wakeLockControls()
            ui.seekPreview?.active == true -> actions.preview.cancel()
            panel != null -> dismissPanel()
            ui.nextSeconds > 0 -> actions.cancelNext()
            ui.subtitleSearchOpen -> Unit // The subtitle dialog owns back while open or closing.
            else -> {
                visible = false
                requestExit()
            }
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
                onLockedTap = ::wakeLockControls,
                verticalActions = verticalActions.takeIf { deviceGesturesEnabled },
                speedBoost = actions.speedBoost.takeIf { speedBoostEnabled },
                gestureKey = ui.itemId to ui.sourceId,
            )
            val showControls = !locked && (visible || panel != null || ui.error != null || (ui.nextSeconds > 0 && !ui.playing))
            PlayerControlsExitHost(visible = showControls) {
                if (tv) TvPlayerLayout(ui, guardedActions, panel, ::openPanel, ::touch)
                else MobilePlayerLayout(ui, guardedActions, panel, ::openPanel, ::touch)
                if (skipVisible && panel == null && ui.error == null && !ui.loading && !ui.subtitleApplying &&
                    ui.canSeek && ui.nextSeconds == 0) {
                    val skipModifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding()
                        .padding(end = if (tv) 40.dp else 22.dp, bottom = (if (tv) 112.dp else 100.dp) * fontScale)
                    if (tv) TvSkipOffer(ui.navigation, actions, skipModifier)
                    else MobileSkipOffer(ui.navigation, actions, skipModifier)
                }
            }
            AnimatedVisibility(
                visible = !tv && locked && lockControlsVisible,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(140)),
            ) {
                MobilePlayerLockedInfo(ui)
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
                            wakeLockControls()
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
            ui.navigation.markFeedback?.takeIf { !locked && showControls && panel == null }?.let { message ->
                Caption(
                    message,
                    Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 76.dp)
                        .background(Palette.Background.copy(alpha = .94f), RoundedCornerShape(12.dp))
                        .border(.5.dp, Palette.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    color = Palette.Text,
                    maxLines = 2,
                )
            }
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
                    ::navigatePanel,
                    ::dismissPanel,
                    initialFocus = when {
                        current == PlayerPanel.Subtitle && subtitleSearchReturn -> "player:subtitle-search"
                        else -> panelReturnFocus ?: current.initialFocus(ui)
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
            .semantics { contentDescription = if (locked) "解锁屏幕" else "锁定屏幕" }
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
