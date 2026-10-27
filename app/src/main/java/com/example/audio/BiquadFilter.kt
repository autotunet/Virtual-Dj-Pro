package com.example.audio

import kotlin.math.*

/**
 * Standard Direct Form 1 / Transposed Direct Form 2 Biquad Filter
 * for lowpass, highpass, peaking, and shelf equalizers.
 */
class BiquadFilter(private val sampleRate: Float = 44100f) {

    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    private var x1 = 0.0f
    private var x2 = 0.0f
    private var y1 = 0.0f
    private var y2 = 0.0f

    fun reset() {
        x1 = 0.0f
        x2 = 0.0f
        y1 = 0.0f
        y2 = 0.0f
    }

    fun setLowPass(frequency: Float, q: Float = 0.707f) {
        val f = frequency.coerceIn(20f, sampleRate * 0.45f)
        val omega = (2.0 * Math.PI * f / sampleRate).toFloat()
        val alpha = (sin(omega) / (2.0f * q)).toFloat()
        val cosOmega = cos(omega).toFloat()

        val a0 = 1.0f + alpha
        b0 = ((1.0f - cosOmega) / 2.0f) / a0
        b1 = (1.0f - cosOmega) / a0
        b2 = ((1.0f - cosOmega) / 2.0f) / a0
        a1 = (-2.0f * cosOmega) / a0
        a2 = (1.0f - alpha) / a0
    }

    fun setHighPass(frequency: Float, q: Float = 0.707f) {
        val f = frequency.coerceIn(20f, sampleRate * 0.45f)
        val omega = (2.0 * Math.PI * f / sampleRate).toFloat()
        val alpha = (sin(omega) / (2.0f * q)).toFloat()
        val cosOmega = cos(omega).toFloat()

        val a0 = 1.0f + alpha
        b0 = ((1.0f + cosOmega) / 2.0f) / a0
        b1 = -(1.0f + cosOmega) / a0
        b2 = ((1.0f + cosOmega) / 2.0f) / a0
        a1 = (-2.0f * cosOmega) / a0
        a2 = (1.0f - alpha) / a0
    }

    fun setPeakingEQ(frequency: Float, gainDb: Float, q: Float = 1.0f) {
        val f = frequency.coerceIn(20f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val omega = (2.0 * Math.PI * f / sampleRate).toFloat()
        val alpha = (sin(omega) / (2.0f * q)).toFloat()
        val cosOmega = cos(omega).toFloat()

        val a0 = 1.0f + alpha / a
        b0 = (1.0f + alpha * a) / a0
        b1 = (-2.0f * cosOmega) / a0
        b2 = (1.0f - alpha * a) / a0
        a1 = (-2.0f * cosOmega) / a0
        a2 = (1.0f - alpha / a) / a0
    }

    fun setLowShelf(frequency: Float, gainDb: Float) {
        val f = frequency.coerceIn(20f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val omega = (2.0 * Math.PI * f / sampleRate).toFloat()
        val cosOmega = cos(omega).toFloat()
        val sinOmega = sin(omega).toFloat()
        val alpha = sinOmega / 2.0f * sqrt(2.0f)

        val a0 = (a + 1.0f) + (a - 1.0f) * cosOmega + 2.0f * sqrt(a) * alpha
        b0 = (a * ((a + 1.0f) - (a - 1.0f) * cosOmega + 2.0f * sqrt(a) * alpha)) / a0
        b1 = (2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosOmega)) / a0
        b2 = (a * ((a + 1.0f) - (a - 1.0f) * cosOmega - 2.0f * sqrt(a) * alpha)) / a0
        a1 = (-2.0f * ((a - 1.0f) + (a + 1.0f) * cosOmega)) / a0
        a2 = ((a + 1.0f) + (a - 1.0f) * cosOmega - 2.0f * sqrt(a) * alpha) / a0
    }

    fun setHighShelf(frequency: Float, gainDb: Float) {
        val f = frequency.coerceIn(20f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val omega = (2.0 * Math.PI * f / sampleRate).toFloat()
        val cosOmega = cos(omega).toFloat()
        val sinOmega = sin(omega).toFloat()
        val alpha = sinOmega / 2.0f * sqrt(2.0f)

        val a0 = (a + 1.0f) - (a - 1.0f) * cosOmega + 2.0f * sqrt(a) * alpha
        b0 = (a * ((a + 1.0f) + (a - 1.0f) * cosOmega + 2.0f * sqrt(a) * alpha)) / a0
        b1 = (-2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosOmega)) / a0
        b2 = (a * ((a + 1.0f) + (a - 1.0f) * cosOmega - 2.0f * sqrt(a) * alpha)) / a0
        a1 = (2.0f * ((a - 1.0f) - (a + 1.0f) * cosOmega)) / a0
        a2 = ((a + 1.0f) - (a - 1.0f) * cosOmega - 2.0f * sqrt(a) * alpha) / a0
    }

    fun process(sample: Float): Float {
        val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = out
        return out
    }
}
