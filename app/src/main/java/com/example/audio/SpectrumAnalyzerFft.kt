package com.example.audio

import kotlin.math.*

/**
 * Real-time 32-band logarithmic audio spectrum analyzer using a Cooley-Tukey FFT.
 * Analyzes mixed audio PCM output and produces 32 normalized frequency bands (0.0 to 1.0)
 * matching human auditory perception across 20 Hz to 20,000 Hz.
 */
class SpectrumAnalyzerFft(
    private val fftSize: Int = 256,
    private val sampleRate: Int = 44100
) {
    private val numBands = 32
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val window = FloatArray(fftSize)
    private val bandFrequencies = FloatArray(numBands + 1)
    private val currentBands = FloatArray(numBands)
    private val peakBands = FloatArray(numBands)

    init {
        // Precompute Hann window to minimize spectral leakage
        for (i in 0 until fftSize) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }

        // Logarithmically spaced frequency band edges from 25 Hz to 20,000 Hz
        val minFreq = 25.0
        val maxFreq = 20000.0
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        for (i in 0..numBands) {
            val freq = exp(logMin + (logMax - logMin) * (i.toDouble() / numBands))
            bandFrequencies[i] = freq.toFloat()
        }
    }

    /**
     * Analyzes interleaved stereo PCM samples and updates [outBands] (32 bands, 0.0 to 1.0).
     */
    fun process(pcmBuffer: ShortArray, frameCount: Int, outBands: FloatArray) {
        val samplesToTake = min(fftSize, frameCount)
        if (samplesToTake <= 0) return

        // Extract mono sum and apply window
        for (i in 0 until samplesToTake) {
            val idx = i * 2
            val sampleL = if (idx < pcmBuffer.size) pcmBuffer[idx] / 32768.0f else 0f
            val sampleR = if (idx + 1 < pcmBuffer.size) pcmBuffer[idx + 1] / 32768.0f else 0f
            val mono = (sampleL + sampleR) * 0.5f
            real[i] = mono * window[i]
            imag[i] = 0f
        }
        for (i in samplesToTake until fftSize) {
            real[i] = 0f
            imag[i] = 0f
        }

        // Radix-2 In-place FFT
        fft(real, imag, fftSize)

        // Compute magnitude spectrum for positive frequencies (0 .. fftSize/2)
        val halfSize = fftSize / 2
        val binWidthHz = sampleRate.toFloat() / fftSize
        val magnitudes = FloatArray(halfSize)
        for (i in 0 until halfSize) {
            val r = real[i]
            val im = imag[i]
            val mag = sqrt(r * r + im * im) * 2.0f / fftSize
            magnitudes[i] = mag
        }

        // Accumulate FFT bins into 32 log-spaced perceptual bands
        for (b in 0 until numBands) {
            val startFreq = bandFrequencies[b]
            val endFreq = bandFrequencies[b + 1]

            val startBin = (startFreq / binWidthHz).toInt().coerceIn(0, halfSize - 1)
            val endBin = ceil(endFreq / binWidthHz).toInt().coerceIn(startBin + 1, halfSize)

            var bandSum = 0f
            var binCount = 0
            for (bin in startBin until endBin) {
                bandSum += magnitudes[bin]
                binCount++
            }
            val avgMag = if (binCount > 0) bandSum / binCount else magnitudes[startBin]

            // Convert to dB-like scale with perceptual tilt
            // Bass frequencies need less boost, high frequencies have lower energy so apply high shelf curve
            val tilt = 1.0f + (b.toFloat() / numBands) * 1.8f
            val rawBandVal = (avgMag * 7.5f * tilt)

            // Smooth ballistics: punchy attack, natural fluid decay
            val prev = currentBands[b]
            val target = rawBandVal.coerceIn(0.02f, 1.0f)
            val smoothed = if (target > prev) {
                prev * 0.25f + target * 0.75f // Fast punchy attack
            } else {
                prev * 0.86f + target * 0.14f // Smooth decay
            }
            currentBands[b] = smoothed
            outBands[b] = smoothed
        }
    }

    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey decimation-in-time
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = (-2.0 * PI / len).toFloat()
            val wStepR = cos(angle)
            val wStepI = sin(angle)

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos = i + k
                    val match = pos + halfLen
                    val tr = wR * real[match] - wI * imag[match]
                    val ti = wR * imag[match] + wI * real[match]

                    real[match] = real[pos] - tr
                    imag[match] = imag[pos] - ti
                    real[pos] += tr
                    imag[pos] += ti

                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len *= 2
        }
    }
}
