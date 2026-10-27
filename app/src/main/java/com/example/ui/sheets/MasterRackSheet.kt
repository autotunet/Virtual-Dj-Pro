package com.example.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.VirtualDjAudioEngine
import com.example.ui.components.DjRotaryKnob
import com.example.ui.components.DjVerticalFader
import com.example.ui.theme.*

@Composable
fun MasterRackSheet(
    audioEngine: VirtualDjAudioEngine,
    spectrumBands: FloatArray,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stereoWidth by remember { mutableStateOf(audioEngine.stereoWidenerAmount) }
    var limiterCeiling by remember { mutableStateOf(0.95f) }
    var multiBandCompression by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DjBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Mastering",
                    tint = LedGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MASTERING SUITE & VST RACK",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 32-Band Real-Time Spectrum Analyzer
        com.example.ui.components.SpectrumAnalyzerView(
            spectrumBands = spectrumBands,
            title = "MASTERING SUITE RTA (32 BANDS)",
            showModeSelector = true,
            showFrequencyLabels = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Master 5-Band Graphic Equalizer
        Text(
            text = "MASTER 5-BAND GRAPHIC EQUALIZER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DjSurface, RoundedCornerShape(8.dp))
                .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val freqs = listOf("60Hz", "250Hz", "1kHz", "4kHz", "12kHz")
            freqs.forEachIndexed { idx, freqLabel ->
                var gain by remember { mutableStateOf(audioEngine.masterEqGains[idx]) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DjVerticalFader(
                        value = ((gain + 12f) / 24f).coerceIn(0f, 1f),
                        label = freqLabel,
                        accentColor = LedGreen,
                        onValueChange = { norm ->
                            gain = (norm * 24f) - 12f
                            audioEngine.masterEqGains[idx] = gain
                        }
                    )
                    Text(
                        text = String.format("%+.1fdB", gain),
                        fontSize = 8.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stereo Widener & Limiter Maximizer Rack
        Text(
            text = "MASTER DYNAMICS & STEREO IMAGING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DjSurface, RoundedCornerShape(8.dp))
                .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DjRotaryKnob(
                value = stereoWidth,
                range = 0f..1f,
                label = "STEREO WIDTH",
                accentColor = DeckACyan,
                onValueChange = {
                    stereoWidth = it
                    audioEngine.stereoWidenerAmount = it
                }
            )

            DjRotaryKnob(
                value = limiterCeiling,
                range = 0.5f..1f,
                label = "PEAK LIMITER",
                accentColor = LedRed,
                onValueChange = { limiterCeiling = it }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MULTIBAND", fontSize = 9.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Switch(
                    checked = multiBandCompression,
                    onCheckedChange = { multiBandCompression = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LedGreen
                    )
                )
                Text(
                    text = if (multiBandCompression) "ACTIVE" else "BYPASS",
                    fontSize = 8.sp,
                    color = if (multiBandCompression) LedGreen else TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VST Plugin Chain Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DjSurfaceElevated),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "VST / DSP PLUGINS STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Echo / Tape Delay (Tempo-synced feedback line)\n" +
                            "• Studio Schroeder Reverb (Comb & All-Pass matrix)\n" +
                            "• LFO Modulated Flanger (Fractional delay sweep)\n" +
                            "• Dynamic Bitcrusher (16-bit to 4-bit downsampling)\n" +
                            "• Dual Biquad Resonant Color Filters (LPF / HPF)",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
