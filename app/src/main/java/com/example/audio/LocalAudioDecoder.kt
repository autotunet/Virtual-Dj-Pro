package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object LocalAudioDecoder {

    private const val TAG = "LocalAudioDecoder"
    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 2
    // Limit to max 3 minutes of decoded PCM in memory to preserve performance (3 min stereo 44.1k = ~31MB)
    private const val MAX_DECODE_DURATION_MS = 180_000L

    data class DecodedAudioResult(
        val track: Track,
        val pcmBuffer: ShortArray
    )

    suspend fun decodeFromUri(context: Context, uri: Uri): DecodedAudioResult = withContext(Dispatchers.IO) {
        var title = "Audio Local"
        var artist = "Dispositivo"
        var durationMs = 60000L
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                if (it.isNotBlank()) title = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                if (it.isNotBlank()) artist = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                if (it > 0) durationMs = it
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading metadata with retriever: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (title == "Audio Local") {
            val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
            if (!rawName.isNullOrBlank()) {
                title = rawName
            }
        }

        // Decode PCM with MediaExtractor & MediaCodec
        val extractor = MediaExtractor()
        var pcmSamples: ShortArray? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex >= 0 && audioFormat != null) {
                extractor.selectTrack(audioTrackIndex)
                pcmSamples = decodeTrackToPcm(extractor, audioFormat)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decoding failed: ${e.message}", e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        // If decoding produced valid PCM, compute real waveform bands from actual audio!
        val finalPcm = if (pcmSamples != null && pcmSamples.size >= 44100) {
            pcmSamples
        } else {
            // Fallback: render procedural club beat so track is always playable
            val fallbackTrack = Track(
                id = "temp",
                title = title,
                artist = artist,
                genre = "Local Audio",
                bpm = 128.0f,
                musicalKey = "8A",
                durationMs = min(durationMs, 60000L)
            )
            ProceduralAudioFactory.renderPcmBuffer(fallbackTrack)
        }

        val actualDurationMs = min(durationMs, (finalPcm.size.toLong() * 1000L) / (TARGET_SAMPLE_RATE * TARGET_CHANNELS))
        val (peaks, bass, mids, highs) = analyzeWaveformFromPcm(finalPcm)
        val estimatedBpm = detectBpm(finalPcm, TARGET_SAMPLE_RATE)

        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.uppercase() ?: ""
        val detectedGenre = when {
            ext == "WMA" -> "Local WMA"
            ext == "MP3" -> "Local MP3"
            ext.isNotBlank() && ext.length in 2..4 -> "Local $ext"
            else -> "Local Audio"
        }

        val track = Track(
            id = "local_${System.currentTimeMillis()}",
            title = title,
            artist = artist,
            genre = detectedGenre,
            bpm = estimatedBpm,
            musicalKey = "8A",
            durationMs = actualDurationMs,
            waveformPeaks = peaks,
            waveformBass = bass,
            waveformMids = mids,
            waveformHighs = highs,
            hotCues = mutableListOf(0L, actualDurationMs / 4, actualDurationMs / 2, (actualDurationMs * 3) / 4),
            isCustomLoaded = true,
            customUri = uri.toString()
        )

        DecodedAudioResult(track, finalPcm)
    }

    private fun decodeTrackToPcm(extractor: MediaExtractor, format: MediaFormat): ShortArray {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return ShortArray(0)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_SAMPLE_RATE
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val rawShorts = ArrayList<ShortArray>()
        var totalDecodedShorts = 0
        val maxShorts = ((MAX_DECODE_DURATION_MS * TARGET_SAMPLE_RATE * TARGET_CHANNELS) / 1000L).toInt()

        val bufferInfo = MediaCodec.BufferInfo()
        var isExtractorEOS = false
        var isCodecEOS = false

        try {
            while (!isCodecEOS && totalDecodedShorts < maxShorts) {
                if (!isExtractorEOS) {
                    val inIdx = codec.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIdx)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIdx >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isCodecEOS = true
                    }
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val chunkShorts = ShortArray(shortBuf.remaining())
                        shortBuf.get(chunkShorts)

                        rawShorts.add(chunkShorts)
                        totalDecodedShorts += chunkShorts.size
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (_: Exception) {}
        }

        // Combine chunks
        val combined = ShortArray(totalDecodedShorts)
        var offset = 0
        for (chunk in rawShorts) {
            System.arraycopy(chunk, 0, combined, offset, chunk.size)
            offset += chunk.size
        }

        // Convert to stereo if mono, or downmix if multi-channel
        return when (channelCount) {
            1 -> {
                // Mono to stereo duplicate
                val stereo = ShortArray(combined.size * 2)
                for (i in combined.indices) {
                    stereo[i * 2] = combined[i]
                    stereo[i * 2 + 1] = combined[i]
                }
                stereo
            }
            2 -> combined
            else -> {
                // Multi-channel downmix to stereo (taking first 2 channels)
                val stereoFrames = combined.size / channelCount
                val stereo = ShortArray(stereoFrames * 2)
                for (f in 0 until stereoFrames) {
                    stereo[f * 2] = combined[f * channelCount]
                    stereo[f * 2 + 1] = combined[f * channelCount + 1]
                }
                stereo
            }
        }
    }

    /**
     * Extracts 256 waveform bins (total peaks, bass, mids, highs) from actual PCM audio.
     */
    private fun analyzeWaveformFromPcm(pcm: ShortArray): WaveformData {
        val bins = 256
        val peaks = FloatArray(bins)
        val bass = FloatArray(bins)
        val mids = FloatArray(bins)
        val highs = FloatArray(bins)

        val totalFrames = pcm.size / 2
        if (totalFrames <= 0) return WaveformData(peaks, bass, mids, highs)

        val framesPerBin = max(1, totalFrames / bins)

        for (b in 0 until bins) {
            val startFrame = b * framesPerBin
            val endFrame = min(totalFrames, startFrame + framesPerBin)

            var maxAmp = 0f
            var bassEnergy = 0f
            var midEnergy = 0f
            var highEnergy = 0f
            var count = 0

            var prevSample = 0f
            for (f in startFrame until endFrame) {
                val idx = f * 2
                val sL = abs(pcm[idx] / 32768f)
                val sR = if (idx + 1 < pcm.size) abs(pcm[idx + 1] / 32768f) else sL
                val amp = (sL + sR) * 0.5f

                if (amp > maxAmp) maxAmp = amp

                // Simple differentiator for high frequencies, smoother for bass
                val diff = abs(amp - prevSample)
                prevSample = amp

                bassEnergy += amp
                highEnergy += diff * 1.5f
                midEnergy += (amp + diff) * 0.5f
                count++
            }

            val avgCount = if (count > 0) count.toFloat() else 1f
            peaks[b] = maxAmp.coerceIn(0.08f, 1.0f)
            bass[b] = ((bassEnergy / avgCount) * 1.6f).coerceIn(0.08f, 1.0f)
            mids[b] = ((midEnergy / avgCount) * 1.5f).coerceIn(0.08f, 1.0f)
            highs[b] = ((highEnergy / avgCount) * 2.2f).coerceIn(0.08f, 1.0f)
        }

        return WaveformData(peaks, bass, mids, highs)
    }

    private fun detectBpm(pcm: ShortArray, sampleRate: Int): Float {
        // Approximate BPM by detecting strong energy onset peaks
        val totalFrames = pcm.size / 2
        if (totalFrames < sampleRate * 4) return 128.0f // default

        val windowSize = sampleRate / 100 // 10ms windows
        val numWindows = totalFrames / windowSize
        val energies = FloatArray(numWindows)

        for (w in 0 until numWindows) {
            var sum = 0f
            for (i in 0 until windowSize) {
                val idx = (w * windowSize + i) * 2
                if (idx < pcm.size) {
                    val s = pcm[idx] / 32768f
                    sum += s * s
                }
            }
            energies[w] = sqrt(sum / windowSize)
        }

        // Count peaks above threshold
        var peakCount = 0
        val threshold = 0.35f
        for (w in 1 until numWindows - 1) {
            if (energies[w] > threshold && energies[w] > energies[w - 1] && energies[w] > energies[w + 1]) {
                peakCount++
            }
        }

        val totalSeconds = totalFrames.toFloat() / sampleRate
        val rawBpm = if (totalSeconds > 0) (peakCount.toFloat() / totalSeconds) * 60f else 128f

        // Round to reasonable DJ dance music tempos (120 - 140 range)
        return when {
            rawBpm in 115f..145f -> (round(rawBpm * 10f) / 10f)
            rawBpm in 60f..90f -> (round(rawBpm * 2f * 10f) / 10f)
            rawBpm in 160f..180f -> (round(rawBpm * 10f) / 10f) // Drum and bass
            else -> 128.0f
        }
    }

    data class WaveformData(
        val peaks: FloatArray,
        val bass: FloatArray,
        val mids: FloatArray,
        val highs: FloatArray
    )
}
