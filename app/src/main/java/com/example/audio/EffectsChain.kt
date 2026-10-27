package com.example.audio

import kotlin.math.*

class EffectsChain(private val sampleRate: Int = 44100) {

    // EQ filters (Stereo L & R)
    val lowFilterL = BiquadFilter(sampleRate.toFloat())
    val lowFilterR = BiquadFilter(sampleRate.toFloat())
    val midFilterL = BiquadFilter(sampleRate.toFloat())
    val midFilterR = BiquadFilter(sampleRate.toFloat())
    val highFilterL = BiquadFilter(sampleRate.toFloat())
    val highFilterR = BiquadFilter(sampleRate.toFloat())

    // Sound Color FX filter
    val colorFilterL = BiquadFilter(sampleRate.toFloat())
    val colorFilterR = BiquadFilter(sampleRate.toFloat())

    // Delay buffer (up to 1.5s stereo)
    private val delayBufferSize = (sampleRate * 1.5).toInt()
    private val delayBufferL = FloatArray(delayBufferSize)
    private val delayBufferR = FloatArray(delayBufferSize)
    private var delayWriteIndex = 0

    // Flanger state
    private var flangerPhase = 0f

    // Reverb comb buffers
    private val comb1 = FloatArray((sampleRate * 0.035).toInt())
    private val comb2 = FloatArray((sampleRate * 0.042).toInt())
    private var c1Idx = 0
    private var c2Idx = 0

    // Bitcrusher state
    private var bcSampleHoldL = 0f
    private var bcSampleHoldR = 0f
    private var bcCounter = 0

    // Filter frequencies
    var eqLowDb: Float = 0f
        set(value) {
            field = value
            val gain = if (value <= -24f) -60f else value
            lowFilterL.setLowShelf(220f, gain)
            lowFilterR.setLowShelf(220f, gain)
        }

    var eqMidDb: Float = 0f
        set(value) {
            field = value
            val gain = if (value <= -24f) -60f else value
            midFilterL.setPeakingEQ(1200f, gain, 1.0f)
            midFilterR.setPeakingEQ(1200f, gain, 1.0f)
        }

    var eqHighDb: Float = 0f
        set(value) {
            field = value
            val gain = if (value <= -24f) -60f else value
            highFilterL.setHighShelf(3800f, gain)
            highFilterR.setHighShelf(3800f, gain)
        }

    // Color FX: -1.0 (Full LowPass) ... 0.0 (Bypass) ... +1.0 (Full HighPass)
    var colorFxAmount: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            if (field < -0.02f) {
                // Lowpass sweep from 20kHz down to 250Hz
                val cutoff = 250f + 19750f * (1f + field).pow(2.5f)
                colorFilterL.setLowPass(cutoff, 1.2f)
                colorFilterR.setLowPass(cutoff, 1.2f)
            } else if (field > 0.02f) {
                // Highpass sweep from 20Hz up to 5000Hz
                val cutoff = 20f + 4980f * field.pow(2.2f)
                colorFilterL.setHighPass(cutoff, 1.2f)
                colorFilterR.setHighPass(cutoff, 1.2f)
            }
        }

    // VST / Pad FX parameters
    var echoEnabled: Boolean = false
    var echoDelaySamples: Int = (sampleRate * 0.35).toInt() // default ~128 BPM 1/2 beat
    var echoFeedback: Float = 0.55f

    var reverbEnabled: Boolean = false
    var reverbMix: Float = 0.35f

    var flangerEnabled: Boolean = false
    var flangerRateHz: Float = 0.4f

    var bitcrushEnabled: Boolean = false
    var bitDepth: Int = 6
    var downsampleFactor: Int = 4

    init {
        eqLowDb = 0f
        eqMidDb = 0f
        eqHighDb = 0f
    }

    /**
     * Process stereo pair [inL, inR] in-place or returns processed pair.
     */
    fun process(inL: Float, inR: Float, out: FloatArray) {
        var l = inL
        var r = inR

        // 1. Color FX filter
        if (abs(colorFxAmount) > 0.02f) {
            l = colorFilterL.process(l)
            r = colorFilterR.process(r)
        }

        // 2. 3-Band Equalizer
        l = lowFilterL.process(l)
        l = midFilterL.process(l)
        l = highFilterL.process(l)

        r = lowFilterR.process(r)
        r = midFilterR.process(r)
        r = highFilterR.process(r)

        // 3. Bitcrusher
        if (bitcrushEnabled) {
            if (bcCounter % downsampleFactor == 0) {
                val levels = (1 shl bitDepth).toFloat()
                bcSampleHoldL = (round(l * levels) / levels)
                bcSampleHoldR = (round(r * levels) / levels)
            }
            bcCounter++
            l = bcSampleHoldL
            r = bcSampleHoldR
        }

        // 4. Flanger
        if (flangerEnabled) {
            flangerPhase += (2.0 * Math.PI * flangerRateHz / sampleRate).toFloat()
            if (flangerPhase > 2.0 * Math.PI) flangerPhase -= (2.0 * Math.PI).toFloat()
            val modOffset = ((sin(flangerPhase) * 0.5f + 0.5f) * (sampleRate * 0.005f)).toInt() + 10
            val readIdx = (delayWriteIndex - modOffset + delayBufferSize) % delayBufferSize
            l = (l + delayBufferL[readIdx] * 0.7f) * 0.75f
            r = (r + delayBufferR[readIdx] * 0.7f) * 0.75f
        }

        // 5. Echo / Tape Delay
        if (echoEnabled) {
            val readIdx = (delayWriteIndex - echoDelaySamples + delayBufferSize) % delayBufferSize
            val delayedL = delayBufferL[readIdx]
            val delayedR = delayBufferR[readIdx]

            delayBufferL[delayWriteIndex] = l + delayedL * echoFeedback
            delayBufferR[delayWriteIndex] = r + delayedR * echoFeedback

            l += delayedL * 0.6f
            r += delayedR * 0.6f
        } else {
            delayBufferL[delayWriteIndex] = l
            delayBufferR[delayWriteIndex] = r
        }
        delayWriteIndex = (delayWriteIndex + 1) % delayBufferSize

        // 6. Reverb (Schroeder comb simulation)
        if (reverbEnabled) {
            val c1Out = comb1[c1Idx]
            comb1[c1Idx] = l + c1Out * 0.75f
            c1Idx = (c1Idx + 1) % comb1.size

            val c2Out = comb2[c2Idx]
            comb2[c2Idx] = r + c2Out * 0.73f
            c2Idx = (c2Idx + 1) % comb2.size

            l = l * (1f - reverbMix) + (c1Out + c2Out) * 0.5f * reverbMix
            r = r * (1f - reverbMix) + (c2Out + c1Out) * 0.5f * reverbMix
        }

        out[0] = l
        out[1] = r
    }
}
