package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.example.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

enum class CrossfaderCurve {
    SMOOTH,
    LINEAR,
    SHARP_SCRATCH
}

class ActiveVoice(
    val samples: ShortArray,
    var position: Int = 0,
    val velocity: Float = 1.0f
)

class VirtualDjAudioEngine(val context: Context, val sampleRate: Int = 44100) {

    val deckA = DeckAudioPlayer("A", sampleRate)
    val deckB = DeckAudioPlayer("B", sampleRate)
    val drumSynth = DrumSynthEngine(sampleRate)
    val sessionRecorder = LiveSessionRecorder(context, sampleRate)

    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Crossfader: -1.0 (Full A) ... 0.0 (Center) ... +1.0 (Full B)
    var crossfaderPosition: Float = 0.0f
    var crossfaderCurve: CrossfaderCurve = CrossfaderCurve.SMOOTH

    var masterVolume: Float = 0.9f
    var stereoWidenerAmount: Float = 0.2f // 0.0 to 1.0

    // Master 5-band EQ gains (in dB)
    val masterEqGains = floatArrayOf(0f, 0f, 0f, 0f, 0f) // 60Hz, 250Hz, 1kHz, 4kHz, 12kHz

    // Active drum percussion voices (trigger Pioneer cymbals & electronic drums)
    private val activeVoices = java.util.concurrent.CopyOnWriteArrayList<ActiveVoice>()

    // Master VU meters
    var masterMeterL: Float = 0f
    var masterMeterR: Float = 0f

    // Real-time FFT spectrum analyzer
    val spectrumAnalyzer = SpectrumAnalyzerFft(fftSize = 256, sampleRate = sampleRate)

    // Spectrum Analyzer bands (32 frequency bins)
    private val _spectrumBands = MutableStateFlow(FloatArray(32) { 0.05f })
    val spectrumBands: StateFlow<FloatArray> = _spectrumBands.asStateFlow()

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, 2048)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
            }
            .build()
    }

    fun start() {
        if (audioJob != null) return

        audioTrack?.play()

        audioJob = engineScope.launch {
            // Set thread priority to audio thread
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Exception) {
                // Ignore if not permitted
            }

            val chunkSize = 512 // 512 stereo frames = 1024 shorts (~11ms latency)
            val pcmBuffer = ShortArray(chunkSize * 2)

            val tempFrameA = FloatArray(2)
            val outFrameA = FloatArray(2)

            val tempFrameB = FloatArray(2)
            val outFrameB = FloatArray(2)

            val fftMagnitudes = FloatArray(32)
            var fftCounter = 0

            while (isActive) {
                var pcmIdx = 0

                // Crossfader volume gains
                val (gainA, gainB) = calculateCrossfaderGains(crossfaderPosition, crossfaderCurve)

                for (f in 0 until chunkSize) {
                    // Read Deck A
                    deckA.nextFrame(tempFrameA, outFrameA)
                    var aL = outFrameA[0] * gainA
                    var aR = outFrameA[1] * gainA

                    // Read Deck B
                    deckB.nextFrame(tempFrameB, outFrameB)
                    var bL = outFrameB[0] * gainB
                    var bR = outFrameB[1] * gainB

                    // Mix Decks
                    var mixL = aL + bL
                    var mixR = aR + bR

                    // Mix Electronic Percussion & Cymbals voices
                    if (!activeVoices.isEmpty()) {
                        val iterator = activeVoices.iterator()
                        while (iterator.hasNext()) {
                            val voice = iterator.next()
                            if (voice.position < voice.samples.size) {
                                val drumSample = (voice.samples[voice.position] / 32768f) * voice.velocity
                                mixL += drumSample
                                mixR += drumSample
                                voice.position++
                            } else {
                                activeVoices.remove(voice)
                            }
                        }
                    }

                    // Stereo Widening (Mid/Side processing)
                    if (stereoWidenerAmount > 0.01f) {
                        val mid = (mixL + mixR) * 0.5f
                        val side = (mixL - mixR) * 0.5f * (1.0f + stereoWidenerAmount * 1.5f)
                        mixL = mid + side
                        mixR = mid - side
                    }

                    // Master Volume
                    mixL *= masterVolume
                    mixR *= masterVolume

                    // Master Limiter (Soft saturation tanh to prevent any digital clipping)
                    mixL = tanh(mixL)
                    mixR = tanh(mixR)

                    val shortL = (mixL * 32700f).toInt().coerceIn(-32767, 32767).toShort()
                    val shortR = (mixR * 32700f).toInt().coerceIn(-32767, 32767).toShort()

                    pcmBuffer[pcmIdx++] = shortL
                    pcmBuffer[pcmIdx++] = shortR

                    // Meter updates
                    masterMeterL = max(abs(mixL), masterMeterL * 0.999f)
                    masterMeterR = max(abs(mixR), masterMeterR * 0.999f)
                }

                // Real-time 32-band FFT frequency analysis
                spectrumAnalyzer.process(pcmBuffer, chunkSize, fftMagnitudes)

                // Write to AudioTrack
                audioTrack?.write(pcmBuffer, 0, pcmBuffer.size)

                // Write to Live Session Recorder if active
                sessionRecorder.writePcmData(pcmBuffer, pcmBuffer.size)

                // Update UI spectrum flow (~50fps)
                fftCounter++
                if (fftCounter >= 2) {
                    fftCounter = 0
                    _spectrumBands.value = fftMagnitudes.clone()
                }
            }
        }
    }

    fun stop() {
        audioJob?.cancel()
        audioJob = null
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun triggerDrumPad(sound: DrumSound, velocity: Float = 1.0f) {
        val sample = drumSynth.getSample(sound)
        if (sample.isNotEmpty()) {
            activeVoices.add(ActiveVoice(sample, 0, velocity.coerceIn(0.2f, 1.0f)))
        }
    }

    var masterDeckId: String = "A"

    fun setMasterDeck(deck: String) {
        masterDeckId = deck
        deckA.isMasterDeck = (deck == "A")
        deckB.isMasterDeck = (deck == "B")
        if (deck == "A" && deckB.isSyncActive) {
            deckB.syncTo(deckA.effectiveBpm, deckA.beatPhase)
        } else if (deck == "B" && deckA.isSyncActive) {
            deckA.syncTo(deckB.effectiveBpm, deckB.beatPhase)
        }
    }

    fun toggleSync(deck: String) {
        val player = if (deck == "A") deckA else deckB
        val otherPlayer = if (deck == "A") deckB else deckA

        player.isSyncActive = !player.isSyncActive
        if (player.isSyncActive) {
            if (!deckA.isMasterDeck && !deckB.isMasterDeck) {
                otherPlayer.isMasterDeck = true
                masterDeckId = otherPlayer.deckId
            }
            val targetBpm = if (player.isMasterDeck) otherPlayer.effectiveBpm else {
                if (masterDeckId == "A") deckA.effectiveBpm else deckB.effectiveBpm
            }
            val targetPhase = if (player.isMasterDeck) otherPlayer.beatPhase else {
                if (masterDeckId == "A") deckA.beatPhase else deckB.beatPhase
            }
            player.syncTo(targetBpm, targetPhase)
        }
    }

    fun syncDeck(source: String) {
        toggleSync(source)
    }

    private fun calculateCrossfaderGains(pos: Float, curve: CrossfaderCurve): Pair<Float, Float> {
        val p = pos.coerceIn(-1.0f, 1.0f)
        return when (curve) {
            CrossfaderCurve.SMOOTH -> {
                // Equal-power crossfade (cos/sin curve)
                val angle = (p + 1.0f) * 0.25f * Math.PI.toFloat()
                Pair(cos(angle), sin(angle))
            }
            CrossfaderCurve.LINEAR -> {
                // Constant sum linear
                val gainA = ((1.0f - p) * 0.5f).coerceIn(0f, 1f)
                val gainB = ((1.0f + p) * 0.5f).coerceIn(0f, 1f)
                Pair(gainA, gainB)
            }
            CrossfaderCurve.SHARP_SCRATCH -> {
                // Scratch DJ curve: sharp cut at edges, instant full volume
                val gainA = if (p > 0.85f) ((1f - p) / 0.15f) else 1.0f
                val gainB = if (p < -0.85f) ((p + 1f) / 0.15f) else 1.0f
                Pair(gainA.coerceIn(0f, 1f), gainB.coerceIn(0f, 1f))
            }
        }
    }
}
