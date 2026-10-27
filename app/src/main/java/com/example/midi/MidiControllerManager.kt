package com.example.midi

import android.content.Context
import android.media.midi.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MidiEvent(
    val timestamp: Long,
    val type: String, // "Note On", "Note Off", "Control Change"
    val channel: Int,
    val data1: Int,   // Note # or CC #
    val data2: Int,   // Velocity or Value
    val mappedAction: String? = null
)

enum class MidiPreset(val displayName: String) {
    PIONEER_DDJ_FLX4("Pioneer DDJ-FLX4"),
    PIONEER_DDJ_400("Pioneer DDJ-400"),
    PIONEER_CDJ_3000("Pioneer CDJ-3000"),
    PIONEER_DDJ_1000("Pioneer DDJ-1000"),
    CUSTOM_LEARN("Custom MIDI Learn")
}

class MidiControllerManager(private val context: Context) {

    private val midiManager: MidiManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    } else null

    private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
    val connectedDevices: StateFlow<List<String>> = _connectedDevices.asStateFlow()

    private val _currentPreset = MutableStateFlow(MidiPreset.PIONEER_DDJ_FLX4)
    val currentPreset: StateFlow<MidiPreset> = _currentPreset.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<MidiEvent>>(emptyList())
    val recentEvents: StateFlow<List<MidiEvent>> = _recentEvents.asStateFlow()

    private val _isMidiLearnActive = MutableStateFlow(false)
    val isMidiLearnActive: StateFlow<Boolean> = _isMidiLearnActive.asStateFlow()

    var onMidiAction: ((action: String, value: Float) -> Unit)? = null

    init {
        refreshDevices()
    }

    fun setPreset(preset: MidiPreset) {
        _currentPreset.value = preset
    }

    fun toggleMidiLearn() {
        _isMidiLearnActive.value = !_isMidiLearnActive.value
    }

    fun refreshDevices() {
        val list = mutableListOf<String>()
        if (midiManager != null) {
            val devices = midiManager.devices
            for (info in devices) {
                val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "USB MIDI Controller"
                list.add(name)
            }
        }
        if (list.isEmpty()) {
            list.add("Virtual Pioneer DDJ Controller (Emulated)")
        }
        _connectedDevices.value = list
    }

    fun handleIncomingMidi(status: Int, data1: Int, data2: Int) {
        val channel = status and 0x0F
        val command = status and 0xF0
        val isNoteOn = command == 0x90 && data2 > 0
        val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)
        val isCC = command == 0xB0

        val typeStr = when {
            isNoteOn -> "Note On"
            isNoteOff -> "Note Off"
            isCC -> "CC"
            else -> "SysEx/Other"
        }

        // Map to action
        val mapped = resolveAction(isNoteOn, isCC, data1, data2)

        val event = MidiEvent(
            timestamp = System.currentTimeMillis(),
            type = typeStr,
            channel = channel + 1,
            data1 = data1,
            data2 = data2,
            mappedAction = mapped
        )

        // Keep last 15 events
        val current = _recentEvents.value.toMutableList()
        current.add(0, event)
        if (current.size > 15) current.removeAt(current.size - 1)
        _recentEvents.value = current

        if (mapped != null) {
            val normValue = if (isCC) (data2 / 127.0f) else (if (isNoteOn) 1.0f else 0.0f)
            onMidiAction?.invoke(mapped, normValue)
        }
    }

    private fun resolveAction(isNote: Boolean, isCC: Boolean, d1: Int, d2: Int): String? {
        if (isCC) {
            return when (d1) {
                8 -> "CROSSFADER"
                19 -> "DECK_A_FADER"
                23 -> "DECK_B_FADER"
                16 -> "DECK_A_GAIN"
                20 -> "DECK_B_GAIN"
                17 -> "DECK_A_EQ_HIGH"
                18 -> "DECK_A_EQ_MID"
                19 -> "DECK_A_EQ_LOW"
                21 -> "DECK_B_EQ_HIGH"
                22 -> "DECK_B_EQ_MID"
                23 -> "DECK_B_EQ_LOW"
                26 -> "DECK_A_COLOR_FX"
                30 -> "DECK_B_COLOR_FX"
                33 -> "DECK_A_JOG"
                34 -> "DECK_B_JOG"
                else -> "CC_$d1"
            }
        } else if (isNote) {
            return when (d1) {
                11 -> "DECK_A_PLAY"
                12 -> "DECK_A_CUE"
                13 -> "DECK_A_SYNC"
                14 -> "DECK_B_PLAY"
                15 -> "DECK_B_CUE"
                16 -> "DECK_B_SYNC"
                in 36..43 -> "PAD_${d1 - 35}"
                else -> "NOTE_$d1"
            }
        }
        return null
    }
}
