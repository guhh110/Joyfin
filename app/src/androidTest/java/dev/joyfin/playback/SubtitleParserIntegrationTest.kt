@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.joyfin.playback

import androidx.media3.common.Format
import androidx.media3.extractor.text.pgs.PgsParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleParserIntegrationTest {
    @Test
    fun libassFactoryStillDelegatesBitmapAndOtherTextToMedia3() {
        val handler = AssHandler(AssRenderType.CUES)
        try {
            val factory = AssSubtitleParserFactory(handler)
            listOf(
                    "application/pgs",
                    "application/vobsub",
                    "application/dvbsubs",
                    "application/x-quicktime-tx3g",
                    "application/ttml+xml",
                    "text/x-ssa",
                )
                .forEach { mime ->
                    assertTrue(
                        mime,
                        factory.supportsFormat(Format.Builder().setSampleMimeType(mime).build()),
                    )
                }
            assertTrue(
                factory.create(Format.Builder().setSampleMimeType("application/pgs").build())
                    is PgsParser
            )
        } finally {
            handler.release()
        }
    }
}
