@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.joyfin.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException

/** Transparent I/O instrumentation. Authentication, redirects, Range, listeners and errors delegate unchanged. */
internal class PlaybackHttpDataSource(
    private val upstream: HttpDataSource,
    private val start: (Long, Long, HttpReadPurpose) -> HttpReadMeter?,
    private val changed: () -> Unit,
    private val purpose: (DataSpec) -> HttpReadPurpose,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : HttpDataSource by upstream {
    private var meter: HttpReadMeter? = null

    override fun open(dataSpec: DataSpec): Long {
        meter = start(dataSpec.position, dataSpec.length, purpose(dataSpec))
        return try {
            val length = upstream.open(dataSpec)
            val contentRange = upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }
                ?.value?.singleOrNull()
            meter?.opened(now(), upstream.responseCode,
                httpRangeResult(dataSpec.position, dataSpec.length, upstream.responseCode, contentRange))
            changed()
            length
        } catch (e: IOException) {
            meter?.fail(now(), diagnosticFailure(e)); changed(); throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return upstream.read(buffer, offset, length)
        meter?.readStarted(now())
        return try {
            upstream.read(buffer, offset, length).also { meter?.readFinished(now(), it) }
        } catch (e: IOException) {
            meter?.fail(now(), diagnosticFailure(e)); changed(); throw e
        }
    }

    override fun close() {
        try { upstream.close() }
        catch (e: IOException) { meter?.fail(now(), diagnosticFailure(e)); throw e }
        finally { meter?.finish(now(), HttpReadState.CLOSED); meter = null; changed() }
    }
}
