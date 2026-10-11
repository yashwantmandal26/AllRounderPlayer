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
    private var smoothedEnergy = 0f
    private var smoothedBass = 0f
    private var runningBassAverage = 0.05f
    private var lastBeatTimestamp = 0L
    private var internalBassPulse = 0f

    // FFT scratch arrays (128-point real FFT)
    private const val FFT_SIZE = 128
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val magnitudes = FloatArray(FFT_SIZE / 2)
    private val window = FloatArray(FFT_SIZE) { i ->
        // Hann window
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    private var lastCaptureTimestamp = 0L
    private const val CAPTURE_INTERVAL_MS = 25L // ~40 fps target for smooth visualizer

    // Preallocated scratch buffer for async processing
    private const val MAX_SAMPLE_CACHE = 1024
    private val sampleCache = ShortArray(MAX_SAMPLE_CACHE)
    @Volatile
    private var isProcessing = false

    // Static singleton logarithmic frequency bins
    private val BIN_MAP = arrayOf(
        intArrayOf(1),
        intArrayOf(2),
        intArrayOf(3),
        intArrayOf(4),
        intArrayOf(5, 6),
        intArrayOf(7, 8),
        intArrayOf(9, 10),
        intArrayOf(11, 13),
        intArrayOf(14, 17),
        intArrayOf(18, 22),
        intArrayOf(23, 27),
        intArrayOf(28, 33),
        intArrayOf(34, 40),
        intArrayOf(41, 48),
        intArrayOf(49, 56),
        intArrayOf(57, 63)
    )

    fun reset() {
        _energy.floatValue = 0f
        _bassPulse.floatValue = 0f
        for (i in 0 until NUM_BANDS) {
            currentBands[i] = 0f
        }
        _bands.value = FloatArray(NUM_BANDS) { 0f }
        _waveformData.value = FloatArray(WAVEFORM_POINTS) { 0f }
        smoothedEnergy = 0f
        smoothedBass = 0f
        internalBassPulse = 0f
        runningBassAverage = 0.05f
        lastCaptureTimestamp = 0L
        isProcessing = false
    }

    /**
     * Non-blocking sample capture called from ExoPlayer audio thread.
     * Extracts samples and dispatches analysis to background worker to prevent audio thread starvation.
     */
    fun onAudioPcmBuffer(buffer: ByteBuffer, channelCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < CAPTURE_INTERVAL_MS || isProcessing) {
            return
        }

        val remaining = buffer.remaining()
        if (remaining < 4) return

        val shortCount = remaining / 2
        val samplesToCopy = min(shortCount, MAX_SAMPLE_CACHE)
        if (samplesToCopy <= 0) return

        lastCaptureTimestamp = now

        val startPos = buffer.position()
        val shortBuffer = buffer.asShortBuffer()
        shortBuffer.get(sampleCache, 0, samplesToCopy)
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
        val newWave = FloatArray(WAVEFORM_POINTS)
        var waveIdx = 0

        // Fill FFT buffer with initial mono samples
        val fftSamples = min(monoCount, FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            if (i < fftSamples) {
                var monoVal = 0f
                val baseIdx = i * channels
                for (ch in 0 until channels) {
                    if (baseIdx + ch < sampleCount) {
                        monoVal += sampleCache[baseIdx + ch].toFloat() / 32768f
                    }
                }
                monoVal /= channels
                fftReal[i] = monoVal * window[i]
            } else {
                fftReal[i] = 0f
            }
            fftImag[i] = 0f
        }

        // Pass over samples for RMS, waveform, and Bass filter
        for (i in 0 until monoCount) {
            var monoVal = 0f
            val baseIdx = i * channels
            for (ch in 0 until channels) {
                if (baseIdx + ch < sampleCount) {
                    monoVal += sampleCache[baseIdx + ch].toFloat() / 32768f
                }
            }
            monoVal /= channels

            val sq = monoVal * monoVal
            sumSquares += sq

            // Simple 1st-order IIR low-pass filter for sub/bass (~180Hz)
            lowPassFilter += 0.035f * (monoVal - lowPassFilter)
            bassSum += lowPassFilter * lowPassFilter

            if (i % waveStep == 0 && waveIdx < WAVEFORM_POINTS) {
                newWave[waveIdx++] = monoVal.coerceIn(-1f, 1f)
            }
        }

        // Normalize energy (RMS)
        val rms = sqrt(sumSquares / monoCount).toFloat()
        smoothedEnergy = smoothedEnergy * 0.7f + (rms * 2.6f).coerceIn(0f, 1f) * 0.3f

        // Bass Energy & Kick Transient Detection
        val bassRms = sqrt(bassSum / monoCount).toFloat() * 3.5f
        smoothedBass = smoothedBass * 0.65f + bassRms.coerceIn(0f, 1f) * 0.35f

        runningBassAverage = runningBassAverage * 0.94f + smoothedBass * 0.06f
        val now = System.currentTimeMillis()
        var currentPulse = internalBassPulse * 0.86f // natural spring decay

        // Transient spike threshold for kick drum / heavy bass drop
        if (smoothedBass > runningBassAverage * 1.32f && smoothedBass > 0.12f && (now - lastBeatTimestamp) > 160L) {
            currentPulse = (smoothedBass * 1.5f).coerceIn(0.6f, 1.2f)
            lastBeatTimestamp = now
        }
        internalBassPulse = currentPulse

        // Perform 128-point FFT
        computeFft(fftReal, fftImag, FFT_SIZE)

        // Map 64 FFT magnitudes into 16 logarithmic frequency bands
        for (i in 0 until FFT_SIZE / 2) {
            val mag = sqrt(fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i])
            magnitudes[i] = (mag * 3.8f).coerceIn(0f, 1f)
        }

        for (bandIdx in 0 until NUM_BANDS) {
            val bins = BIN_MAP[bandIdx]
            var sum = 0f
            for (b in bins) {
                if (b < magnitudes.size) sum += magnitudes[b]
            }
            val avg = (sum / bins.size) * (1f + bandIdx * 0.05f) // high-freq boost for visual balance
            val target = avg.coerceIn(0.04f, 1f)

            // Instant attack, smooth gravity decay
            if (target > currentBands[bandIdx]) {
                currentBands[bandIdx] = target
            } else {
                currentBands[bandIdx] = currentBands[bandIdx] * 0.82f + target * 0.18f
            }
        }

        _energy.floatValue = smoothedEnergy
        _bassPulse.floatValue = currentPulse
        _bands.value = currentBands.clone()
        _waveformData.value = newWave
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
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return

        val outBuffer = replaceOutputBuffer(count)
        val startPos = inputBuffer.position()

        // Asynchronously peek audio samples in AudioReactor without blocking audio render thread
        AudioReactor.onAudioPcmBuffer(inputBuffer, inputAudioFormat.channelCount)

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
