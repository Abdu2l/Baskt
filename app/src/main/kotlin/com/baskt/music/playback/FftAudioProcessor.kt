/*
 * Baskt (2026)
 * FFT tap for the audio-reactive lyrics visualizer.
 * Feeds per-band magnitudes to VisualizerState; audio passes through untouched.
 */

package com.baskt.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared, UI-collectable audio levels. Updated ~10x/sec while audio flows. */
object VisualizerState {
    const val BAND_COUNT = 8

    private val _magnitudes =
        kotlinx.coroutines.flow.MutableStateFlow(FloatArray(BAND_COUNT) { 0.15f })
    val magnitudes: kotlinx.coroutines.flow.StateFlow<FloatArray> = _magnitudes

    internal fun update(bands: FloatArray) {
        _magnitudes.value = bands
    }
}

/**
 * Transparent [AudioProcessor] that observes PCM samples and publishes 8
 * log-spaced frequency-band magnitudes. Output bytes are identical to input.
 */
class FftAudioProcessor : AudioProcessor {
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    private var sampleRate = 44100
    private var channelCount = 2
    private var floatEncoding = false

    private val window = FloatArray(FFT_SIZE)
    private var windowFill = 0
    private val smoothed = FloatArray(VisualizerState.BAND_COUNT) { 0.15f }
    private var lastPublishMs = 0L

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        floatEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val supported =
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || floatEncoding
        active = supported
        if (!supported) return AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        return inputAudioFormat
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        outputBuffer = inputBuffer
        if (!active || !inputBuffer.hasRemaining()) return
        analyze(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        windowFill = 0
    }

    override fun reset() {
        flush()
        active = false
    }

    private fun analyze(data: ByteBuffer) {
        val frames = data.remaining() / bytesPerFrame()
        var consumed = 0
        while (consumed < frames) {
            var mono = 0f
            repeat(channelCount) {
                mono +=
                    when {
                        floatEncoding -> if (data.remaining() >= 4) data.float else 0f
                        else -> if (data.remaining() >= 2) data.short / 32768f else 0f
                    }
            }
            mono /= channelCount
            window[windowFill++] = mono
            consumed++
            if (windowFill == FFT_SIZE) {
                windowFill = 0
                onWindowFull()
            }
        }
    }

    private fun bytesPerFrame(): Int = channelCount * if (floatEncoding) 4 else 2

    private fun onWindowFull() {
        val spectrum = magnitudeSpectrum(window, sampleRate)
        for (i in spectrum.indices) {
            val target = spectrum[i].coerceIn(0f, 1f)
            val rate = if (target > smoothed[i]) 0.55f else 0.18f
            smoothed[i] += (target - smoothed[i]) * rate
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPublishMs >= 100L) {
            lastPublishMs = now
            VisualizerState.update(smoothed.copyOf())
        }
    }

    private companion object {
        const val FFT_SIZE = 1024

        /** Log-spaced band edges in Hz, 8 bands from 55Hz to ~12kHz. */
        val BAND_EDGES =
            floatArrayOf(55f, 110f, 220f, 440f, 880f, 1760f, 3520f, 7040f, 12000f)

        fun magnitudeSpectrum(samples: FloatArray, sampleRate: Int): FloatArray {
            val n = samples.size
            val re = FloatArray(n)
            val im = FloatArray(n)
            for (i in 0 until n) {
                // Hann window to reduce leakage.
                val w = 0.5f - 0.5f * cos(2f * Math.PI.toFloat() * i / n)
                re[i] = samples[i] * w
            }
            fft(re, im)
            val out = FloatArray(BAND_EDGES.size - 1)
            val binHz = sampleRate.toFloat() / n
            for (b in out.indices) {
                val fromBin = (BAND_EDGES[b] / binHz).toInt().coerceIn(1, n / 2 - 1)
                val toBin = (BAND_EDGES[b + 1] / binHz).toInt().coerceIn(fromBin + 1, n / 2)
                var peak = 0f
                for (k in fromBin until toBin) {
                    val mag = sqrt(re[k] * re[k] + im[k] * im[k]) / n
                    if (mag > peak) peak = mag
                }
                // Perceptual-ish gain: bass needs a boost, highs need more.
                val gain = 6f + b * 2.2f
                out[b] = (peak * gain * (1f + b * 0.12f)).coerceIn(0f, 1f)
            }
            return out
        }

        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j and bit.inv()
                    bit = bit shr 1
                }
                j = j or bit
                if (i < j) {
                    var tmp = re[i]
                    re[i] = re[j]
                    re[j] = tmp
                    tmp = im[i]
                    im[i] = im[j]
                    im[j] = tmp
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2f * Math.PI.toFloat() / len
                val wRe = cos(angle)
                val wIm = sin(angle)
                var i = 0
                while (i < n) {
                    var wr = 1f
                    var wi = 0f
                    for (m in 0 until len / 2) {
                        val ur = re[i + m]
                        val ui = im[i + m]
                        val vr = re[i + m + len / 2] * wr - im[i + m + len / 2] * wi
                        val vi = re[i + m + len / 2] * wi + im[i + m + len / 2] * wr
                        re[i + m] = ur + vr
                        im[i + m] = ui + vi
                        re[i + m + len / 2] = ur - vr
                        im[i + m + len / 2] = ui - vi
                        val nwr = wr * wRe - wi * wIm
                        wi = wr * wIm + wi * wRe
                        wr = nwr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
