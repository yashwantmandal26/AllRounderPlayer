@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.service

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time Audio Reactor holding live music analysis state
 * consumed by UI visualizers and animations.
 *
 * Optimized to be completely non-blocking and decoupled from the
 * real-time ExoPlayer audio playback thread to ensure zero audio underruns or distortion.
 */
object AudioReactor {
    const val NUM_BANDS = 16
    const val WAVEFORM_POINTS = 64

    private val visualizerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live reactive state for Compose UI
    private val _energy = mutableFloatStateOf(0f)
    val energy: State<Float> = _energy

    private val _bassPulse = mutableFloatStateOf(0f)
    val bassPulse: State<Float> = _bassPulse

    private val _bands = mutableStateOf(FloatArray(NUM_BANDS) { 0f })
    val bands: State<FloatArray> = _bands

    private val _waveformData = mutableStateOf(FloatArray(WAVEFORM_POINTS) { 0f })
    val waveformData: State<FloatArray> = _waveformData

    // Running smoothing values
    private val currentBands = FloatArray(NUM_BANDS) { 0f }
    private val currentWaveform = FloatArray(WAVEFORM_POINTS) { 0f }
    private var smoothedEnergy = 0f
    private var smoothedBass = 0f

    // FFT scratch arrays (512-point real FFT for clean low-frequency resolution)
    private const val FFT_SIZE = 512
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val magnitudes = FloatArray(FFT_SIZE / 2)
    private val window = FloatArray(FFT_SIZE) { i ->
        // Hann window
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    private var lastCaptureTimestamp = 0L
    private const val CAPTURE_INTERVAL_MS = 22L // ~45 fps smooth analysis cadence

    // Preallocated scratch buffer for async processing
    private const val MAX_SAMPLE_CACHE = 2048
    private val floatSampleCache = FloatArray(MAX_SAMPLE_CACHE)
    @Volatile
    private var isProcessing = false

    // Logarithmic frequency bins across 512-point FFT (bins 1..255)
    private val BIN_MAP = arrayOf(
        intArrayOf(1),
        intArrayOf(2),
        intArrayOf(3, 4),
        intArrayOf(5, 6),
        intArrayOf(7, 8, 9),
        intArrayOf(10, 11, 12, 13),
        intArrayOf(14, 15, 16, 17, 18),
        intArrayOf(19, 20, 21, 22, 23, 24),
        intArrayOf(25, 26, 28, 30, 32, 34),
        intArrayOf(35, 37, 39, 42, 45, 48),
        intArrayOf(49, 53, 57, 61, 65, 69),
        intArrayOf(70, 75, 80, 86, 92, 98),
        intArrayOf(99, 106, 113, 121, 129, 137),
        intArrayOf(138, 147, 156, 166, 176, 186),
        intArrayOf(187, 196, 205, 214, 223, 232),
        intArrayOf(233, 238, 243, 248, 252, 255)
    )

    internal fun smoothEma(previous: Float, target: Float, attackAlpha: Float = 0.24f, releaseAlpha: Float = 0.11f): Float {
        val alpha = if (target > previous) attackAlpha else releaseAlpha
        return (previous + (target - previous) * alpha).coerceIn(0f, 1f)
    }

    internal fun spatialSmooth3(values: FloatArray, wrap: Boolean = false): FloatArray {
        if (values.size < 3) return values.copyOf()
        val out = FloatArray(values.size)
        for (i in values.indices) {
            val prevIdx = if (i == 0) (if (wrap) values.lastIndex else 0) else i - 1
            val nextIdx = if (i == values.lastIndex) (if (wrap) 0 else values.lastIndex) else i + 1
            out[i] = values[prevIdx] * 0.2f + values[i] * 0.6f + values[nextIdx] * 0.2f
        }
        return out
    }

    fun reset() {
        _energy.floatValue = 0f
        _bassPulse.floatValue = 0f
        for (i in 0 until NUM_BANDS) {
            currentBands[i] = 0f
        }
        for (i in 0 until WAVEFORM_POINTS) {
            currentWaveform[i] = 0f
        }
        _bands.value = FloatArray(NUM_BANDS) { 0f }
        _waveformData.value = FloatArray(WAVEFORM_POINTS) { 0f }
        smoothedEnergy = 0f
        smoothedBass = 0f
        lastCaptureTimestamp = 0L
        isProcessing = false
    }

    /**
     * Non-blocking sample capture called from ExoPlayer audio thread.
     * Extracts samples and dispatches analysis to background worker to prevent audio thread starvation.
     */
    fun onAudioPcmBuffer(buffer: ByteBuffer, channelCount: Int, encoding: Int = C.ENCODING_PCM_16BIT) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < CAPTURE_INTERVAL_MS || isProcessing) {
            return
        }

        val remaining = buffer.remaining()
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        if (remaining < bytesPerSample * 2) return

        val totalSamples = remaining / bytesPerSample
        val samplesToCopy = min(totalSamples, MAX_SAMPLE_CACHE)
        if (samplesToCopy <= 0) return

        lastCaptureTimestamp = now

        val startPos = buffer.position()
        val ro = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val fb = ro.asFloatBuffer()
            for (i in 0 until samplesToCopy) {
                floatSampleCache[i] = fb.get().coerceIn(-1f, 1f)
            }
        } else {
            val sb = ro.asShortBuffer()
            for (i in 0 until samplesToCopy) {
                floatSampleCache[i] = sb.get().toFloat() / 32768f
            }
        }
        buffer.position(startPos)

        val channels = max(1, channelCount)

        isProcessing = true
        visualizerScope.launch {
            try {
                processSampleCache(samplesToCopy, channels)
            } finally {
                isProcessing = false
            }
        }
    }

    private fun processSampleCache(sampleCount: Int, channels: Int) {
        val monoCount = sampleCount / channels
        if (monoCount <= 0) return

        var sumSquares = 0.0
        var bassSum = 0.0
        var lowPassFilter = 0f
        val waveStep = max(1, monoCount / WAVEFORM_POINTS)
        val rawWave = FloatArray(WAVEFORM_POINTS)
        var waveIdx = 0

        // Fill FFT buffer with initial mono samples (zero-padded cleanly if monoCount < FFT_SIZE)
        val fftSamples = min(monoCount, FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            if (i < fftSamples) {
                var monoVal = 0f
                val baseIdx = i * channels
                for (ch in 0 until channels) {
                    if (baseIdx + ch < sampleCount) {
                        monoVal += floatSampleCache[baseIdx + ch]
                    }
                }
                monoVal /= channels
                fftReal[i] = monoVal * window[i]
            } else {
                fftReal[i] = 0f
            }
            fftImag[i] = 0f
        }

        // Pass over samples for RMS, smooth waveform, and gentle bass filter
        for (i in 0 until monoCount) {
            var monoVal = 0f
            val baseIdx = i * channels
            for (ch in 0 until channels) {
                if (baseIdx + ch < sampleCount) {
                    monoVal += floatSampleCache[baseIdx + ch]
                }
            }
            monoVal /= channels

            val sq = monoVal * monoVal
            sumSquares += sq

            // Smooth 1st-order IIR low-pass filter for warm sub/bass (~160Hz)
            lowPassFilter += 0.032f * (monoVal - lowPassFilter)
            bassSum += lowPassFilter * lowPassFilter

            if (i % waveStep == 0 && waveIdx < WAVEFORM_POINTS) {
                rawWave[waveIdx++] = monoVal.coerceIn(-1f, 1f)
            }
        }

        // Normalize energy (RMS) with gentle fluid EMA (zero choppy jumps)
        val rms = sqrt(sumSquares / monoCount).toFloat()
        smoothedEnergy = smoothEma(smoothedEnergy, (rms * 2.3f).coerceIn(0f, 1f), attackAlpha = 0.18f, releaseAlpha = 0.08f)

        // Continuous fluid bass envelope without abrupt transient spikes
        val bassRms = sqrt(bassSum / monoCount).toFloat() * 2.8f
        smoothedBass = smoothEma(smoothedBass, bassRms.coerceIn(0f, 1f), attackAlpha = 0.20f, releaseAlpha = 0.09f)

        // Smooth waveform temporally and spatially so wave paths flow like silk
        val spatialWave = spatialSmooth3(rawWave, wrap = false)
        for (i in 0 until WAVEFORM_POINTS) {
            currentWaveform[i] = currentWaveform[i] * 0.66f + spatialWave[i] * 0.34f
        }

        // Perform 512-point FFT (scale normalized cleanly against FFT size)
        computeFft(fftReal, fftImag, FFT_SIZE)

        val normFactor = 6.5f / sqrt(FFT_SIZE.toFloat())
        for (i in 0 until FFT_SIZE / 2) {
            val mag = sqrt(fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i])
            magnitudes[i] = (mag * normFactor).coerceIn(0f, 1f)
        }

        val targetBands = FloatArray(NUM_BANDS)
        for (bandIdx in 0 until NUM_BANDS) {
            val bins = BIN_MAP[bandIdx]
            var sum = 0f
            for (b in bins) {
                if (b < magnitudes.size) sum += magnitudes[b]
            }
            val avg = (sum / bins.size) * (0.95f + bandIdx * 0.045f)
            targetBands[bandIdx] = avg.coerceIn(0.05f, 1f)
        }

        val blendedTargets = spatialSmooth3(targetBands, wrap = false)
        for (bandIdx in 0 until NUM_BANDS) {
            currentBands[bandIdx] = smoothEma(
                previous = currentBands[bandIdx],
                target = blendedTargets[bandIdx],
                attackAlpha = 0.24f,
                releaseAlpha = 0.11f
            )
        }

        _energy.floatValue = smoothedEnergy
        _bassPulse.floatValue = smoothedBass
        _bands.value = currentBands.clone()
        _waveformData.value = currentWaveform.clone()
    }

    /**
     * Radix-2 Cooley-Tukey in-place FFT
     */
    private fun computeFft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey FFT
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val pos = i + k + half
                    val vR = real[pos] * wR - imag[pos] * wI
                    val vI = real[pos] * wI + imag[pos] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[pos] = uR - vR
                    imag[pos] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * ExoPlayer AudioProcessor that intercepts PCM stream without modifying audio
 */
class AudioVisualizerProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return

        val outBuffer = replaceOutputBuffer(count)
        val startPos = inputBuffer.position()

        // Safely peek audio samples in AudioReactor without blocking audio render thread
        try {
            AudioReactor.onAudioPcmBuffer(inputBuffer, inputAudioFormat.channelCount, inputAudioFormat.encoding)
        } catch (_: Throwable) {}

        // Pass through unmodified to audio output sink
        inputBuffer.position(startPos)
        outBuffer.put(inputBuffer)
        outBuffer.flip()
    }

    override fun onReset() {
        super.onReset()
        AudioReactor.reset()
    }
}
