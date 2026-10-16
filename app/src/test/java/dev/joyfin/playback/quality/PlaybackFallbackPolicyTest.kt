@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback.quality

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import dev.joyfin.playback.quality.QualityFixtures.capabilities
import dev.joyfin.playback.quality.QualityFixtures.allPaths
import dev.joyfin.playback.quality.QualityFixtures.decision
import dev.joyfin.playback.quality.QualityFixtures.requirements
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackFallbackPolicyTest {
    private fun policy() = PlaybackFallbackPolicy().apply { context("episode", "main", 1, 1) }
    private fun error(code: Int) = PlaybackException("failure", null, code)
    private fun audioError() = error(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
    private fun current(policy: PlaybackFallbackPolicy) = decision(requirements = requirements(
        forcePcm = policy.forcePcm, forceSoftware = policy.forceSoftware,
        forceAudio = policy.forceAudio, forceVideo = policy.forceVideo))

    @Test fun failedPassthroughTriesPlatformThenSoftwareThenAudioServerExactlyOnce() {
        val policy = policy()
        assertTrue(policy.advance(audioError(), current(policy)))
        assertEquals(AudioPath.PLATFORM_PCM, current(policy).audioPath)
        assertTrue(policy.advance(audioError(), current(policy)))
        assertEquals(AudioPath.SOFTWARE_PCM, current(policy).audioPath)
        assertTrue(policy.advance(audioError(), current(policy)))
        assertEquals(AudioPath.SERVER, current(policy).audioPath)
        assertFalse(policy.forcePcm)
        assertFalse(policy.forceSoftware)
        assertFalse(policy.advance(audioError(), current(policy)))
        assertFalse(policy.forceVideo)
    }

    @Test fun noLocalDecoderSkipsDirectlyToAudioConversion() {
        val policy = policy()
        val input = decision(capabilities = capabilities(mapOf("dts" to allPaths.copy(platformChannels = 0, softwareChannels = 0))))
        assertTrue(policy.advance(audioError(), input))
        assertTrue(policy.forceAudio)
        assertFalse(policy.forcePcm)
        assertFalse(policy.forceVideo)
    }

    @Test fun audioRendererDecodeFailureDoesNotTriggerVideoConversion() {
        val policy = policy()
        val failure = ExoPlaybackException.createForRenderer(IOException("decoder"), "audio", 0,
            Format.Builder().setSampleMimeType("audio/vnd.dts").build(), C.FORMAT_HANDLED, false,
            PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(policy.advance(failure, current(policy)))
        assertTrue(policy.forcePcm)
        assertFalse(policy.forceVideo)
    }

    @Test fun videoAndContainerFormatFailuresTryConversionOnce() {
        for (code in listOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)) {
            val policy = policy()
            assertTrue(policy.advance(error(code), current(policy)))
            assertTrue(policy.forceVideo)
            assertFalse(policy.forceAudio)
            assertFalse(policy.advance(error(code), current(policy)))
        }
    }

    @Test fun networkTimeoutAuthenticationAndHttpFailuresDoNotDowngrade() {
        for (code in listOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)) {
            val policy = policy()
            assertFalse(policy.advance(error(code), current(policy)))
            assertFalse(policy.active)
        }
    }

    @Test fun unrelatedFailuresDoNotStartAFormatRetryLoop() {
        val policy = policy()
        assertFalse(policy.advance(error(PlaybackException.ERROR_CODE_UNSPECIFIED), current(policy)))
        assertFalse(policy.active)
    }

    @Test fun identicalContextKeepsAttemptsWhileMediaVersionTrackAndRouteChangesResetThem() {
        val contexts = listOf<(PlaybackFallbackPolicy) -> Unit>(
            { it.context("next-episode", "main", 1, 1) }, { it.context("episode", "alternate", 1, 1) },
            { it.context("episode", "main", 3, 1) }, { it.context("episode", "main", 1, 2) },
        )
        for (next in contexts) {
            val policy = policy()
            assertTrue(policy.advance(audioError(), current(policy)))
            policy.context("episode", "main", 1, 1)
            assertTrue(policy.forcePcm)
            next(policy)
            assertFalse(policy.active)
            assertTrue(policy.advance(audioError(), current(policy)))
        }
    }

    @Test fun discoveredHdrAndDecoderFailureShareOneVideoFallbackBudget() {
        val policy = policy()
        assertTrue(policy.requireVideoConversion())
        assertFalse(policy.requireVideoConversion())
        assertFalse(policy.advance(error(PlaybackException.ERROR_CODE_DECODING_FAILED), current(policy)))
        policy.reset()
        assertFalse(policy.active)
        assertTrue(policy.requireVideoConversion())
    }

    @Test fun exhaustedDiscFailureExplainsMainStreamLimitationButNetworkRemainsNetwork() {
        val policy = policy()
        val disc = decision().copy(discSource = true)
        assertTrue(policy.failureMessage(audioError(), disc).contains("主影片流"))
        val network = policy.failureMessage(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), disc)
        assertTrue(network.contains("网络"))
        assertFalse(network.contains("菜单"))
    }
}
