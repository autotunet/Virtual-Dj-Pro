package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DrumSound
import com.example.ui.DeckUiState
import com.example.ui.PadMode
import com.example.ui.theme.*

@Composable
fun PioneerDeckUnit(
    deckState: DeckUiState,
    primaryColor: Color,
    padSounds: List<DrumSound>,
    onPlayToggle: () -> Unit,
    onCueDown: () -> Unit,
    onCueUp: () -> Unit,
    onSync: () -> Unit,
    onJogTouchDown: () -> Unit,
    onJogTouchMove: (Float) -> Unit,
    onJogTouchUp: () -> Unit,
    onPitchChange: (Float) -> Unit,
    onPitchReset: () -> Unit,
    onPitchNudge: (Float) -> Unit,
    onLoopToggle: () -> Unit,
    onHalveLoop: () -> Unit,
    onDoubleLoop: () -> Unit,
    onSlipToggle: () -> Unit,
    onScratchModeToggle: () -> Unit = {},
    onPadModeChange: (PadMode) -> Unit,
    onPadTrigger: (Int) -> Unit,
    onPadLongPress: (Int) -> Unit,
    onEqHighChange: (Float) -> Unit,
    onEqMidChange: (Float) -> Unit,
    onEqLowChange: (Float) -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onScrub: (Long) -> Unit = {},
    onMasterClick: () -> Unit = {},
    onSelectTrack: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    var landscapeDeckTab by remember { mutableStateOf(0) } // 0 = Pads, 1 = Deck EQ

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DjSurface, RoundedCornerShape(10.dp))
            .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(if (isLandscape) 4.dp else 8.dp)
    ) {
        // Track Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectTrack() }
                .background(Color(0xFF0C0F14), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Deck Badge
                Box(
                    modifier = Modifier
                        .background(primaryColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DECK ${deckState.deckId}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = deckState.trackTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${deckState.trackArtist} • ${deckState.genre}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // BPM and Key Badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.1f", deckState.bpm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryColor
                    )
                    Text(
                        text = "BPM",
                        fontSize = 8.sp,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E2532), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = deckState.key,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Real-time Canvas Waveform Viewer & Scrubbing
        WaveformView(
            deckId = deckState.deckId,
            waveformPeaks = deckState.waveformPeaks,
            waveformBass = deckState.waveformBass,
            waveformMids = deckState.waveformMids,
            waveformHighs = deckState.waveformHighs,
            liveAudioSignal = deckState.liveAudioSignal,
            isPlaying = deckState.isPlaying,
            eqLow = deckState.eqLow,
            eqMid = deckState.eqMid,
            eqHigh = deckState.eqHigh,
            currentPositionMs = deckState.currentPositionMs,
            durationMs = deckState.durationMs,
            bpm = deckState.bpm,
            deckColor = primaryColor,
            onSeek = onSeek,
            onScrub = onScrub,
            isCompact = isLandscape
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

        // Center section: Jog Wheel + Pitch Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp)
        ) {
            // Pioneer Jog Wheel Platter ("Platillo")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = if (isLandscape) 115.dp else 220.dp)
            ) {
                PioneerJogWheel(
                    deckId = deckState.deckId,
                    jogAngle = deckState.jogAngle,
                    isPlaying = deckState.isPlaying,
                    isScratching = deckState.isScratching,
                    isSlipMode = deckState.isSlipMode,
                    isScratchMode = deckState.isScratchMode,
                    scratchSpeed = deckState.scratchSpeed,
                    currentPositionMs = deckState.currentPositionMs,
                    durationMs = deckState.durationMs,
                    primaryColor = primaryColor,
                    onJogTouchDown = onJogTouchDown,
                    onJogTouchMove = onJogTouchMove,
                    onJogTouchUp = onJogTouchUp
                )

                // Platter Top-Right Corner: Quick Vinyl / CDJ Mode Toggle Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (deckState.isScratchMode) Color(0xFF132A38) else Color(0xFF1E222B))
                        .border(
                            1.dp,
                            if (deckState.isScratchMode) primaryColor else Color(0xFF404859),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onScratchModeToggle() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (deckState.isScratchMode) primaryColor else TextMuted)
                        )
                        Text(
                            text = if (deckState.isScratchMode) "VINYL" else "CDJ",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (deckState.isScratchMode) primaryColor else TextSecondary
                        )
                    }
                }
            }

            // Pitch Fader & Nudge Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(if (isLandscape) 44.dp else 52.dp)
            ) {
                Text(
                    text = String.format("%+.1f%%", deckState.pitchPercent),
                    fontSize = if (isLandscape) 8.sp else 9.sp,
                    color = if (deckState.pitchPercent != 0f) primaryColor else TextSecondary
                )
                // Vertical pitch fader
                DjVerticalFader(
                    value = ((deckState.pitchPercent + 8f) / 16f).coerceIn(0f, 1f),
                    label = "TEMPO",
                    accentColor = primaryColor,
                    onValueChange = { norm ->
                        val pitch = (norm * 16f) - 8f
                        onPitchChange(pitch)
                    }
                )
                // Pitch Reset & Nudge buttons
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 20.dp else 24.dp)
                            .background(Color(0xFF1E2532), RoundedCornerShape(4.dp))
                            .clickable { onPitchNudge(-1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .height(if (isLandscape) 20.dp else 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E2532))
                            .clickable { onPitchReset() }
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("0", fontSize = 9.sp, color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 20.dp else 24.dp)
                            .background(Color(0xFF1E2532), RoundedCornerShape(4.dp))
                            .clickable { onPitchNudge(1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

        // Pioneer Transport Buttons: CUE, PLAY, SYNC, LOOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CUE Button (Amber LED)
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 42.dp else 52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181C24))
                    .border(
                        width = 2.dp,
                        color = if (deckState.isCueing) LedAmber else LedAmber.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .testTag("btn_cue_${deckState.deckId}")
                    .clickable { onCueDown() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CUE",
                    fontSize = if (isLandscape) 11.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (deckState.isCueing) Color.White else LedAmber
                )
            }

            // PLAY / PAUSE Button (Iconic Pioneer Green LED ring)
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 46.dp else 56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181C24))
                    .border(
                        width = 2.5.dp,
                        color = if (deckState.isPlaying) LedGreen else LedGreen.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .testTag("btn_play_${deckState.deckId}")
                    .clickable { onPlayToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (deckState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = if (deckState.isPlaying) LedGreen else Color.White,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 28.dp)
                )
            }

            // Pioneer Beat-Matching SYNC & Master Clock Button with Pulsing Beat LED
            PioneerSyncButton(
                deckId = deckState.deckId,
                isSyncActive = deckState.isSyncActive,
                isMasterDeck = deckState.isMasterDeck,
                currentBeatInBar = deckState.currentBeatInBar,
                bpm = deckState.bpm,
                beatPhase = deckState.beatPhase,
                isPlaying = deckState.isPlaying,
                primaryColor = primaryColor,
                onSyncClick = onSync,
                onMasterClick = onMasterClick,
                modifier = Modifier.width(if (isLandscape) 60.dp else 72.dp)
            )

            // Loop controls
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .height(if (isLandscape) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (deckState.isLoopActive) LedAmber else Color(0xFF1E2532))
                        .clickable { onLoopToggle() }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LOOP ${deckState.loopLengthBeats}",
                        fontSize = if (isLandscape) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (deckState.isLoopActive) Color.Black else TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E2532))
                        .clickable { onHalveLoop() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("1/2", fontSize = if (isLandscape) 8.sp else 10.sp, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E2532))
                        .clickable { onDoubleLoop() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("2X", fontSize = if (isLandscape) 8.sp else 10.sp, color = TextSecondary)
                }
            }

            // Mode Toggles: SLIP and VINYL SCRATCH
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Slip Mode Toggle
                Box(
                    modifier = Modifier
                        .height(if (isLandscape) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (deckState.isSlipMode) LedRed else Color(0xFF1E2532))
                        .clickable { onSlipToggle() }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SLIP",
                        fontSize = if (isLandscape) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (deckState.isSlipMode) Color.White else TextSecondary
                    )
                }

                // Vinyl Scratch Mode Toggle
                Box(
                    modifier = Modifier
                        .height(if (isLandscape) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (deckState.isScratchMode) primaryColor else Color(0xFF1E2532))
                        .testTag("btn_scratch_toggle_${deckState.deckId}")
                        .clickable { onScratchModeToggle() }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (deckState.isScratchMode) "VINYL" else "CDJ",
                        fontSize = if (isLandscape) 8.sp else 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (deckState.isScratchMode) Color.Black else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

        if (isLandscape) {
            // Landscape Mode: Clean tab selector between Pads and Deck EQ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(DjSurfaceElevated)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf("PERCUSSION PADS" to 0, "DECK EQ" to 1).forEach { (label, idx) ->
                    val isSel = landscapeDeckTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSel) primaryColor else Color.Transparent)
                            .clickable { landscapeDeckTab = idx }
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color.Black else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (landscapeDeckTab == 0) {
                PioneerPerformancePads(
                    deckId = deckState.deckId,
                    padMode = deckState.padMode,
                    padSounds = padSounds,
                    accentColor = primaryColor,
                    onPadModeChange = onPadModeChange,
                    onPadTrigger = onPadTrigger,
                    onPadLongPress = onPadLongPress
                )
            } else {
                Material3DeckEq(
                    deckId = deckState.deckId,
                    eqHigh = deckState.eqHigh,
                    eqMid = deckState.eqMid,
                    eqLow = deckState.eqLow,
                    primaryColor = primaryColor,
                    onEqHighChange = onEqHighChange,
                    onEqMidChange = onEqMidChange,
                    onEqLowChange = onEqLowChange
                )
            }
        } else {
            // Portrait Mode: Display both Deck EQ and Performance Pads
            Material3DeckEq(
                deckId = deckState.deckId,
                eqHigh = deckState.eqHigh,
                eqMid = deckState.eqMid,
                eqLow = deckState.eqLow,
                primaryColor = primaryColor,
                onEqHighChange = onEqHighChange,
                onEqMidChange = onEqMidChange,
                onEqLowChange = onEqLowChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            PioneerPerformancePads(
                deckId = deckState.deckId,
                padMode = deckState.padMode,
                padSounds = padSounds,
                accentColor = primaryColor,
                onPadModeChange = onPadModeChange,
                onPadTrigger = onPadTrigger,
                onPadLongPress = onPadLongPress
            )
        }
    }
}
