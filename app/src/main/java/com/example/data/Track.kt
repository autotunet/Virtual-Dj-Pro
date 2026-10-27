package com.example.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Float,
    val musicalKey: String,
    val durationMs: Long,
    val waveformPeaks: FloatArray = FloatArray(256) { 0.5f },
    val waveformBass: FloatArray = FloatArray(256) { 0.5f },
    val waveformMids: FloatArray = FloatArray(256) { 0.5f },
    val waveformHighs: FloatArray = FloatArray(256) { 0.5f },
    val hotCues: MutableList<Long> = mutableListOf(),
    val isCustomLoaded: Boolean = false,
    val customUri: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Track
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
