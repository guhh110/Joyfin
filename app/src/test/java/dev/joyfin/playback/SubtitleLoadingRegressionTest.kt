@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.source.CompositeSequenceableLoader
import androidx.media3.exoplayer.source.SequenceableLoader
import org.junit.Assert.*
import org.junit.Test

/** Reproduces the actual Media3 scheduler behavior without a device or a remote server. */
class SubtitleLoadingRegressionTest {
    private class Loader(private val positionUs: Long) : SequenceableLoader {
        var calls = 0
        var finished = false
        override fun getBufferedPositionUs() = if (finished) C.TIME_END_OF_SOURCE else positionUs
        override fun getNextLoadPositionUs() = bufferedPositionUs
        override fun continueLoading(loadingInfo: LoadingInfo): Boolean { calls++; return false }
        override fun isLoading() = false
        override fun reevaluateBuffer(positionUs: Long) = Unit
    }

    @Test fun slowIndependentSubtitleCanHoldBackVideoLoadingUntilItsRequestFinishes() {
        val video = Loader(2_000_000)
        val subtitle = Loader(0)
        val loading = LoadingInfo.Builder().setPlaybackPositionUs(1_000_000).build()
        val merged = CompositeSequenceableLoader(listOf(video, subtitle),
            listOf(listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_TEXT)))
        merged.continueLoading(loading)
        assertEquals(0, video.calls)
        assertEquals(1, subtitle.calls)
        // The reported buffer can still be ahead while the subtitle holds the load position back.
        assertEquals(2_000_000L, merged.bufferedPositionUs)
        subtitle.finished = true
        merged.continueLoading(loading)
        assertEquals(1, video.calls)

        val nativeMkv = Loader(2_000_000)
        val mediaOnly = CompositeSequenceableLoader(listOf(nativeMkv),
            listOf(listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)))
        mediaOnly.continueLoading(loading)
        assertEquals(1, nativeMkv.calls)
    }
}
