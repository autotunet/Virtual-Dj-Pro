package com.example.audio

import com.example.data.Track
import kotlin.math.*

class DeckAudioPlayer(val deckId: String, private val sampleRate: Int = 44100) {

    var currentTrack: Track? = null
        private set

    var pcmData: ShortArray? = null
        private set

    // Playback state
    var isPlaying = false
    var isCueing = false
    var cueSamplePosition = 0L

    var readPositionSamples = 0.0
    var slipPositionSamples = 0.0

    // Pitch & Tempo
    var pitchPercent = 0.0f // -8.0f to +8.0f
    var pitchRange = 8.0f   // 8%, 16%, 50%
    var masterTempoKeyLock = true
    var isSyncActive = false
    var isMasterDeck = false

    // Vinyl Scratch & Jog Wheel physics
    var isScratchMode = true // true = Vinyl Scratch Mode, false = CDJ Pitch Bend Mode
    var isScratching = false
    var scratchSpeed = 1.0f // <0 reverse, >0 forward
    var targetScratchSpeed = 0.0f
    var smoothedScratchSpeed = 0.0f
    private var motorSpinUpTotalFrames = 0
    private var motorSpinUpRemainingFrames = 0
    private var motorStartRate = 0.0f
    var jogAngle = 0.0f     // Degrees (0..360)
    var jogSpinVelocity = 0.0f

    // Loop & Slip
    var isSlipMode = false
    var isLoopActive = false
    var loopStartSamples = 0L
    var loopEndSamples = 0L
    var loopLengthBeats = 4

    // Volume & Gain
    var volumeFader = 0.85f // 0.0 to 1.0
    var gainTrim = 1.0f     // 0.0 to 2.0
    var isHeadphoneCue = false

    // DSP Effects Chain
    val effectsChain = EffectsChain(sampleRate)

    // VU Meter output (Peak amplitude 0.0 to 1.0)
    var peakMeterL = 0.0f
    var peakMeterR = 0.0f

    // Live audio signal buffer (64 samples for real-time oscilloscope waveform)
    val liveSignalBuffer = FloatArray(64)
    private var liveSignalCounter = 0

    fun getLiveSignalSnapshot(): FloatArray {
        return liveSignalBuffer.clone()
    }

    // 8 Hot Cues (in samples)
    val hotCuePositions = LongArray(8) { -1L }

    fun loadTrack(track: Track, buffer: ShortArray) {
        currentTrack = track
        pcmData = buffer
        readPositionSamples = 0.0
        slipPositionSamples = 0.0
        cueSamplePosition = 0L
        isPlaying = false
        isLoopActive = false

        // Initialize hot cues if track has any
        for (i in 0 until 8) {
            hotCuePositions[i] = if (i < track.hotCues.size) {
                ((track.hotCues[i] * sampleRate) / 1000L) * 2 // Stereo samples
            } else {
                -1L
            }
        }
    }

    val normalPlaybackRate: Double
        get() = 1.0 + (pitchPercent / 100.0)

    val playbackRate: Double
        get() {
            if (isScratching && isScratchMode) {
                return smoothedScratchSpeed.toDouble()
            }
            if (motorSpinUpRemainingFrames > 0 && isPlaying) {
                val progress = 1.0f - (motorSpinUpRemainingFrames.toFloat() / motorSpinUpTotalFrames.coerceAtLeast(1).toFloat())
                val target = normalPlaybackRate.toFloat()
                val curved = progress * progress // Turntable torque acceleration ramp
                return (motorStartRate + (target - motorStartRate) * curved).toDouble()
            }
            return normalPlaybackRate
        }

    val durationMs: Long
        get() = currentTrack?.durationMs ?: 0L

    val currentPositionMs: Long
        get() {
            val totalFrames = (pcmData?.size ?: 0) / 2
            if (totalFrames <= 0) return 0L
            val currentFrame = (readPositionSamples / 2).toLong().coerceIn(0, totalFrames.toLong())
            return (currentFrame * 1000L) / sampleRate
        }

    val effectiveBpm: Float
        get() {
            val baseBpm = currentTrack?.bpm ?: 128.0f
            return baseBpm * (1.0f + pitchPercent / 100.0f)
        }

    val beatPhase: Float
        get() {
            val bpm = effectiveBpm
            val secondsPerBeat = 60.0f / bpm
            val samplesPerBeat = secondsPerBeat * sampleRate * 2.0
            if (samplesPerBeat <= 0.0) return 0f
            val posInBeat = (readPositionSamples % samplesPerBeat) / samplesPerBeat
            return posInBeat.toFloat().coerceIn(0f, 1f)
        }

    val currentBeatInBar: Int
        get() {
            val bpm = effectiveBpm
            val secondsPerBeat = 60.0f / bpm
            val samplesPerBeat = secondsPerBeat * sampleRate * 2.0
            if (samplesPerBeat <= 0.0) return 1
            val totalBeats = (readPositionSamples / samplesPerBeat).toInt()
            val beat = (totalBeats % 4) + 1
            return if (beat in 1..4) beat else 1
        }

    fun play() {
        isPlaying = true
        isCueing = false
    }

    fun pause() {
        isPlaying = false
    }

    fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    fun cuePressed() {
        if (isPlaying) {
            // If playing, pressing cue jumps back to cue position and pauses
            isPlaying = false
            readPositionSamples = cueSamplePosition.toDouble()
        } else {
            // Set current position as new cue point, or play while holding
            cueSamplePosition = readPositionSamples.toLong()
            isCueing = true
            isPlaying = true
        }
    }

    fun cueReleased() {
        if (isCueing) {
            isCueing = false
            isPlaying = false
            readPositionSamples = cueSamplePosition.toDouble()
        }
    }

    fun seekTo(positionMs: Long) {
        val totalFrames = (pcmData?.size ?: 0) / 2
        if (totalFrames > 0) {
            val targetFrame = (positionMs.coerceAtLeast(0) * sampleRate) / 1000L
            readPositionSamples = (targetFrame * 2).toDouble().coerceIn(0.0, (pcmData?.size ?: 0).toDouble())
        }
    }

    fun scrubDelta(deltaMs: Long) {
        val deltaSamples = (deltaMs * sampleRate * 2L) / 1000L
        val maxSamples = (pcmData?.size ?: 0).toDouble()
        readPositionSamples = (readPositionSamples + deltaSamples).coerceIn(0.0, maxSamples)
    }

    fun jumpToHotCue(index: Int) {
        if (index in 0..7) {
            val pos = hotCuePositions[index]
            if (pos >= 0) {
                readPositionSamples = pos.toDouble()
                if (!isPlaying) play()
            } else {
                // Set hot cue at current position
                hotCuePositions[index] = readPositionSamples.toLong()
            }
        }
    }

    fun clearHotCue(index: Int) {
        if (index in 0..7) {
            hotCuePositions[index] = -1L
        }
    }

    fun setAutoLoop(beats: Int) {
        val bpm = effectiveBpm
        val secondsPerBeat = 60.0f / bpm
        val samplesPerBeat = (secondsPerBeat * sampleRate * 2).toLong()

        loopLengthBeats = beats
        loopStartSamples = (readPositionSamples / samplesPerBeat).toLong() * samplesPerBeat
        loopEndSamples = loopStartSamples + (beats * samplesPerBeat)
        isLoopActive = true
    }

    fun exitLoop() {
        isLoopActive = false
    }

    fun toggleLoop() {
        if (isLoopActive) exitLoop() else setAutoLoop(loopLengthBeats)
    }

    fun halveLoop() {
        if (loopLengthBeats > 1) {
            setAutoLoop(loopLengthBeats / 2)
        }
    }

    fun doubleLoop() {
        if (loopLengthBeats < 32) {
            setAutoLoop(loopLengthBeats * 2)
        }
    }

    fun onJogTouchDown() {
        if (isScratchMode) {
            isScratching = true
            targetScratchSpeed = 0.0f
            smoothedScratchSpeed = 0.0f
            scratchSpeed = 0.0f
            motorSpinUpRemainingFrames = 0
            if (isSlipMode) {
                slipPositionSamples = readPositionSamples
            }
        }
    }

    fun onJogTouchMove(deltaDegrees: Float) {
        jogAngle = (jogAngle + deltaDegrees) % 360f
        if (jogAngle < 0f) jogAngle += 360f
        jogSpinVelocity = deltaDegrees

        if (isScratchMode) {
            isScratching = true
            // Emulate classic vinyl scratch rate: proportional to rotational velocity
            // Forward rotation speeds up (+ rate), backward rotation plays in reverse (- rate)
            val dynamicRate = (deltaDegrees * 1.6f).coerceIn(-4.5f, 4.5f)
            targetScratchSpeed = dynamicRate
            scratchSpeed = dynamicRate
        } else {
            // CDJ Pitch Bend Mode (nudge): Does not stop or scratch audio, allows tempo nudge
            val bendFactor = (deltaDegrees * 0.04f).coerceIn(-0.6f, 0.6f)
            readPositionSamples += (bendFactor * sampleRate * 0.04)
        }
    }

    fun onJogTouchUp() {
        if (isScratchMode) {
            isScratching = false
            if (isSlipMode) {
                // Resume from background slip position!
                readPositionSamples = slipPositionSamples
            } else if (isPlaying) {
                // Turntable motor recovery ramp (~120ms spin-up from scratch speed to normal rate)
                motorSpinUpTotalFrames = (sampleRate * 0.12f).toInt()
                motorSpinUpRemainingFrames = motorSpinUpTotalFrames
                motorStartRate = smoothedScratchSpeed
            }
        }
    }

    fun pitchNudge(factor: Float) {
        // Outer ring pitch bend: momentarily speed up or slow down
        readPositionSamples += (factor * sampleRate * 0.08f)
    }

    fun syncTo(targetBpm: Float, targetBeatPhase: Float) {
        val baseBpm = currentTrack?.bpm ?: 128f
        // Adjust pitch to match BPM
        val diffBpm = targetBpm - baseBpm
        pitchPercent = ((diffBpm / baseBpm) * 100f).coerceIn(-pitchRange, pitchRange)

        // Phase align
        val secondsPerBeat = 60.0f / targetBpm
        val samplesPerBeat = (secondsPerBeat * sampleRate * 2.0)
        val currentBeat = (readPositionSamples / samplesPerBeat).toLong()
        readPositionSamples = (currentBeat + targetBeatPhase) * samplesPerBeat
    }

    /**
     * Reads next stereo audio frame [outL, outR] with volume, gain and effects.
     */
    fun nextFrame(tempFrame: FloatArray, outFrame: FloatArray): Boolean {
        val buffer = pcmData ?: return false
        val bufferSize = buffer.size

        if (bufferSize == 0) return false

        // Update jog spin animation if spinning
        if (!isScratching && isPlaying) {
            val degPerFrame = (360f * (effectiveBpm / 60f) * 0.5f) / sampleRate
            jogAngle = (jogAngle + degPerFrame) % 360f
        }

        // Interpolate smoothed scratch speed towards target for fluid analog vinyl scratch sound
        if (isScratching && isScratchMode) {
            smoothedScratchSpeed += (targetScratchSpeed - smoothedScratchSpeed) * 0.035f
            scratchSpeed = smoothedScratchSpeed
            targetScratchSpeed *= 0.9995f // Platter friction deceleration if finger rests
        }

        // Turntable motor spin-up frame counter
        if (motorSpinUpRemainingFrames > 0) {
            motorSpinUpRemainingFrames--
        }

        if (!isPlaying && !isScratching && abs(jogSpinVelocity) < 0.01f) {
            outFrame[0] = 0f
            outFrame[1] = 0f
            peakMeterL = peakMeterL * 0.92f
            peakMeterR = peakMeterR * 0.92f
            if (++liveSignalCounter % 4 == 0) {
                val idx = (liveSignalCounter / 4) % liveSignalBuffer.size
                liveSignalBuffer[idx] *= 0.85f
            }
            return false
        }

        // Apply inertia spin if released with velocity
        if (!isScratching && abs(jogSpinVelocity) > 0.05f) {
            if (isScratchMode && !isPlaying) {
                readPositionSamples += jogSpinVelocity * 6.0
                jogSpinVelocity *= 0.96f
            } else if (!isScratchMode) {
                readPositionSamples += jogSpinVelocity * 4.0
                jogSpinVelocity *= 0.92f
            } else {
                jogSpinVelocity *= 0.90f
            }
        }

        // Slip tracking
        if (isSlipMode) {
            val rate = 1.0 + (pitchPercent / 100.0)
            slipPositionSamples += rate * 2.0
            if (slipPositionSamples >= bufferSize) slipPositionSamples %= bufferSize
        }

        val rate = playbackRate
        readPositionSamples += rate * 2.0

        // Handle loop wrap
        if (isLoopActive && loopEndSamples > loopStartSamples) {
            if (readPositionSamples >= loopEndSamples) {
                readPositionSamples = loopStartSamples.toDouble() + (readPositionSamples - loopEndSamples)
            } else if (readPositionSamples < loopStartSamples) {
                readPositionSamples = loopStartSamples.toDouble()
            }
        } else {
            // Normal track wrap / loop
            if (readPositionSamples >= bufferSize - 2) {
                readPositionSamples %= bufferSize
            } else if (readPositionSamples < 0) {
                readPositionSamples = (bufferSize + (readPositionSamples % bufferSize))
            }
        }

        // High fidelity linear sample interpolation
        val sampleIndex = (readPositionSamples / 2).toInt() * 2
        val frac = (readPositionSamples - sampleIndex) / 2.0

        val idx0 = sampleIndex.coerceIn(0, bufferSize - 2)
        val idx1 = ((sampleIndex + 2) % bufferSize).coerceIn(0, bufferSize - 2)

        val s0L = buffer[idx0] / 32768.0f
        val s0R = buffer[idx0 + 1] / 32768.0f

        val s1L = buffer[idx1] / 32768.0f
        val s1R = buffer[idx1 + 1] / 32768.0f

        var frameL = (s0L + (s1L - s0L) * frac).toFloat()
        var frameR = (s0R + (s1R - s0R) * frac).toFloat()

        // Apply DSP Effects (EQ, Color Filter, Echo, Reverb, Flanger, Bitcrush)
        effectsChain.process(frameL, frameR, tempFrame)
        frameL = tempFrame[0]
        frameR = tempFrame[1]

        // Channel Gain & Volume Fader
        val gain = gainTrim * volumeFader
        frameL *= gain
        frameR *= gain

        // Peak meters
        val absL = abs(frameL)
        val absR = abs(frameR)
        peakMeterL = max(absL, peakMeterL * 0.96f)
        peakMeterR = max(absR, peakMeterR * 0.96f)

        // Capture live physical audio signal for visual waveform display (oscilloscope)
        if (++liveSignalCounter % 4 == 0) {
            val idx = (liveSignalCounter / 4) % liveSignalBuffer.size
            liveSignalBuffer[idx] = (frameL + frameR) * 0.5f
        }

        outFrame[0] = frameL
        outFrame[1] = frameR
        return true
    }
}
