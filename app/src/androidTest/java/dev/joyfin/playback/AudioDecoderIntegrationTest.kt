@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on each supported ABI to check JNI loading and compatibility with the resolved Media3. */
@RunWith(AndroidJUnit4::class)
class AudioDecoderIntegrationTest {
    @Test
    fun nativeDecoderHandlesDtsHdAndTrueHdAndNegotiationIncludesThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("FFmpeg JNI must load on this ABI", FfmpegLibrary.isAvailable())
        val renderer = FfmpegAudioRenderer(null, null, DefaultAudioSink.Builder(context).build())
        listOf("audio/vnd.dts", "audio/vnd.dts.hd", "audio/true-hd").forEach { mime ->
            val format =
                Format.Builder()
                    .setSampleMimeType(mime)
                    .setSampleRate(48_000)
                    .setChannelCount(6)
                    .build()
            assertEquals(
                mime,
                C.FORMAT_HANDLED,
                RendererCapabilities.getFormatSupport(renderer.supportsFormat(format)),
            )
        }
        val capabilities = AudioPlaybackCapabilities.detect(context, null)
        listOf("dts", "dtshd", "truehd").forEach { codec ->
            assertTrue(codec, (capabilities.channelsByCodec[codec] ?: 0) >= 6)
        }
    }
}
