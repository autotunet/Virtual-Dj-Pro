package com.example.audio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalAudioScanner {

    private const val TAG = "LocalAudioScanner"

    fun getRequiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        val perm = getRequiredAudioPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanLocalTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE
            )

            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val duration = cursor.getLong(durCol)
                    val displayName = cursor.getString(displayCol) ?: "audio_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                    // Format title and artist
                    val title = when {
                        !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle
                        displayName.contains('.') -> displayName.substringBeforeLast('.')
                        else -> displayName
                    }

                    val artist = when {
                        !rawArtist.isNullOrBlank() && rawArtist != "<unknown>" -> rawArtist
                        else -> "Dispositivo Local"
                    }

                    // Only include tracks longer than 3 seconds (skip UI clicks/alerts)
                    if (duration > 3000L || duration == 0L) {
                        val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        val extension = displayName.substringAfterLast('.', "").uppercase()
                        val formatTag = when {
                            extension.isNotBlank() -> extension
                            mime != null && mime.contains("wma") -> "WMA"
                            mime != null && mime.contains("mpeg") -> "MP3"
                            else -> "AUDIO"
                        }

                        tracks.add(
                            Track(
                                id = "media_$id",
                                title = title,
                                artist = artist,
                                genre = "Local $formatTag",
                                bpm = 126.0f,
                                musicalKey = "8A",
                                durationMs = if (duration > 0) duration else 60_000L,
                                isCustomLoaded = true,
                                customUri = contentUri.toString()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local audio media: ${e.message}", e)
        }

        tracks
    }
}
