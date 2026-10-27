package com.example.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.DrumSound
import com.example.ui.components.*
import com.example.ui.dialogs.CustomizePadDialog
import com.example.ui.sheets.*
import com.example.ui.theme.*

enum class ActiveViewTab {
    DUAL,
    DECK_A,
    MIXER,
    DECK_B
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualDjScreen(
    viewModel: VirtualDjViewModel,
    onShowInterstitial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val deckAState by viewModel.deckAState.collectAsStateWithLifecycle()
    val deckBState by viewModel.deckBState.collectAsStateWithLifecycle()
    val crossfader by viewModel.crossfader.collectAsStateWithLifecycle()
    val crossfaderCurve by viewModel.crossfaderCurve.collectAsStateWithLifecycle()
    val masterVolume by viewModel.masterVolume.collectAsStateWithLifecycle()
    val activeModal by viewModel.activeModal.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val padSounds by viewModel.padSounds.collectAsStateWithLifecycle()
    val isRecording by viewModel.audioEngine.sessionRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.audioEngine.sessionRecorder.recordingDurationSeconds.collectAsStateWithLifecycle()
    val recordedSessions by viewModel.audioEngine.sessionRecorder.recordedSessions.collectAsStateWithLifecycle()
    val spectrumBands by viewModel.audioEngine.spectrumBands.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.midiManager.connectedDevices.collectAsStateWithLifecycle()
    val currentPreset by viewModel.midiManager.currentPreset.collectAsStateWithLifecycle()
    val recentEvents by viewModel.midiManager.recentEvents.collectAsStateWithLifecycle()
    val isMidiLearnActive by viewModel.midiManager.isMidiLearnActive.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var activeViewTab by remember { mutableStateOf(ActiveViewTab.DUAL) }
    var padToCustomize by remember { mutableStateOf<Int?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    val adStatus by com.example.ads.AdMobManager.adStatus.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val mp3PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importLocalAudio(uri)
        }
    }

    // Automatic periodic interstitial ad timer (runs every 90 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(90_000L)
            onShowInterstitial()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DjBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Top Navigation & Quick Access Bar
            Surface(
                color = DjSurface,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, DjSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = if (isLandscape) 4.dp else 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Branding
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 22.dp else 26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeckACyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("DJ", fontSize = if (isLandscape) 10.sp else 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "VIRTUAL DJ PRO",
                                fontSize = if (isLandscape) 11.sp else 13.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            if (!isLandscape) {
                                Text(
                                    text = "PIONEER CDJ / DJM EDITION",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Quick Action Icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Import Local Audio button (MP3, WMA, WAV, etc.)
                        IconButton(
                            onClick = { mp3PickerLauncher.launch(arrayOf("audio/*", "audio/x-ms-wma", "audio/mpeg", "*/*")) },
                            modifier = Modifier.testTag("nav_import_mp3")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = "Import Audio", tint = WaveformMid)
                        }

                        // Cloud Library button
                        IconButton(
                            onClick = { viewModel.openModal(ActiveModal.CLOUD_LIBRARY) },
                            modifier = Modifier.testTag("nav_library")
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = "Tracks", tint = DeckACyan)
                        }

                        // Mastering & VST FX button
                        IconButton(
                            onClick = { viewModel.openModal(ActiveModal.MASTERING_RACK) },
                            modifier = Modifier.testTag("nav_mastering")
                        ) {
                            Icon(Icons.Default.Equalizer, contentDescription = "Mastering", tint = LedGreen)
                        }

                        // Recordings button
                        IconButton(
                            onClick = { viewModel.openModal(ActiveModal.RECORDINGS) },
                            modifier = Modifier.testTag("nav_recordings")
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Recordings",
                                tint = if (isRecording) LedRed else TextSecondary
                            )
                        }

                        // MIDI Controller button
                        IconButton(
                            onClick = { viewModel.openModal(ActiveModal.MIDI_CONTROLLER) },
                            modifier = Modifier.testTag("nav_midi")
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = "MIDI", tint = LedAmber)
                        }

                        // AdMob Interstitial Trigger Button
                        Button(
                            onClick = onShowInterstitial,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232B38)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_trigger_interstitial")
                        ) {
                            Text(
                                text = "POPUP AD",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = LedAmber
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Google AdMob Real Bottom Banner (Bottom crossfader removed; master crossfader is in central Pioneer DJM mixer unit)
            Surface(
                color = DjSurfaceElevated,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, DjSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    AdMobBannerView(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // View Mode Selector (Dual, Deck A, Mixer, Deck B) on portrait
            if (!isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DjSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        ActiveViewTab.DUAL to "DUAL VIEW",
                        ActiveViewTab.DECK_A to "DECK A",
                        ActiveViewTab.MIXER to "DJM MIXER",
                        ActiveViewTab.DECK_B to "DECK B"
                    )
                    tabs.forEach { (tab, label) ->
                        val isSelected = activeViewTab == tab
                        val tabColor = when (tab) {
                            ActiveViewTab.DECK_A -> DeckACyan
                            ActiveViewTab.DECK_B -> DeckBAmber
                            ActiveViewTab.MIXER -> LedGreen
                            ActiveViewTab.DUAL -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) tabColor else DjSurfaceElevated)
                                .clickable { activeViewTab = tab }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            // Main Console Workspace
            if (isLandscape) {
                // Pro Club Horizontal Layout (Deck A | Pioneer DJM Mixer with Spectrum & Crossfader | Deck B)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DeckAContent(viewModel, deckAState, padSounds, isLandscape = true) { padToCustomize = it }
                    }
                    Box(modifier = Modifier.width(235.dp).fillMaxHeight()) {
                        MixerContent(
                            viewModel = viewModel,
                            deckAState = deckAState,
                            deckBState = deckBState,
                            crossfader = crossfader,
                            crossfaderCurve = crossfaderCurve,
                            masterVolume = masterVolume,
                            isRecording = isRecording,
                            recordingDurationSeconds = recordingDuration,
                            spectrumBands = spectrumBands,
                            isLandscape = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DeckBContent(viewModel, deckBState, padSounds, isLandscape = true) { padToCustomize = it }
                    }
                }
            } else {
                // Portrait Layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    when (activeViewTab) {
                        ActiveViewTab.DUAL -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DeckAContent(viewModel, deckAState, padSounds, isLandscape = false) { padToCustomize = it }
                                MixerContent(
                                    viewModel = viewModel,
                                    deckAState = deckAState,
                                    deckBState = deckBState,
                                    crossfader = crossfader,
                                    crossfaderCurve = crossfaderCurve,
                                    masterVolume = masterVolume,
                                    isRecording = isRecording,
                                    recordingDurationSeconds = recordingDuration,
                                    spectrumBands = spectrumBands,
                                    isLandscape = false
                                )
                                DeckBContent(viewModel, deckBState, padSounds, isLandscape = false) { padToCustomize = it }
                            }
                        }
                        ActiveViewTab.DECK_A -> {
                            DeckAContent(viewModel, deckAState, padSounds, isLandscape = false) { padToCustomize = it }
                        }
                        ActiveViewTab.MIXER -> {
                            MixerContent(
                                viewModel = viewModel,
                                deckAState = deckAState,
                                deckBState = deckBState,
                                crossfader = crossfader,
                                crossfaderCurve = crossfaderCurve,
                                masterVolume = masterVolume,
                                isRecording = isRecording,
                                recordingDurationSeconds = recordingDuration,
                                spectrumBands = spectrumBands,
                                isLandscape = false
                            )
                        }
                        ActiveViewTab.DECK_B -> {
                            DeckBContent(viewModel, deckBState, padSounds, isLandscape = false) { padToCustomize = it }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheets
    when (activeModal) {
        ActiveModal.CLOUD_LIBRARY -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                containerColor = DjBackground
            ) {
                CloudLibrarySheet(
                    tracks = allTracks,
                    onLoadToDeck = { deck, track ->
                        viewModel.loadTrackToDeck(deck, track)
                        onShowInterstitial()
                    },
                    onImportLocalMp3 = { uri ->
                        viewModel.importLocalAudio(uri)
                    },
                    onScanDeviceAudio = {
                        viewModel.scanDeviceAudio()
                    },
                    onClose = { viewModel.closeModal() }
                )
            }
        }
        ActiveModal.MASTERING_RACK -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                containerColor = DjBackground
            ) {
                MasterRackSheet(
                    audioEngine = viewModel.audioEngine,
                    spectrumBands = spectrumBands,
                    onClose = { viewModel.closeModal() }
                )
            }
        }
        ActiveModal.RECORDINGS -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                containerColor = DjBackground
            ) {
                RecordingsSheet(
                    recorder = viewModel.audioEngine.sessionRecorder,
                    sessions = recordedSessions,
                    isRecording = isRecording,
                    recordingDurationSeconds = recordingDuration,
                    onToggleRecording = { viewModel.toggleRecording() },
                    onClose = { viewModel.closeModal() }
                )
            }
        }
        ActiveModal.MIDI_CONTROLLER -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeModal() },
                containerColor = DjBackground
            ) {
                MidiControllerSheet(
                    midiManager = viewModel.midiManager,
                    connectedDevices = connectedDevices,
                    currentPreset = currentPreset,
                    recentEvents = recentEvents,
                    isMidiLearnActive = isMidiLearnActive,
                    onClose = { viewModel.closeModal() }
                )
            }
        }
        ActiveModal.NONE -> {}
    }

    // Pad Customization Dialog
    padToCustomize?.let { padIdx ->
        val currentSound = if (padIdx in padSounds.indices) padSounds[padIdx] else DrumSound.KICK_808
        CustomizePadDialog(
            padIndex = padIdx,
            currentSound = currentSound,
            onSelectSound = { sound ->
                viewModel.customizePadSound(padIdx, sound)
                padToCustomize = null
            },
            onDismiss = { padToCustomize = null }
        )
    }
}

@Composable
private fun DeckAContent(
    viewModel: VirtualDjViewModel,
    deckState: DeckUiState,
    padSounds: List<DrumSound>,
    isLandscape: Boolean = false,
    onPadLongPress: (Int) -> Unit
) {
    PioneerDeckUnit(
        deckState = deckState,
        primaryColor = DeckACyan,
        padSounds = padSounds,
        onPlayToggle = { viewModel.togglePlay("A") },
        onCueDown = { viewModel.cuePressed("A") },
        onCueUp = { viewModel.cueReleased("A") },
        onSync = { viewModel.syncDeck("A") },
        onJogTouchDown = { viewModel.onJogTouchDown("A") },
        onJogTouchMove = { viewModel.onJogTouchMove("A", it) },
        onJogTouchUp = { viewModel.onJogTouchUp("A") },
        onPitchChange = { viewModel.setPitchPercent("A", it) },
        onPitchReset = { viewModel.resetPitch("A") },
        onPitchNudge = { viewModel.pitchNudge("A", it) },
        onLoopToggle = { viewModel.toggleLoop("A") },
        onHalveLoop = { viewModel.halveLoop("A") },
        onDoubleLoop = { viewModel.doubleLoop("A") },
        onSlipToggle = { viewModel.toggleSlipMode("A") },
        onScratchModeToggle = { viewModel.toggleScratchMode("A") },
        onPadModeChange = { viewModel.setPadMode("A", it) },
        onPadTrigger = { viewModel.triggerPadAction("A", it) },
        onPadLongPress = onPadLongPress,
        onEqHighChange = { viewModel.setEqHigh("A", it) },
        onEqMidChange = { viewModel.setEqMid("A", it) },
        onEqLowChange = { viewModel.setEqLow("A", it) },
        onSeek = { viewModel.seekDeck("A", it) },
        onScrub = { viewModel.scrubDeck("A", it) },
        onMasterClick = { viewModel.setMasterDeck("A") },
        onSelectTrack = { viewModel.openModal(ActiveModal.CLOUD_LIBRARY) },
        isLandscape = isLandscape
    )
}

@Composable
private fun DeckBContent(
    viewModel: VirtualDjViewModel,
    deckState: DeckUiState,
    padSounds: List<DrumSound>,
    isLandscape: Boolean = false,
    onPadLongPress: (Int) -> Unit
) {
    PioneerDeckUnit(
        deckState = deckState,
        primaryColor = DeckBAmber,
        padSounds = padSounds,
        onPlayToggle = { viewModel.togglePlay("B") },
        onCueDown = { viewModel.cuePressed("B") },
        onCueUp = { viewModel.cueReleased("B") },
        onSync = { viewModel.syncDeck("B") },
        onJogTouchDown = { viewModel.onJogTouchDown("B") },
        onJogTouchMove = { viewModel.onJogTouchMove("B", it) },
        onJogTouchUp = { viewModel.onJogTouchUp("B") },
        onPitchChange = { viewModel.setPitchPercent("B", it) },
        onPitchReset = { viewModel.resetPitch("B") },
        onPitchNudge = { viewModel.pitchNudge("B", it) },
        onLoopToggle = { viewModel.toggleLoop("B") },
        onHalveLoop = { viewModel.halveLoop("B") },
        onDoubleLoop = { viewModel.doubleLoop("B") },
        onSlipToggle = { viewModel.toggleSlipMode("B") },
        onScratchModeToggle = { viewModel.toggleScratchMode("B") },
        onPadModeChange = { viewModel.setPadMode("B", it) },
        onPadTrigger = { viewModel.triggerPadAction("B", it) },
        onPadLongPress = onPadLongPress,
        onEqHighChange = { viewModel.setEqHigh("B", it) },
        onEqMidChange = { viewModel.setEqMid("B", it) },
        onEqLowChange = { viewModel.setEqLow("B", it) },
        onSeek = { viewModel.seekDeck("B", it) },
        onScrub = { viewModel.scrubDeck("B", it) },
        onMasterClick = { viewModel.setMasterDeck("B") },
        onSelectTrack = { viewModel.openModal(ActiveModal.CLOUD_LIBRARY) },
        isLandscape = isLandscape
    )
}

@Composable
private fun MixerContent(
    viewModel: VirtualDjViewModel,
    deckAState: DeckUiState,
    deckBState: DeckUiState,
    crossfader: Float,
    crossfaderCurve: com.example.audio.CrossfaderCurve,
    masterVolume: Float,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    spectrumBands: FloatArray = FloatArray(32),
    isLandscape: Boolean = false
) {
    PioneerMixerUnit(
        deckAState = deckAState,
        deckBState = deckBState,
        crossfader = crossfader,
        crossfaderCurve = crossfaderCurve,
        masterVolume = masterVolume,
        isRecording = isRecording,
        recordingDurationSeconds = recordingDurationSeconds,
        onCrossfaderChange = { viewModel.setCrossfader(it) },
        onCrossfaderCurveChange = { viewModel.setCrossfaderCurve(it) },
        onMasterVolumeChange = { viewModel.setMasterVolume(it) },
        onVolumeFaderChange = { deck, v -> viewModel.setVolumeFader(deck, v) },
        onGainTrimChange = { deck, v -> viewModel.setGainTrim(deck, v) },
        onEqLowChange = { deck, db -> viewModel.setEqLow(deck, db) },
        onEqMidChange = { deck, db -> viewModel.setEqMid(deck, db) },
        onEqHighChange = { deck, db -> viewModel.setEqHigh(deck, db) },
        onColorFxChange = { deck, amt -> viewModel.setColorFx(deck, amt) },
        onToggleRecording = { viewModel.toggleRecording() },
        spectrumBands = spectrumBands,
        isLandscape = isLandscape
    )
}
