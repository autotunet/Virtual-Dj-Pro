package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
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
import com.example.audio.CrossfaderCurve
import com.example.ui.DeckUiState
import com.example.ui.theme.*

enum class EqDisplayMode {
    SLIDERS,
    KNOBS
}

@Composable
fun PioneerMixerUnit(
    deckAState: DeckUiState,
    deckBState: DeckUiState,
    crossfader: Float,
    crossfaderCurve: CrossfaderCurve,
    masterVolume: Float,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    onCrossfaderChange: (Float) -> Unit,
    onCrossfaderCurveChange: (CrossfaderCurve) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onVolumeFaderChange: (deck: String, Float) -> Unit,
    onGainTrimChange: (deck: String, Float) -> Unit,
    onEqLowChange: (deck: String, Float) -> Unit,
    onEqMidChange: (deck: String, Float) -> Unit,
    onEqHighChange: (deck: String, Float) -> Unit,
    onColorFxChange: (deck: String, Float) -> Unit,
    onToggleRecording: () -> Unit,
    spectrumBands: FloatArray = FloatArray(32) { 0.05f },
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    var eqMode by remember { mutableStateOf(if (isLandscape) EqDisplayMode.KNOBS else EqDisplayMode.SLIDERS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DjSurface, RoundedCornerShape(10.dp))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(10.dp))
            .padding(if (isLandscape) 4.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mixer Top Bar: Master Level & REC Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Recording Button & Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isRecording) Color(0xFF3B1015) else Color(0xFF1E2532))
                    .clickable { onToggleRecording() }
                    .padding(horizontal = 8.dp, vertical = if (isLandscape) 3.dp else 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = "Record",
                    tint = if (isRecording) LedRed else TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val minutes = recordingDurationSeconds / 60
                val seconds = recordingDurationSeconds % 60
                Text(
                    text = if (isRecording) String.format("REC %02d:%02d", minutes, seconds) else "REC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) LedRed else TextPrimary
                )
            }

            Text(
                text = "PIONEER DJM",
                fontSize = if (isLandscape) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            // Master Volume Knob
            DjRotaryKnob(
                value = masterVolume,
                range = 0f..1f,
                label = "MASTER",
                accentColor = Color.White,
                onValueChange = onMasterVolumeChange
            )
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 3.dp else 8.dp))

        // Integrated Real-Time Spectrum Analyzer Composable
        SpectrumAnalyzerView(
            spectrumBands = spectrumBands,
            title = "DJM MASTER SPECTRUM",
            showModeSelector = !isLandscape,
            showFrequencyLabels = !isLandscape,
            defaultMode = SpectrumMode.BARS,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

        // EQ Mode Selector: M3 SLIDERS vs ROTARY KNOBS
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(DjSurfaceElevated)
                .padding(2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (eqMode == EqDisplayMode.SLIDERS) DeckACyan else Color.Transparent)
                    .clickable { eqMode = EqDisplayMode.SLIDERS }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M3 EQ SLIDERS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (eqMode == EqDisplayMode.SLIDERS) Color.Black else TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (eqMode == EqDisplayMode.KNOBS) DeckACyan else Color.Transparent)
                    .clickable { eqMode = EqDisplayMode.KNOBS }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ROTARY KNOBS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (eqMode == EqDisplayMode.KNOBS) Color.Black else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dual Channels (CH 1 = Deck A, CH 2 = Deck B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Channel 1 (Deck A)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "CH 1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeckACyan)
                DjRotaryKnob(
                    value = deckAState.gainTrim,
                    range = 0f..2f,
                    label = "TRIM",
                    accentColor = DeckACyan,
                    onValueChange = { onGainTrimChange("A", it) }
                )

                if (eqMode == EqDisplayMode.SLIDERS) {
                    // Material 3 EQ Sliders for Channel 1
                    MixerChannelM3EqSliders(
                        deckId = "A",
                        eqHigh = deckAState.eqHigh,
                        eqMid = deckAState.eqMid,
                        eqLow = deckAState.eqLow,
                        accentColor = DeckACyan,
                        onEqHighChange = { onEqHighChange("A", it) },
                        onEqMidChange = { onEqMidChange("A", it) },
                        onEqLowChange = { onEqLowChange("A", it) }
                    )
                } else {
                    DjRotaryKnob(
                        value = deckAState.eqHigh,
                        range = -26f..6f,
                        label = "HI",
                        accentColor = DeckACyan,
                        onValueChange = { onEqHighChange("A", it) }
                    )
                    DjRotaryKnob(
                        value = deckAState.eqMid,
                        range = -26f..6f,
                        label = "MID",
                        accentColor = DeckACyan,
                        onValueChange = { onEqMidChange("A", it) }
                    )
                    DjRotaryKnob(
                        value = deckAState.eqLow,
                        range = -26f..6f,
                        label = "LOW",
                        accentColor = DeckACyan,
                        onValueChange = { onEqLowChange("A", it) }
                    )
                }

                DjRotaryKnob(
                    value = deckAState.colorFx,
                    range = -1f..1f,
                    label = "COLOR FX",
                    accentColor = DeckACyan,
                    onValueChange = { onColorFxChange("A", it) }
                )

                // Fader + VU Meter Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    DjVuMeter(level = deckAState.peakMeterL)
                    Spacer(modifier = Modifier.width(6.dp))
                    DjVerticalFader(
                        value = deckAState.volumeFader,
                        label = "CH1",
                        accentColor = DeckACyan,
                        onValueChange = { onVolumeFaderChange("A", it) }
                    )
                }
            }

            // Center Channel Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DjSurfaceBorder)
            )

            // Channel 2 (Deck B)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "CH 2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeckBAmber)
                DjRotaryKnob(
                    value = deckBState.gainTrim,
                    range = 0f..2f,
                    label = "TRIM",
                    accentColor = DeckBAmber,
                    onValueChange = { onGainTrimChange("B", it) }
                )

                if (eqMode == EqDisplayMode.SLIDERS) {
                    // Material 3 EQ Sliders for Channel 2
                    MixerChannelM3EqSliders(
                        deckId = "B",
                        eqHigh = deckBState.eqHigh,
                        eqMid = deckBState.eqMid,
                        eqLow = deckBState.eqLow,
                        accentColor = DeckBAmber,
                        onEqHighChange = { onEqHighChange("B", it) },
                        onEqMidChange = { onEqMidChange("B", it) },
                        onEqLowChange = { onEqLowChange("B", it) }
                    )
                } else {
                    DjRotaryKnob(
                        value = deckBState.eqHigh,
                        range = -26f..6f,
                        label = "HI",
                        accentColor = DeckBAmber,
                        onValueChange = { onEqHighChange("B", it) }
                    )
                    DjRotaryKnob(
                        value = deckBState.eqMid,
                        range = -26f..6f,
                        label = "MID",
                        accentColor = DeckBAmber,
                        onValueChange = { onEqMidChange("B", it) }
                    )
                    DjRotaryKnob(
                        value = deckBState.eqLow,
                        range = -26f..6f,
                        label = "LOW",
                        accentColor = DeckBAmber,
                        onValueChange = { onEqLowChange("B", it) }
                    )
                }

                DjRotaryKnob(
                    value = deckBState.colorFx,
                    range = -1f..1f,
                    label = "COLOR FX",
                    accentColor = DeckBAmber,
                    onValueChange = { onColorFxChange("B", it) }
                )

                // Fader + VU Meter Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    DjVerticalFader(
                        value = deckBState.volumeFader,
                        label = "CH2",
                        accentColor = DeckBAmber,
                        onValueChange = { onVolumeFaderChange("B", it) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    DjVuMeter(level = deckBState.peakMeterR)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Crossfader Section
        DjCrossfader(
            value = crossfader,
            onValueChange = onCrossfaderChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Crossfader Curve Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "CURVE: ", fontSize = 9.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            CrossfaderCurve.values().forEach { curve ->
                val isSel = crossfaderCurve == curve
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSel) Color(0xFF2C3545) else Color.Transparent)
                        .clickable { onCrossfaderCurveChange(curve) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = curve.name,
                        fontSize = 8.sp,
                        color = if (isSel) Color.White else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MixerChannelM3EqSliders(
    deckId: String,
    eqHigh: Float,
    eqMid: Float,
    eqLow: Float,
    accentColor: Color,
    onEqHighChange: (Float) -> Unit,
    onEqMidChange: (Float) -> Unit,
    onEqLowChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MixerCompactSlider(
            band = "HI",
            gain = eqHigh,
            color = WaveformHigh,
            onGainChange = onEqHighChange
        )
        MixerCompactSlider(
            band = "MID",
            gain = eqMid,
            color = WaveformMid,
            onGainChange = onEqMidChange
        )
        MixerCompactSlider(
            band = "LOW",
            gain = eqLow,
            color = WaveformBass,
            onGainChange = onEqLowChange
        )
    }
}

@Composable
private fun MixerCompactSlider(
    band: String,
    gain: Float,
    color: Color,
    onGainChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = band,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = if (gain <= -24f) "KILL" else String.format("%+.1f", gain),
                fontSize = 8.sp,
                color = if (gain <= -24f) LedRed else TextSecondary
            )
        }
        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -26f..6f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color(0xFF222834)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        )
    }
}
