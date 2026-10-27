package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.*
import com.example.data.Track
import com.example.midi.MidiControllerManager
import com.example.midi.MidiPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PadMode {
    HOT_CUE,
    PAD_FX,
    LOOP_ROLL,
    PERCUSSION
}

enum class ActiveModal {
    NONE,
    CLOUD_LIBRARY,
    RECORDINGS,
    MASTERING_RACK,
    MIDI_CONTROLLER
}

data class DeckUiState(
    val deckId: String,
    val trackTitle: String = "No Track Loaded",
    val trackArtist: String = "Select track from library",
    val genre: String = "Club",
    val bpm: Float = 128.0f,
    val key: String = "8A",
    val isPlaying: Boolean = false,
    val isCueing: Boolean = false,
    val isLoopActive: Boolean = false,
    val loopLengthBeats: Int = 4,
    val isSlipMode: Boolean = false,
    val isScratching: Boolean = false,
    val pitchPercent: Float = 0.0f,
    val jogAngle: Float = 0.0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 60000L,
    val beatPhase: Float = 0.0f,
    val peakMeterL: Float = 0.0f,
    val peakMeterR: Float = 0.0f,
    val volumeFader: Float = 0.85f,
    val gainTrim: Float = 1.0f,
    val eqLow: Float = 0.0f,
    val eqMid: Float = 0.0f,
    val eqHigh: Float = 0.0f,
    val colorFx: Float = 0.0f,
    val padMode: PadMode = PadMode.PERCUSSION,
    val isSyncActive: Boolean = false,
    val isMasterDeck: Boolean = false,
    val currentBeatInBar: Int = 1,
    val isScratchMode: Boolean = true,
    val scratchSpeed: Float = 1.0f,
    val hotCues: List<Long> = emptyList(),
    val waveformPeaks: FloatArray = FloatArray(256) { 0.5f },
    val waveformBass: FloatArray = FloatArray(256) { 0.5f },
    val waveformMids: FloatArray = FloatArray(256) { 0.5f },
    val waveformHighs: FloatArray = FloatArray(256) { 0.5f },
    val liveAudioSignal: FloatArray = FloatArray(64)
)

class VirtualDjViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = VirtualDjAudioEngine(application)
    val midiManager = MidiControllerManager(application)

    private val _deckAState = MutableStateFlow(DeckUiState(deckId = "A"))
    val deckAState: StateFlow<DeckUiState> = _deckAState.asStateFlow()

    private val _deckBState = MutableStateFlow(DeckUiState(deckId = "B"))
    val deckBState: StateFlow<DeckUiState> = _deckBState.asStateFlow()

    private val _crossfader = MutableStateFlow(0.0f)
    val crossfader: StateFlow<Float> = _crossfader.asStateFlow()

    private val _crossfaderCurve = MutableStateFlow(CrossfaderCurve.SMOOTH)
    val crossfaderCurve: StateFlow<CrossfaderCurve> = _crossfaderCurve.asStateFlow()

    private val _masterVolume = MutableStateFlow(0.9f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _activeModal = MutableStateFlow(ActiveModal.NONE)
    val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()

    // 8 Custom electronic percussion pads mapped to high-fidelity sounds
    private val _padSounds = MutableStateFlow(
        listOf(
            DrumSound.CRASH_PIONEER,
            DrumSound.RIDE_PIONEER,
            DrumSound.HIHAT_CLOSED,
            DrumSound.HIHAT_OPEN,
            DrumSound.KICK_808,
            DrumSound.KICK_909,
            DrumSound.SNARE_TRAP,
            DrumSound.SCRATCH_DROP
        )
    )
    val padSounds: StateFlow<List<DrumSound>> = _padSounds.asStateFlow()

    // Status snackbar / toast
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Load default library
        val tracks = ProceduralAudioFactory.createDefaultTracks()
        _allTracks.value = tracks

        // Render audio buffers and pre-load Deck A & Deck B
        viewModelScope.launch(Dispatchers.Default) {
            val bufA = ProceduralAudioFactory.renderPcmBuffer(tracks[0])
            audioEngine.deckA.loadTrack(tracks[0], bufA)

            val bufB = ProceduralAudioFactory.renderPcmBuffer(tracks[1])
            audioEngine.deckB.loadTrack(tracks[1], bufB)

            // Start audio engine
            audioEngine.start()
        }

        // Connect MIDI action listener
        setupMidiListener()

        // 60fps UI State Sync loop
        startUiUpdateLoop()
    }

    private fun setupMidiListener() {
        midiManager.onMidiAction = { action, value ->
            when (action) {
                "DECK_A_PLAY" -> if (value > 0.5f) togglePlay("A")
                "DECK_B_PLAY" -> if (value > 0.5f) togglePlay("B")
                "DECK_A_CUE" -> if (value > 0.5f) cuePressed("A") else cueReleased("A")
                "DECK_B_CUE" -> if (value > 0.5f) cuePressed("B") else cueReleased("B")
                "DECK_A_SYNC" -> if (value > 0.5f) syncDeck("A")
                "DECK_B_SYNC" -> if (value > 0.5f) syncDeck("B")
                "CROSSFADER" -> setCrossfader((value * 2.0f) - 1.0f)
                "DECK_A_FADER" -> setVolumeFader("A", value)
                "DECK_B_FADER" -> setVolumeFader("B", value)
                "DECK_A_GAIN" -> setGainTrim("A", value * 2.0f)
                "DECK_B_GAIN" -> setGainTrim("B", value * 2.0f)
                "DECK_A_COLOR_FX" -> setColorFx("A", (value * 2.0f) - 1.0f)
                "DECK_B_COLOR_FX" -> setColorFx("B", (value * 2.0f) - 1.0f)
                else -> {
                    if (action.startsWith("PAD_")) {
                        val padIndex = (action.removePrefix("PAD_").toIntOrNull() ?: 1) - 1
                        if (padIndex in 0..7 && value > 0.5f) {
                            triggerPercussionPad(padIndex)
                        }
                    }
                }
            }
        }
    }

    private fun startUiUpdateLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val pA = audioEngine.deckA
                val tA = pA.currentTrack
                _deckAState.value = _deckAState.value.copy(
                    trackTitle = tA?.title ?: "Deck A",
                    trackArtist = tA?.artist ?: "Select track",
                    genre = tA?.genre ?: "Club",
                    bpm = pA.effectiveBpm,
                    key = tA?.musicalKey ?: "8A",
                    isPlaying = pA.isPlaying,
                    isCueing = pA.isCueing,
                    isLoopActive = pA.isLoopActive,
                    loopLengthBeats = pA.loopLengthBeats,
                    isSlipMode = pA.isSlipMode,
                    isScratching = pA.isScratching,
                    pitchPercent = pA.pitchPercent,
                    jogAngle = pA.jogAngle,
                    currentPositionMs = pA.currentPositionMs,
                    durationMs = pA.durationMs,
                    beatPhase = pA.beatPhase,
                    peakMeterL = pA.peakMeterL,
                    peakMeterR = pA.peakMeterR,
                    volumeFader = pA.volumeFader,
                    gainTrim = pA.gainTrim,
                    eqLow = pA.effectsChain.eqLowDb,
                    eqMid = pA.effectsChain.eqMidDb,
                    eqHigh = pA.effectsChain.eqHighDb,
                    colorFx = pA.effectsChain.colorFxAmount,
                    isSyncActive = pA.isSyncActive,
                    isMasterDeck = pA.isMasterDeck,
                    currentBeatInBar = pA.currentBeatInBar,
                    isScratchMode = pA.isScratchMode,
                    scratchSpeed = pA.scratchSpeed,
                    waveformPeaks = tA?.waveformPeaks ?: FloatArray(256) { 0.5f },
                    waveformBass = tA?.waveformBass ?: FloatArray(256) { 0.5f },
                    waveformMids = tA?.waveformMids ?: FloatArray(256) { 0.5f },
                    waveformHighs = tA?.waveformHighs ?: FloatArray(256) { 0.5f },
                    liveAudioSignal = pA.getLiveSignalSnapshot()
                )

                val pB = audioEngine.deckB
                val tB = pB.currentTrack
                _deckBState.value = _deckBState.value.copy(
                    trackTitle = tB?.title ?: "Deck B",
                    trackArtist = tB?.artist ?: "Select track",
                    genre = tB?.genre ?: "Club",
                    bpm = pB.effectiveBpm,
                    key = tB?.musicalKey ?: "9A",
                    isPlaying = pB.isPlaying,
                    isCueing = pB.isCueing,
                    isLoopActive = pB.isLoopActive,
                    loopLengthBeats = pB.loopLengthBeats,
                    isSlipMode = pB.isSlipMode,
                    isScratching = pB.isScratching,
                    pitchPercent = pB.pitchPercent,
                    jogAngle = pB.jogAngle,
                    currentPositionMs = pB.currentPositionMs,
                    durationMs = pB.durationMs,
                    beatPhase = pB.beatPhase,
                    peakMeterL = pB.peakMeterL,
                    peakMeterR = pB.peakMeterR,
                    volumeFader = pB.volumeFader,
                    gainTrim = pB.gainTrim,
                    eqLow = pB.effectsChain.eqLowDb,
                    eqMid = pB.effectsChain.eqMidDb,
                    eqHigh = pB.effectsChain.eqHighDb,
                    colorFx = pB.effectsChain.colorFxAmount,
                    isSyncActive = pB.isSyncActive,
                    isMasterDeck = pB.isMasterDeck,
                    currentBeatInBar = pB.currentBeatInBar,
                    isScratchMode = pB.isScratchMode,
                    scratchSpeed = pB.scratchSpeed,
                    waveformPeaks = tB?.waveformPeaks ?: FloatArray(256) { 0.5f },
                    waveformBass = tB?.waveformBass ?: FloatArray(256) { 0.5f },
                    waveformMids = tB?.waveformMids ?: FloatArray(256) { 0.5f },
                    waveformHighs = tB?.waveformHighs ?: FloatArray(256) { 0.5f },
                    liveAudioSignal = pB.getLiveSignalSnapshot()
                )

                delay(20) // ~50fps
            }
        }
    }

    fun openModal(modal: ActiveModal) {
        _activeModal.value = modal
    }

    fun closeModal() {
        _activeModal.value = ActiveModal.NONE
    }

    private val customPcmCache = mutableMapOf<String, ShortArray>()

    fun importLocalAudio(uri: android.net.Uri, targetDeck: String? = null) {
        viewModelScope.launch {
            _statusMessage.value = "Importando y decodificando audio local..."
            try {
                val context = getApplication<Application>()
                val decoded = LocalAudioDecoder.decodeFromUri(context, uri)
                customPcmCache[decoded.track.id] = decoded.pcmBuffer

                // Add to tracks list
                _allTracks.value = listOf(decoded.track) + _allTracks.value.filter { it.id != decoded.track.id }

                if (targetDeck != null) {
                    loadTrackToDeck(targetDeck, decoded.track)
                } else {
                    _statusMessage.value = "Audio listo: ${decoded.track.title} (${String.format("%.1f", decoded.track.bpm)} BPM)"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error al importar audio: ${e.message}"
            }
        }
    }

    fun importLocalMp3(uri: android.net.Uri, targetDeck: String? = null) {
        importLocalAudio(uri, targetDeck)
    }

    fun scanDeviceAudio() {
        viewModelScope.launch {
            _statusMessage.value = "Buscando archivos de audio en el almacenamiento..."
            try {
                val context = getApplication<Application>()
                val localTracks = LocalAudioScanner.scanLocalTracks(context)
                if (localTracks.isEmpty()) {
                    _statusMessage.value = "No se detectaron audios locales en el almacenamiento."
                } else {
                    val existingIds = _allTracks.value.map { it.id }.toSet()
                    val newTracks = localTracks.filter { it.id !in existingIds }
                    _allTracks.value = newTracks + _allTracks.value
                    _statusMessage.value = "Se añadieron ${localTracks.size} audios locales (MP3/WMA/WAV)."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error escaneando audios: ${e.message}"
            }
        }
    }

    fun setStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun loadTrackToDeck(deck: String, track: Track) {
        viewModelScope.launch(Dispatchers.Default) {
            val buf = if (track.isCustomLoaded && customPcmCache.containsKey(track.id)) {
                customPcmCache[track.id]!!
            } else if (track.isCustomLoaded && track.customUri != null) {
                try {
                    val decoded = LocalAudioDecoder.decodeFromUri(getApplication(), android.net.Uri.parse(track.customUri))
                    customPcmCache[track.id] = decoded.pcmBuffer
                    decoded.pcmBuffer
                } catch (e: Exception) {
                    ProceduralAudioFactory.renderPcmBuffer(track)
                }
            } else {
                ProceduralAudioFactory.renderPcmBuffer(track)
            }

            if (deck == "A") {
                audioEngine.deckA.loadTrack(track, buf)
            } else {
                audioEngine.deckB.loadTrack(track, buf)
            }
            _statusMessage.value = "Loaded ${track.title} to Deck $deck"
            closeModal()
        }
    }

    fun togglePlay(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.togglePlay()
    }

    fun cuePressed(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.cuePressed()
    }

    fun cueReleased(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.cueReleased()
    }

    fun seekDeck(deck: String, positionMs: Long) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.seekTo(positionMs)
    }

    fun scrubDeck(deck: String, deltaMs: Long) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.scrubDelta(deltaMs)
    }

    fun syncDeck(deck: String) {
        audioEngine.syncDeck(deck)
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        _statusMessage.value = if (player.isSyncActive) {
            "Deck $deck Beat-Synced to Master Clock (${String.format("%.1f", player.effectiveBpm)} BPM)"
        } else {
            "Deck $deck Sync Disabled"
        }
    }

    fun setMasterDeck(deck: String) {
        audioEngine.setMasterDeck(deck)
        _statusMessage.value = "Deck $deck set as Master Clock"
    }

    fun setPitchPercent(deck: String, value: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        val otherPlayer = if (deck == "A") audioEngine.deckB else audioEngine.deckA
        player.pitchPercent = value.coerceIn(-player.pitchRange, player.pitchRange)

        // Propagate tempo to synced follower deck in real-time
        if (player.isMasterDeck && otherPlayer.isSyncActive) {
            otherPlayer.syncTo(player.effectiveBpm, player.beatPhase)
        }
    }

    fun resetPitch(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.pitchPercent = 0.0f
    }

    fun pitchNudge(deck: String, factor: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.pitchNudge(factor)
    }

    fun onJogTouchDown(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.onJogTouchDown()
    }

    fun onJogTouchMove(deck: String, deltaDegrees: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.onJogTouchMove(deltaDegrees)
    }

    fun onJogTouchUp(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.onJogTouchUp()
    }

    fun toggleLoop(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.toggleLoop()
    }

    fun halveLoop(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.halveLoop()
    }

    fun doubleLoop(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.doubleLoop()
    }

    fun toggleSlipMode(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.isSlipMode = !player.isSlipMode
    }

    fun toggleScratchMode(deck: String) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.isScratchMode = !player.isScratchMode
        val mode = if (player.isScratchMode) "VINYL SCRATCH" else "CDJ PITCH BEND"
        _statusMessage.value = "Deck $deck: $mode Mode Active"
    }

    fun setVolumeFader(deck: String, value: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.volumeFader = value.coerceIn(0f, 1f)
    }

    fun setGainTrim(deck: String, value: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.gainTrim = value.coerceIn(0f, 2f)
    }

    fun setEqLow(deck: String, db: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.effectsChain.eqLowDb = db
    }

    fun setEqMid(deck: String, db: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.effectsChain.eqMidDb = db
    }

    fun setEqHigh(deck: String, db: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.effectsChain.eqHighDb = db
    }

    fun setColorFx(deck: String, amount: Float) {
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB
        player.effectsChain.colorFxAmount = amount
    }

    fun setCrossfader(value: Float) {
        _crossfader.value = value.coerceIn(-1.0f, 1.0f)
        audioEngine.crossfaderPosition = _crossfader.value
    }

    fun setCrossfaderCurve(curve: CrossfaderCurve) {
        _crossfaderCurve.value = curve
        audioEngine.crossfaderCurve = curve
    }

    fun setMasterVolume(value: Float) {
        _masterVolume.value = value.coerceIn(0f, 1f)
        audioEngine.masterVolume = _masterVolume.value
    }

    fun setPadMode(deck: String, mode: PadMode) {
        if (deck == "A") {
            _deckAState.value = _deckAState.value.copy(padMode = mode)
        } else {
            _deckBState.value = _deckBState.value.copy(padMode = mode)
        }
    }

    fun triggerPadAction(deck: String, padIndex: Int) {
        val state = if (deck == "A") _deckAState.value else _deckBState.value
        val player = if (deck == "A") audioEngine.deckA else audioEngine.deckB

        when (state.padMode) {
            PadMode.PERCUSSION -> {
                triggerPercussionPad(padIndex)
            }
            PadMode.HOT_CUE -> {
                player.jumpToHotCue(padIndex)
            }
            PadMode.PAD_FX -> {
                togglePadFx(player.effectsChain, padIndex)
            }
            PadMode.LOOP_ROLL -> {
                // Instant roll: 1/16, 1/8, 1/4, 1/2, 1, 2, 4, 8 beats
                val beats = when (padIndex) {
                    0 -> 1 // 1 beat
                    1 -> 2
                    2 -> 4
                    3 -> 8
                    4 -> 1
                    5 -> 2
                    6 -> 4
                    else -> 8
                }
                player.setAutoLoop(beats)
            }
        }
    }

    private fun togglePadFx(chain: EffectsChain, index: Int) {
        when (index) {
            0 -> chain.echoEnabled = !chain.echoEnabled
            1 -> chain.reverbEnabled = !chain.reverbEnabled
            2 -> chain.flangerEnabled = !chain.flangerEnabled
            3 -> chain.bitcrushEnabled = !chain.bitcrushEnabled
            4 -> {
                chain.echoEnabled = true
                chain.echoFeedback = 0.85f
            }
            5 -> {
                chain.reverbEnabled = true
                chain.reverbMix = 0.7f
            }
            else -> {
                chain.echoEnabled = false
                chain.reverbEnabled = false
                chain.flangerEnabled = false
                chain.bitcrushEnabled = false
            }
        }
    }

    fun triggerPercussionPad(index: Int) {
        val sounds = _padSounds.value
        if (index in sounds.indices) {
            val sound = sounds[index]
            audioEngine.triggerDrumPad(sound, 1.0f)
        }
    }

    fun customizePadSound(padIndex: Int, newSound: DrumSound) {
        val list = _padSounds.value.toMutableList()
        if (padIndex in list.indices) {
            list[padIndex] = newSound
            _padSounds.value = list
            _statusMessage.value = "Pad ${padIndex + 1} set to ${newSound.displayName}"
        }
    }

    // Live Recording
    fun toggleRecording() {
        if (audioEngine.sessionRecorder.isRecording.value) {
            val file = audioEngine.sessionRecorder.stopRecording()
            _statusMessage.value = "Recording saved: ${file?.name ?: "Mix.wav"}"
        } else {
            val started = audioEngine.sessionRecorder.startRecording()
            if (started) {
                _statusMessage.value = "Live session recording started (WAV 44.1kHz)"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
