package dev.joyfin.scenario

import android.util.Log
import coil.EventListener
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult

/** Preview-only timing without URLs, headers or identifiers. */
internal class ScenarioImageProfile : EventListener {
    private var started = 0L
    private var decoding = 0L
    override fun onStart(request: ImageRequest) { started = System.nanoTime() }
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) { decoding = System.nanoTime() }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        Log.i("JoyfinImageProfile", "decodeMs=${(System.nanoTime() - decoding) / 1_000_000.0}")
    }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        Log.i("JoyfinImageProfile", "source=${result.dataSource} elapsedMs=${(System.nanoTime() - started) / 1_000_000.0} ns=${System.nanoTime()}")
    }
}
