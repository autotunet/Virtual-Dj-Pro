package com.example.audio

import kotlin.math.*
import java.util.Random

enum class DrumSound(val displayName: String, val category: String) {
    CRASH_PIONEER("Pioneer Crash", "Cymbals"),
    RIDE_PIONEER("Pioneer Ride", "Cymbals"),
    HIHAT_CLOSED("Closed Hat", "Hi-Hats"),
    HIHAT_OPEN("Open Hat", "Hi-Hats"),
    KICK_808("808 Sub Kick", "Kicks"),
    KICK_909("909 Club Kick", "Kicks"),
    SNARE_TRAP("Trap Snare", "Snares"),
    CLAP("Hand Clap", "Perc"),
    ELECTRO_TOM("Electro Tom", "Toms"),
    RIMSHOT("Rimshot", "Perc"),
    AIRHORN("DJ Airhorn", "FX"),
    SCRATCH_DROP("Vinyl Scratch", "FX")
}

class DrumSynthEngine(private val sampleRate: Int = 44100) {

    private val random = Random()
    private val precomputedSounds = mutableMapOf<DrumSound, ShortArray>()

    init {
        // Pre-render all electronic percussion and Pioneer cymbals into high-fidelity PCM buffers
        for (sound in DrumSound.values()) {
            precomputedSounds[sound] = generateSound(sound)
        }
    }

    fun getSample(sound: DrumSound): ShortArray {
        return precomputedSounds[sound] ?: ShortArray(0)
    }

    private fun generateSound(sound: DrumSound): ShortArray {
        return when (sound) {
            DrumSound.CRASH_PIONEER -> generateCrashCymbal()
            DrumSound.RIDE_PIONEER -> generateRideCymbal()
            DrumSound.HIHAT_CLOSED -> generateClosedHiHat()
            DrumSound.HIHAT_OPEN -> generateOpenHiHat()
            DrumSound.KICK_808 -> generateKick808()
            DrumSound.KICK_909 -> generateKick909()
            DrumSound.SNARE_TRAP -> generateSnare()
            DrumSound.CLAP -> generateClap()
            DrumSound.ELECTRO_TOM -> generateTom()
            DrumSound.RIMSHOT -> generateRimshot()
            DrumSound.AIRHORN -> generateAirhorn()
            DrumSound.SCRATCH_DROP -> generateScratchDrop()
        }
    }

    // High fidelity Pioneer Crash Cymbal: metallic inharmonic frequencies + highpass noise decay
    private fun generateCrashCymbal(): ShortArray {
        val duration = 1.6f // 1.6 seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        val metalFreqs = floatArrayOf(587f, 845f, 1200f, 1850f, 2900f, 4100f, 6200f)

        var noiseState = 0f
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val decay = exp(-t * 3.2f)
            val initialAttack = min(1.0f, (i / 150f))

            // Inharmonic square/metal wave synthesis
            var metal = 0f
            for (f in metalFreqs) {
                metal += sin(2.0 * Math.PI * f * t).toFloat() * 0.15f
            }

            // High frequency sizzle noise
            val whiteNoise = (random.nextFloat() * 2f - 1f)
            noiseState = noiseState * 0.4f + whiteNoise * 0.6f

            val sampleVal = (metal * 0.4f + noiseState * 0.6f) * decay * initialAttack
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    // Pioneer Ride Cymbal: distinct bell attack + crisp ping + resonant metallic ring
    private fun generateRideCymbal(): ShortArray {
        val duration = 1.2f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        val bellFreqs = floatArrayOf(820f, 1340f, 2150f, 3200f)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val decay = exp(-t * 4.0f)
            val bellDecay = exp(-t * 8.0f)
            val ping = if (i < 400) (random.nextFloat() * 2f - 1f) * (1f - i / 400f) else 0f

            var bell = 0f
            for (f in bellFreqs) {
                bell += sin(2.0 * Math.PI * f * t).toFloat() * 0.25f
            }

            val sampleVal = (bell * bellDecay * 0.6f + ping * 0.4f + bell * decay * 0.3f)
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateClosedHiHat(): ShortArray {
        val numSamples = (sampleRate * 0.06f).toInt()
        val buffer = ShortArray(numSamples)
        var hp = 0f
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val decay = exp(-t * 60f)
            val noise = (random.nextFloat() * 2f - 1f)
            hp = noise - 0.7f * hp
            val sampleVal = hp * decay
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateOpenHiHat(): ShortArray {
        val numSamples = (sampleRate * 0.35f).toInt()
        val buffer = ShortArray(numSamples)
        var hp = 0f
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val decay = exp(-t * 9.5f)
            val noise = (random.nextFloat() * 2f - 1f)
            hp = noise - 0.65f * hp
            val metal = sin(2.0 * Math.PI * 7200 * t).toFloat() * 0.2f
            val sampleVal = (hp * 0.8f + metal) * decay
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateKick808(): ShortArray {
        val duration = 0.55f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            // Exponential pitch drop from 180Hz to 48Hz
            val freq = 48.0 + 132.0 * exp(-t * 22.0)
            val phase = 2.0 * Math.PI * freq * t
            val decay = exp(-t * 6.5f)
            val click = if (i < 120) (1.0f - i / 120f) * 0.4f else 0.0f
            val sampleVal = sin(phase).toFloat() * decay + click
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32700).toInt().toShort()
        }
        return buffer
    }

    private fun generateKick909(): ShortArray {
        val duration = 0.38f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq = 55.0 + 190.0 * exp(-t * 30.0)
            val phase = 2.0 * Math.PI * freq * t
            val decay = exp(-t * 9.0f)
            val punch = if (i < 200) (1.0f - i / 200f) * 0.5f else 0f
            val sampleVal = sin(phase).toFloat() * decay + punch
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32700).toInt().toShort()
        }
        return buffer
    }

    private fun generateSnare(): ShortArray {
        val duration = 0.3f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val toneFreq = 185.0 * exp(-t * 8.0)
            val tone = sin(2.0 * Math.PI * toneFreq * t).toFloat() * exp(-t * 18.0f)
            val noise = (random.nextFloat() * 2f - 1f) * exp(-t * 14.0f)
            val sampleVal = tone * 0.45f + noise * 0.65f
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateClap(): ShortArray {
        val duration = 0.32f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val burst = when {
                t < 0.012 -> 0.8f
                t < 0.024 -> 0.85f
                t < 0.038 -> 1.0f
                else -> exp(-(t - 0.038f) * 16.0f)
            }
            val noise = (random.nextFloat() * 2f - 1f)
            val sampleVal = noise * burst
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 31000).toInt().toShort()
        }
        return buffer
    }

    private fun generateTom(): ShortArray {
        val duration = 0.4f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq = 120.0 + 160.0 * exp(-t * 14.0)
            val sampleVal = sin(2.0 * Math.PI * freq * t).toFloat() * exp(-t * 9.0f)
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateRimshot(): ShortArray {
        val duration = 0.09f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val wood = sin(2.0 * Math.PI * 1750 * t).toFloat() * exp(-t * 50f)
            val click = if (i < 80) (random.nextFloat() * 2f - 1f) * 0.6f else 0f
            val sampleVal = wood * 0.7f + click * 0.5f
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateAirhorn(): ShortArray {
        val duration = 0.75f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        // Reggae/DJ Airhorn multi-tone fanfare burst
        val freqs = floatArrayOf(466.16f, 554.37f, 622.25f) // Bb4, Db5, Eb5
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            // Pulse envelope (3 bursts)
            val cycle = (t * 4.5f) % 1.0f
            val pulse = if (cycle < 0.65f) 1.0f else 0.1f
            var wave = 0f
            for (f in freqs) {
                // Bright sawtooth approximation
                wave += sin(2.0 * Math.PI * f * t).toFloat() * 0.3f
                wave += sin(2.0 * Math.PI * (f * 2) * t).toFloat() * 0.15f
            }
            val decay = exp(-t * 1.5f)
            val sampleVal = wave * pulse * decay
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateScratchDrop(): ShortArray {
        val duration = 0.5f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            // Forward-backward vinyl speed variation
            val speed = sin(2.0 * Math.PI * 3.5 * t).toFloat()
            val freq = 440f * (0.8f + 1.2f * abs(speed))
            val noise = (random.nextFloat() * 2f - 1f) * 0.15f
            val tone = sin(2.0 * Math.PI * freq * t).toFloat() * 0.7f
            val sampleVal = (tone + noise) * (1f - t / duration)
            buffer[i] = (sampleVal.coerceIn(-1.0f, 1.0f) * 30000).toInt().toShort()
        }
        return buffer
    }
}
