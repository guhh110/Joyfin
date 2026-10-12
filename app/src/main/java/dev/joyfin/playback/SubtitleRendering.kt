@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType

/** Wiring only: parsing, fonts, layout and drawing belong to Media3/libass-android. */
class SubtitleRendering {
    private val handler =
        AssHandler(
            AssRenderType.OVERLAY_OPEN_GL,
            AssHandlerConfig(maxRenderPixels = 1920 * 1080),
        )

    fun configure(
        builder: ExoPlayer.Builder,
        context: Context,
        dataSource: DataSource.Factory,
    ): ExoPlayer.Builder {
        val parser = AssSubtitleParserFactory(handler)
        return builder
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                        dataSource,
                        DefaultExtractorsFactory().withAssMkvSupport(parser, handler),
                    )
                    .setSubtitleParserFactory(parser),
            )
            .setRenderersFactory(DefaultRenderersFactory(context).withAssSupport(handler))
    }

    fun bind(player: ExoPlayer) = handler.init(player)

    fun bind(view: SubtitleView) = view.withAssSupport(handler)

    /** Call after the player stops its playback thread. */
    fun release() = handler.release()
}
