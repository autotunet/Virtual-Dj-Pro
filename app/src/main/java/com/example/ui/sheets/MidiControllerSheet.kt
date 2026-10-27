package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.midi.MidiControllerManager
import com.example.midi.MidiEvent
import com.example.midi.MidiPreset
import com.example.ui.theme.*

@Composable
fun MidiControllerSheet(
    midiManager: MidiControllerManager,
    connectedDevices: List<String>,
    currentPreset: MidiPreset,
    recentEvents: List<MidiEvent>,
    isMidiLearnActive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DjBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "MIDI",
                    tint = DeckACyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MIDI HARDWARE & CONTROLLER MAPPING",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Connected Device Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DjSurface),
            shape = RoundedCornerShape(8.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DjSurfaceBorder))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HARDWARE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = { midiManager.refreshDevices() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DeckACyan)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                connectedDevices.forEach { dev ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(LedGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = dev, fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset Selector
        Text(
            text = "HARDWARE CONTROLLER PRESET",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MidiPreset.values().take(3).forEach { preset ->
                val isSel = currentPreset == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) DeckACyan else DjSurfaceElevated)
                        .clickable { midiManager.setPreset(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.displayName,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color.Black else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MIDI Learn Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DjSurfaceElevated, RoundedCornerShape(8.dp))
                .clickable { midiManager.toggleMidiLearn() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MIDI Learn Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Touch any control on screen, then move a physical knob or pad",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = isMidiLearnActive,
                onCheckedChange = { midiManager.toggleMidiLearn() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LedAmber
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live MIDI Event Monitor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE MIDI TRAFFIC MONITOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            // Test event trigger button
            Button(
                onClick = {
                    // Send simulated Pioneer crossfader or pad hit
                    midiManager.handleIncomingMidi(0xB0, 8, (Math.random() * 127).toInt())
                },
                colors = ButtonDefaults.buttonColors(containerColor = DjSurfaceElevated),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Simulate Signal", fontSize = 9.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF090B0E), RoundedCornerShape(8.dp))
                .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (recentEvents.isEmpty()) {
                Text(
                    text = "Listening for MIDI CC and Note-On signals from USB or Bluetooth controller...",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(recentEvents) { ev ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${ev.type} CH:${ev.channel} D1:${ev.data1} D2:${ev.data2}",
                                fontSize = 10.sp,
                                color = if (ev.mappedAction != null) LedGreen else TextSecondary
                            )
                            Text(
                                text = ev.mappedAction ?: "Unmapped",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ev.mappedAction != null) DeckACyan else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
