package com.example.audio

import com.example.data.Track
import kotlin.math.*
import java.util.Random

object ProceduralAudioFactory {

    private val random = Random(42)

    fun createDefaultTracks(): List<Track> {
        return listOf(
            createTrackWithBands(
                id = "track_1",
                title = "Tokyo Neon (Club Anthem)",
                artist = "Pioneer Studio Crew",
                genre = "Tech House / Club",
                bpm = 128.0f,
                musicalKey = "8A (Am)",
                durationMs = 60000L,
                hotCues = mutableListOf(0L, 7500L, 15000L, 30000L)
            ),
            createTrackWithBands(
                id = "track_2",
                title = "Berlin Underground (Acid Melodic)",
                artist = "Klangwerk",
                genre = "Melodic Techno",
                bpm = 126.0f,
                musicalKey = "9A (Em)",
                durationMs = 60000L,
                hotCues = mutableListOf(0L, 7619L, 15238L, 30476L)
            ),
            createTrackWithBands(
                id = "track_3",
                title = "Miami Drift (808 Future Trap)",
                artist = "Sub Heavy",
                genre = "Trap / Bass",
                bpm = 140.0f,
                musicalKey = "4A (Fm)",
                durationMs = 60000L,
                hotCues = mutableListOf(0L, 6857L, 13714L, 27428L)
            ),
            createTrackWithBands(
                id = "track_4",
                title = "Hyperdrive (London 174)",
                artist = "Veloce",
                genre = "Drum & Bass",
                bpm = 174.0f,
                musicalKey = "11B (A)",
                durationMs = 60000L,
                hotCues = mutableListOf(0L, 5517L, 11034L, 22068L)
            ),
            createTrackWithBands(
                id = "track_5",
                title = "Solaris (Ibiza Sunset Deep)",
                artist = "Oasis Sounds",
                genre = "Afro Deep House",
                bpm = 124.0f,
                musicalKey = "7A (Dm)",
                durationMs = 60000L,
                hotCues = mutableListOf(0L, 7741L, 15483L, 30967L)
            )
        )
    }

    private fun createTrackWithBands(
        id: String,
        title: String,
        artist: String,
        genre: String,
        bpm: Float,
        musicalKey: String,
        durationMs: Long,
        hotCues: MutableList<Long>
    ): Track {
        val numPoints = 256
        val peaks = FloatArray(numPoints)
        val bass = FloatArray(numPoints)
        val mids = FloatArray(numPoints)
        val highs = FloatArray(numPoints)

        for (i in 0 until numPoints) {
            val progress = i.toFloat() / numPoints
            val sectionEnergy = when {
                progress < 0.20f -> 0.65f // Intro groove
                progress < 0.40f -> 0.45f + 0.4f * ((progress - 0.20f) / 0.20f) // Build-up
                progress < 0.80f -> 0.95f // Drop / Peak energy
                else -> 0.55f // Outro
            }

            val isKickBeat = (i % 4 == 0)
            val isSnareBeat = (i % 4 == 2)
            val isOffbeat = (i % 2 == 1)

            // Bass: heavy on kicks, drops in breakdown, explosions on drop
            val bVal = if (progress in 0.30f..0.39f) 0.15f else (if (isKickBeat) 0.95f else 0.35f) * sectionEnergy
            bass[i] = bVal.coerceIn(0.1f, 1.0f)

            // Mids: melodic synths, snares and claps
            val mVal = ((if (isSnareBeat) 0.85f else 0.45f) + 0.2f * sin(progress * 40f)) * sectionEnergy
            mids[i] = mVal.coerceIn(0.15f, 0.95f)

            // Highs: crisp hi-hats, rides, white noise sweeps
            val hVal = ((if (isOffbeat) 0.8f else 0.3f) + 0.25f * (progress)) * sectionEnergy
            highs[i] = hVal.coerceIn(0.2f, 1.0f)

            // Combined composite peak
            peaks[i] = max(bass[i], max(mids[i], highs[i]))
        }

        return Track(
            id = id,
            title = title,
            artist = artist,
            genre = genre,
            bpm = bpm,
            musicalKey = musicalKey,
            durationMs = durationMs,
            waveformPeaks = peaks,
            waveformBass = bass,
            waveformMids = mids,
            waveformHighs = highs,
            hotCues = hotCues
        )
    }

    /**
     * Generates a stereo 16-bit PCM buffer (L, R interleaved) at 44.1kHz
     * for a 16-bar looping dance groove.
     */
    fun renderPcmBuffer(track: Track, sampleRate: Int = 44100): ShortArray {
        val bpm = track.bpm
        val secondsPerBeat = 60.0 / bpm
        // 16 bars = 64 beats
        val numBeats = 64
        val totalSeconds = numBeats * secondsPerBeat
        val totalStereoSamples = (totalSeconds * sampleRate).toInt() * 2 // L and R
        val buffer = ShortArray(totalStereoSamples)

        val samplesPerBeat = (secondsPerBeat * sampleRate).toInt()
        val baseFreq = when (track.id) {
            "track_1" -> 110.0 // A2
            "track_2" -> 82.4  // E2
            "track_3" -> 87.3  // F2
            "track_4" -> 110.0 // A2
            else -> 73.4       // D2
        }

        // Synthesize multi-layered track
        var sampleIdx = 0
        val totalFrames = totalStereoSamples / 2

        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val beatPos = frame.toDouble() / samplesPerBeat
            val currentBeat = beatPos.toInt()
            val beatFraction = beatPos - currentBeat

            var left = 0.0
            var right = 0.0

            // 1. Kick Drum (On beats 0, 1, 2, 3...)
            val isKickBeat = (currentBeat % 4 == 0 || currentBeat % 4 == 1 || currentBeat % 4 == 2 || currentBeat % 4 == 3)
            if (isKickBeat) {
                val kickT = beatFraction * secondsPerBeat
                if (kickT < 0.28) {
                    val kickPitch = 48.0 + 120.0 * exp(-kickT * 32.0)
                    val kickEnv = exp(-kickT * 11.0)
                    val kickSample = sin(2.0 * Math.PI * kickPitch * kickT) * kickEnv * 0.7
                    left += kickSample
                    right += kickSample
                }
            }

            // 2. Snare / Clap (On beats 1 and 3 of 4-beat bar)
            val isSnareBeat = (currentBeat % 4 == 1 || currentBeat % 4 == 3)
            if (isSnareBeat) {
                val snareT = beatFraction * secondsPerBeat
                if (snareT < 0.22) {
                    val snareNoise = (random.nextFloat() * 2.0 - 1.0) * exp(-snareT * 18.0) * 0.4
                    val snareTone = sin(2.0 * Math.PI * 190.0 * snareT) * exp(-snareT * 14.0) * 0.3
                    val snare = snareNoise + snareTone
                    left += snare * 0.9
                    right += snare * 1.1
                }
            }

            // 3. Offbeat Open Hi-Hat & 16th closed hats
            val subBeat16 = (beatFraction * 4.0).toInt()
            val subFraction = (beatFraction * 4.0) - subBeat16
            val subT = subFraction * (secondsPerBeat / 4.0)
            if (subBeat16 == 2) {
                // Offbeat open hat
                if (subT < 0.18) {
                    val hatNoise = (random.nextFloat() * 2.0 - 1.0) * exp(-subT * 16.0) * 0.25
                    left += hatNoise * 1.1
                    right += hatNoise * 0.9
                }
            } else {
                // Closed hat tick
                if (subT < 0.04) {
                    val hatTick = (random.nextFloat() * 2.0 - 1.0) * exp(-subT * 70.0) * 0.12
                    left += hatTick
                    right += hatTick
                }
            }

            // 4. Bassline (Syncopated 16th groove)
            val bassPattern = intArrayOf(0, 3, 5, 7, 0, 10, 7, 5)
            val bassNoteIdx = (beatPos * 2.0).toInt() % bassPattern.size
            val semitone = bassPattern[bassNoteIdx]
            val bassFreq = baseFreq * 2.0.pow(semitone / 12.0)
            val bassFraction = (beatPos * 2.0) - (beatPos * 2.0).toInt()
            val bassEnv = (1.0 - bassFraction).coerceIn(0.0, 1.0).pow(1.8)
            // Sawtooth bass with lowpass warmth
            val bassPhase = (t * bassFreq) % 1.0
            val bassSaw = (2.0 * bassPhase - 1.0) * 0.35 * bassEnv
            left += bassSaw
            right += bassSaw

            // 5. Melodic Synth Chord / Stab
            val chordBeat = (currentBeat % 8)
            if (chordBeat == 0 || chordBeat == 3 || chordBeat == 6) {
                val chordT = beatFraction * secondsPerBeat
                if (chordT < 0.6) {
                    val chordEnv = exp(-chordT * 4.0)
                    val note1 = baseFreq * 2.0 * 2.0.pow(0.0 / 12.0)
                    val note2 = baseFreq * 2.0 * 2.0.pow(3.0 / 12.0) // minor third
                    val note3 = baseFreq * 2.0 * 2.0.pow(7.0 / 12.0) // fifth
                    val chordW = (sin(2.0 * Math.PI * note1 * t) +
                            sin(2.0 * Math.PI * note2 * t) * 0.9 +
                            sin(2.0 * Math.PI * note3 * t) * 0.8) * 0.18 * chordEnv
                    left += chordW * 1.2
                    right += chordW * 0.8
                }
            }

            // 6. Crash cymbal on bar 0 downbeat
            if (currentBeat == 0 && beatFraction < 0.5) {
                val crashT = t % totalSeconds
                val crashEnv = exp(-crashT * 3.5)
                val crash = (random.nextFloat() * 2.0 - 1.0) * crashEnv * 0.28
                left += crash * 1.15
                right += crash * 0.85
            }

            // Soft master limiting
            val clampedL = (tanh(left * 0.95) * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
            val clampedR = (tanh(right * 0.95) * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

            buffer[sampleIdx++] = clampedL
            buffer[sampleIdx++] = clampedR
        }

        return buffer
    }
}
