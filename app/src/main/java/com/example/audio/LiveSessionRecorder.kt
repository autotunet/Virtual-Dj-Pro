package com.example.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedSession(
    val file: File,
    val name: String,
    val dateString: String,
    val durationSeconds: Long,
    val sizeBytes: Long
)

class LiveSessionRecorder(private val context: Context, private val sampleRate: Int = 44100) {

    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private var totalBytesWritten = 0L
    private var recordingStartTime = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _recordedSessions = MutableStateFlow<List<RecordedSession>>(emptyList())
    val recordedSessions: StateFlow<List<RecordedSession>> = _recordedSessions.asStateFlow()

    init {
        loadExistingSessions()
    }

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        try {
            val dir = File(context.filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "DJ_Mix_$timestamp.wav")
            val fos = FileOutputStream(file)

            // Write placeholder 44-byte WAV header
            val header = ByteArray(44)
            fos.write(header)

            currentFile = file
            fileOutputStream = fos
            totalBytesWritten = 0L
            recordingStartTime = System.currentTimeMillis()
            _isRecording.value = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun writePcmData(pcmShorts: ShortArray, length: Int) {
        if (!_isRecording.value || fileOutputStream == null) return
        val byteBuf = ByteArray(length * 2)
        var bIdx = 0
        for (i in 0 until length) {
            val s = pcmShorts[i].toInt()
            byteBuf[bIdx++] = (s and 0xFF).toByte()
            byteBuf[bIdx++] = ((s shr 8) and 0xFF).toByte()
        }
        try {
            fileOutputStream?.write(byteBuf)
            totalBytesWritten += byteBuf.size
            val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
            _recordingDurationSeconds.value = elapsed
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        val file = currentFile ?: return null

        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
            fileOutputStream = null

            // Patch RIFF WAV header with exact byte sizes
            writeWavHeader(file, totalBytesWritten, sampleRate, 2, 16)
            loadExistingSessions()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun loadExistingSessions() {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) } ?: return
        val sessions = files.sortedByDescending { it.lastModified() }.map { f ->
            val dataSize = (f.length() - 44).coerceAtLeast(0)
            val bytesPerSecond = sampleRate * 2 * 2 // 2 channels * 16 bits (2 bytes)
            val durationSec = if (bytesPerSecond > 0) dataSize / bytesPerSecond else 0
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
            RecordedSession(
                file = f,
                name = f.nameWithoutExtension,
                dateString = dateStr,
                durationSeconds = durationSec,
                sizeBytes = f.length()
            )
        }
        _recordedSessions.value = sessions
    }

    fun shareSession(session: RecordedSession) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                session.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export DJ Mix (WAV)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSession(session: RecordedSession) {
        if (session.file.exists()) {
            session.file.delete()
        }
        loadExistingSessions()
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val blockAlign = (channels * bitsPerSample / 8)

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = ((byteRate shr 8) and 0xffL).toByte()
        header[30] = ((byteRate shr 16) and 0xffL).toByte()
        header[31] = ((byteRate shr 24) and 0xffL).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }
}
