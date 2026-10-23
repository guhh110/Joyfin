@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import dev.joyfin.playback.PlaybackController
import dev.joyfin.playback.AndroidPlayerDeviceControls
import dev.joyfin.playback.output.PlayerVideoViewport
import dev.joyfin.ui.features.player.PlayerRoute
import dev.joyfin.ui.features.player.PlayerSubtitleSearchRoute
import dev.joyfin.ui.theme.BoundlessTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the native video surface, safe insets and lifecycle; all playback execution is delegated.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var controller: PlaybackController
    private lateinit var video: PlayerView
    private lateinit var viewport: PlayerVideoViewport

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller =
            PlaybackController(
                context = this,
                scope = lifecycleScope,
                isStarted = { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
                onClose = { expired ->
                    if (expired) setResult(RESULT_CANCELED, Intent().putExtra("authExpired", true))
                    finish()
                },
                itemId = intent.getStringExtra("itemId").orEmpty(),
                startTicks = intent.getLongExtra("startTicks", 0),
                initialSourceId = intent.getStringExtra("sourceId"),
                restored = savedInstanceState,
            )
        val frame = FrameLayout(this)
        video =
            PlayerView(this).apply {
                subtitleView?.let(controller::bindSubtitles)
                player = controller.player
                useController = false
                setShutterBackgroundColor(Color.BLACK)
            }
        viewport = PlayerVideoViewport(video).apply { apply(controller.state.value.videoScaleMode) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.map { it.videoScaleMode }.distinctUntilChanged().collect(viewport::apply)
            }
        }
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        val deviceControls = AndroidPlayerDeviceControls(this)
        frame.addView(
            ComposeView(this).apply {
                setContent {
                    val ui by controller.state.collectAsStateWithLifecycle()
                    BoundlessTheme {
                        PlayerRoute(ui, controller.actions, deviceControls)
                        PlayerSubtitleSearchRoute(controller.subtitleSearch)
                    }
                }
            },
            FrameLayout.LayoutParams(-1, -1),
        )
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val safe =
                insets.getInsets(
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars()
                )
            // Keep the video edge-to-edge; only subtitle content needs a safe viewport.
            video.subtitleView?.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(frame)
        window.decorView.post {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (controller.state.value.nextSeconds > 0) controller.cancelCountdown()
                    else finish()
                }
            },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (::controller.isInitialized && controller.handleMediaKey(keyCode)) true
        else super.onKeyDown(keyCode, event)

    override fun onStart() {
        super.onStart()
        if (::controller.isInitialized) controller.onStart()
    }

    override fun onStop() {
        if (::controller.isInitialized) controller.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::controller.isInitialized) controller.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::viewport.isInitialized) viewport.release()
        if (::video.isInitialized) video.player = null
        if (::controller.isInitialized) controller.release()
        super.onDestroy()
    }
}
