package com.example.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Track
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLibrarySheet(
    tracks: List<Track>,
    onLoadToDeck: (deck: String, track: Track) -> Unit,
    onImportLocalMp3: (Uri) -> Unit = {},
    onScanDeviceAudio: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }

    val genres = listOf("All", "Tech House", "Melodic Techno", "Trap / Bass", "Drum & Bass", "Afro House", "Imported MP3")

    val filteredTracks = remember(tracks, searchQuery, selectedGenre) {
        tracks.filter {
            val matchesGenre = selectedGenre == "All" || it.genre.contains(selectedGenre, ignoreCase = true)
            val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.genre.contains(searchQuery, ignoreCase = true)
            matchesGenre && matchesSearch
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAudioPerm by remember {
        mutableStateOf(com.example.audio.LocalAudioScanner.hasAudioPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPerm = isGranted
        if (isGranted) {
            onScanDeviceAudio()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onImportLocalMp3(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DjBackground)
            .padding(16.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloud Library",
                    tint = DeckACyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BIBLIOTECA & AUDIOS LOCALES",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Device Audio Permission & Scan Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasAudioPerm) Color(0xFF14241B) else Color(0xFF261D13)
            ),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasAudioPerm) LedGreen.copy(alpha = 0.5f) else LedAmber.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasAudioPerm) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                            contentDescription = "Status",
                            tint = if (hasAudioPerm) LedGreen else LedAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (hasAudioPerm) "Permiso Concedido: Audios Locales" else "Permiso para leer MP3, WMA y Audios",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (hasAudioPerm) "Puedes escanear o seleccionar archivos de audio" else "Permite leer archivos .mp3, .wma, .wav, .flac de tu dispositivo",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (!hasAudioPerm) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    com.example.audio.LocalAudioScanner.getRequiredAudioPermission()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LedAmber),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_request_audio_permission")
                        ) {
                            Text("PERMITIR", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onScanDeviceAudio,
                            colors = ButtonDefaults.buttonColors(containerColor = LedGreen),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_scan_device_audio")
                        ) {
                            Text("ESCANEAR", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por título, artista, MP3, WMA o BPM...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeckACyan,
                unfocusedBorderColor = DjSurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Genre Filter Chips
        val genres = listOf("All", "Tech House", "Melodic Techno", "Trap / Bass", "Drum & Bass", "Local", "MP3", "WMA")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) DeckACyan else DjSurfaceElevated)
                        .clickable { selectedGenre = genre }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = genre,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Import Local Audio File Button (supports MP3, WMA, WAV, FLAC, OGG)
        Button(
            onClick = {
                filePickerLauncher.launch(
                    arrayOf(
                        "audio/*",
                        "audio/mpeg",
                        "audio/x-ms-wma",
                        "audio/x-wav",
                        "audio/ogg",
                        "audio/aac",
                        "audio/flac",
                        "*/*"
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_import_local_audio"),
            colors = ButtonDefaults.buttonColors(containerColor = DjSurfaceElevated),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = "Import", tint = WaveformMid)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Seleccionar Archivo (MP3, WMA, WAV, FLAC, OGG)", color = TextPrimary, fontSize = 11.5.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredTracks) { track ->
                TrackCard(
                    track = track,
                    onLoadDeckA = { onLoadToDeck("A", track) },
                    onLoadDeckB = { onLoadToDeck("B", track) }
                )
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DjSurface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DjSurfaceBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${track.artist} • ${track.genre}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${track.bpm} BPM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckACyan
                    )
                    Text(
                        text = "Key: ${track.musicalKey}",
                        fontSize = 10.sp,
                        color = LedGreen
                    )
                    Text(
                        text = "Duration: ${track.durationMs / 1000}s",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Deck Load Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onLoadDeckA,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckACyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("DECK A", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onLoadDeckB,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckBAmber),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("DECK B", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
