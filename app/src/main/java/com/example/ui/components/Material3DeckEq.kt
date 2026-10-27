package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Material3DeckEq(
    deckId: String,
    eqHigh: Float, // -26dB to +6dB
    eqMid: Float,
    eqLow: Float,
    primaryColor: Color,
    onEqHighChange: (Float) -> Unit,
    onEqMidChange: (Float) -> Unit,
    onEqLowChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Band Kill state
    var isHighKilled by remember { mutableStateOf(false) }
    var isMidKilled by remember { mutableStateOf(false) }
    var isLowKilled by remember { mutableStateOf(false) }

    var savedHigh by remember { mutableStateOf(0f) }
    var savedMid by remember { mutableStateOf(0f) }
    var savedLow by remember { mutableStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("m3_eq_panel_$deckId"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, DjSurfaceBorder),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header: Title, EQ curve badge, Collapse toggle, Reset button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "3-Band EQ",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "3-BAND ISO EQUALIZER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "DECK $deckId • HI / MID / LOW GAIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Reset to 0dB button
                    IconButton(
                        onClick = {
                            isHighKilled = false
                            isMidKilled = false
                            isLowKilled = false
                            onEqHighChange(0f)
                            onEqMidChange(0f)
                            onEqLowChange(0f)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("eq_reset_$deckId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset EQ",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle EQ",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Visual EQ Curve Display (Canvas drawing frequency response)
                    EqFrequencyCurvePreview(
                        lowGain = if (isLowKilled) -26f else eqLow,
                        midGain = if (isMidKilled) -26f else eqMid,
                        highGain = if (isHighKilled) -26f else eqHigh,
                        primaryColor = primaryColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0C10))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // HIGH BAND SLIDER
                    EqBandSliderRow(
                        bandName = "HIGH",
                        freqRange = "3.8 kHz+",
                        currentGain = if (isHighKilled) -26f else eqHigh,
                        isKilled = isHighKilled,
                        primaryColor = primaryColor,
                        bandColor = WaveformHigh,
                        onGainChange = {
                            isHighKilled = false
                            onEqHighChange(it)
                        },
                        onKillToggle = {
                            if (isHighKilled) {
                                isHighKilled = false
                                onEqHighChange(savedHigh)
                            } else {
                                savedHigh = eqHigh
                                isHighKilled = true
                                onEqHighChange(-26f)
                            }
                        },
                        onResetBand = {
                            isHighKilled = false
                            onEqHighChange(0f)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // MID BAND SLIDER
                    EqBandSliderRow(
                        bandName = "MID",
                        freqRange = "1.2 kHz",
                        currentGain = if (isMidKilled) -26f else eqMid,
                        isKilled = isMidKilled,
                        primaryColor = primaryColor,
                        bandColor = WaveformMid,
                        onGainChange = {
                            isMidKilled = false
                            onEqMidChange(it)
                        },
                        onKillToggle = {
                            if (isMidKilled) {
                                isMidKilled = false
                                onEqMidChange(savedMid)
                            } else {
                                savedMid = eqMid
                                isMidKilled = true
                                onEqMidChange(-26f)
                            }
                        },
                        onResetBand = {
                            isMidKilled = false
                            onEqMidChange(0f)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // LOW BAND SLIDER
                    EqBandSliderRow(
                        bandName = "LOW",
                        freqRange = "220 Hz-",
                        currentGain = if (isLowKilled) -26f else eqLow,
                        isKilled = isLowKilled,
                        primaryColor = primaryColor,
                        bandColor = WaveformBass,
                        onGainChange = {
                            isLowKilled = false
                            onEqLowChange(it)
                        },
                        onKillToggle = {
                            if (isLowKilled) {
                                isLowKilled = false
                                onEqLowChange(savedLow)
                            } else {
                                savedLow = eqLow
                                isLowKilled = true
                                onEqLowChange(-26f)
                            }
                        },
                        onResetBand = {
                            isLowKilled = false
                            onEqLowChange(0f)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqBandSliderRow(
    bandName: String,
    freqRange: String,
    currentGain: Float, // -26f to +6f
    isKilled: Boolean,
    primaryColor: Color,
    bandColor: Color,
    onGainChange: (Float) -> Unit,
    onKillToggle: () -> Unit,
    onResetBand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141820), RoundedCornerShape(8.dp))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label & Frequency
        Column(
            modifier = Modifier
                .width(48.dp)
                .clickable { onResetBand() }
        ) {
            Text(
                text = bandName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = bandColor
            )
            Text(
                text = freqRange,
                fontSize = 8.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Material 3 Smooth Gain Slider
        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = currentGain,
                onValueChange = onGainChange,
                valueRange = -26f..6f,
                colors = SliderDefaults.colors(
                    thumbColor = if (isKilled) LedRed else bandColor,
                    activeTrackColor = if (isKilled) LedRedDim else bandColor,
                    inactiveTrackColor = Color(0xFF262D3B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_eq_$bandName")
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // dB Gain Value Badge
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0C10))
                .clickable { onResetBand() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isKilled) "KILL" else String.format("%+.1fdB", currentGain),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isKilled) LedRed else if (currentGain > 0f) primaryColor else TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Instant Band Kill Switch
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isKilled) LedRed else Color(0xFF202633))
                .border(
                    width = 1.dp,
                    color = if (isKilled) Color.White else DjSurfaceBorder,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable { onKillToggle() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "KILL",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = if (isKilled) Color.White else TextSecondary
            )
        }
    }
}

@Composable
private fun EqFrequencyCurvePreview(
    lowGain: Float,
    midGain: Float,
    highGain: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw 0dB reference center line
        drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1f
        )

        // Draw +6dB and -26dB subtle guide lines
        drawLine(
            color = Color(0x15FFFFFF),
            start = Offset(0f, centerY - height * 0.35f),
            end = Offset(width, centerY - height * 0.35f),
            strokeWidth = 0.5f
        )
        drawLine(
            color = Color(0x15FFFFFF),
            start = Offset(0f, centerY + height * 0.4f),
            end = Offset(width, centerY + height * 0.4f),
            strokeWidth = 0.5f
        )

        // Generate frequency response curve Path
        val path = Path()
        val numPoints = 64
        val maxGain = 6f
        val minGain = -26f

        // Normalize gains to Y offset (-1.0 to 1.0)
        fun gainToY(gain: Float): Float {
            val norm = (gain / 26f).coerceIn(-1f, 0.4f)
            return centerY - (norm * (height * 0.42f))
        }

        val lowY = gainToY(lowGain)
        val midY = gainToY(midGain)
        val highY = gainToY(highGain)

        path.moveTo(0f, lowY)

        for (i in 1..numPoints) {
            val progress = i.toFloat() / numPoints
            val x = progress * width

            // Weighted interpolation between Low (0..0.3), Mid (0.3..0.7), and High (0.7..1.0)
            val currentY = when {
                progress < 0.35f -> {
                    val t = progress / 0.35f
                    val smoothT = 0.5f - 0.5f * cos(t * Math.PI.toFloat())
                    lowY + (midY - lowY) * smoothT
                }
                progress < 0.7f -> {
                    val t = (progress - 0.35f) / 0.35f
                    val smoothT = 0.5f - 0.5f * cos(t * Math.PI.toFloat())
                    midY + (highY - midY) * smoothT
                }
                else -> {
                    highY
                }
            }

            path.lineTo(x, currentY)
        }

        // Draw curve glow and stroke
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
