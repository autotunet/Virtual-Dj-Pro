package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

enum class SpectrumMode {
    BARS,
    CURVE,
    MIRROR
}

/**
 * High-fidelity, real-time Canvas-based audio spectrum analyzer for DJ audio output.
 * Visualizes 32 log-spaced frequency bands (20 Hz - 20 kHz) with peak hold ballistics,
 * Pioneer multi-band frequency coloring, and selectable visualizer modes.
 */
@Composable
fun SpectrumAnalyzerView(
    spectrumBands: FloatArray,
    modifier: Modifier = Modifier,
    title: String = "REAL-TIME SPECTRUM RTA",
    showModeSelector: Boolean = true,
    showFrequencyLabels: Boolean = true,
    defaultMode: SpectrumMode = SpectrumMode.BARS
) {
    var mode by remember { mutableStateOf(defaultMode) }

    // Persistent peak hold array with ballistic gravity decay
    val numBands = 32
    val peakHold = remember { FloatArray(numBands) { 0.05f } }
    val peakHoldVelocity = remember { FloatArray(numBands) { 0f } }

    // Update peak hold ballistics whenever spectrumBands changes
    LaunchedEffect(spectrumBands) {
        val count = min(numBands, spectrumBands.size)
        for (i in 0 until count) {
            val mag = spectrumBands[i]
            if (mag >= peakHold[i]) {
                peakHold[i] = mag
                peakHoldVelocity[i] = 0f
            } else {
                // Ballistic decay under gravity
                peakHoldVelocity[i] += 0.003f
                peakHold[i] = (peakHold[i] - peakHoldVelocity[i]).coerceAtLeast(0.02f)
            }
        }
    }

    // Maximum peak level across all bands for header status
    val maxPeak = remember(spectrumBands) {
        if (spectrumBands.isNotEmpty()) {
            spectrumBands.maxOrNull() ?: 0.05f
        } else 0.05f
    }
    val peakDb = remember(maxPeak) {
        val db = 20.0f * log10(maxPeak.coerceIn(0.01f, 1.0f))
        String.format("%.1f dB", db)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF090B10))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .testTag("spectrum_analyzer_container")
    ) {
        // Spectrum Header: Title, Peak Meter Readout, and Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Active Pulse LED
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (maxPeak > 0.15f) LedGreen else TextMuted)
                )
                Text(
                    text = title,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                // Peak readout badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF141822))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = peakDb,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (maxPeak > 0.85f) LedRed else if (maxPeak > 0.5f) LedAmber else LedGreen
                    )
                }
            }

            // Mode Selector: BARS / CURVE / MIRROR
            if (showModeSelector) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SpectrumMode.values().forEach { m ->
                        val isSelected = mode == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) Color(0xFF222938) else Color.Transparent)
                                .clickable { mode = m }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = m.name,
                                fontSize = 7.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) WaveformMid else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Spectrum Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("spectrum_analyzer_canvas")
        ) {
            val width = size.width
            val height = size.height
            if (width <= 1f || height <= 1f) return@Canvas

            // 1. Dark Chamber Background with Subtle Grid Lines
            drawRect(color = Color(0xFF07080C), size = size)

            // dB Horizontal Reference Grid Lines (-6dB, -12dB, -18dB)
            val dbLevels = listOf(0.25f to "-18 dB", 0.50f to "-12 dB", 0.75f to "-6 dB")
            for ((frac, _) in dbLevels) {
                val y = height * (1.0f - frac)
                drawLine(
                    color = Color(0x1FFFFFFF),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 0.8.dp.toPx()
                )
            }

            val bands = if (spectrumBands.isNotEmpty()) spectrumBands else FloatArray(numBands) { 0.05f }
            val count = min(numBands, bands.size)
            val barSpacing = 1.5.dp.toPx()
            val availableWidth = width - (barSpacing * (count - 1))
            val barWidth = (availableWidth / count).coerceAtLeast(1.5f)

            when (mode) {
                SpectrumMode.BARS -> {
                    // Segmented LED Columns with Floating Peak Hold needles
                    for (i in 0 until count) {
                        val mag = bands[i].coerceIn(0.02f, 1.0f)
                        val barHeight = (mag * (height - 4f)).coerceAtLeast(2f)
                        val x = i * (barWidth + barSpacing)
                        val y = height - barHeight

                        // Multi-band frequency color gradient
                        val baseColor = when {
                            i < 8 -> WaveformBass    // 20Hz - 250Hz (Sub / Bass)
                            i < 20 -> WaveformMid    // 250Hz - 2.5kHz (Mid / Vocals)
                            else -> WaveformHigh     // 2.5kHz - 20kHz (Treble / Air)
                        }

                        // Gradient fill (brighter apex, darker bottom)
                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(baseColor, baseColor.copy(alpha = 0.45f)),
                            startY = y,
                            endY = height
                        )

                        // Draw LED Bar
                        drawRoundRect(
                            brush = gradientBrush,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                        )

                        // Peak Hold Floating Cap
                        val peakVal = peakHold[i].coerceIn(0.02f, 1.0f)
                        val peakY = (height - (peakVal * (height - 4f)) - 2.dp.toPx()).coerceAtLeast(0f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.95f),
                            topLeft = Offset(x, peakY),
                            size = Size(barWidth, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(0.5.dp.toPx(), 0.5.dp.toPx())
                        )
                    }
                }

                SpectrumMode.CURVE -> {
                    // Smooth Filled Gradient Curve (Continuous spectrum curve)
                    val curvePath = Path()
                    val fillPath = Path()

                    val stepX = width / (count - 1).coerceAtLeast(1)
                    var first = true

                    fillPath.moveTo(0f, height)

                    for (i in 0 until count) {
                        val mag = bands[i].coerceIn(0.02f, 1.0f)
                        val curX = i * stepX
                        val curY = height - (mag * (height - 4f))

                        if (first) {
                            curvePath.moveTo(curX, curY)
                            fillPath.lineTo(curX, curY)
                            first = false
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevMag = bands[i - 1].coerceIn(0.02f, 1.0f)
                            val prevY = height - (prevMag * (height - 4f))

                            // Cubic bezier smoothing
                            val midX = (prevX + curX) / 2f
                            curvePath.cubicTo(midX, prevY, midX, curY, curX, curY)
                            fillPath.cubicTo(midX, prevY, midX, curY, curX, curY)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    // Spectral horizontal gradient (Bass Cyan -> Mid Green -> Treble Amber)
                    val spectralGradient = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to WaveformBass.copy(alpha = 0.35f),
                            0.45f to WaveformMid.copy(alpha = 0.35f),
                            1.0f to WaveformHigh.copy(alpha = 0.35f)
                        )
                    )
                    drawPath(path = fillPath, brush = spectralGradient)

                    val strokeGradient = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to WaveformBass,
                            0.45f to WaveformMid,
                            1.0f to WaveformHigh
                        )
                    )
                    drawPath(path = curvePath, brush = strokeGradient, style = Stroke(width = 2.dp.toPx()))
                }

                SpectrumMode.MIRROR -> {
                    // Symmetrical Symmetrical Visualizer (Centered horizontal zero axis)
                    val centerY = height / 2f
                    for (i in 0 until count) {
                        val mag = bands[i].coerceIn(0.02f, 1.0f)
                        val halfBarH = (mag * (centerY - 2f)).coerceAtLeast(1.5f)
                        val x = i * (barWidth + barSpacing)

                        val baseColor = when {
                            i < 8 -> WaveformBass
                            i < 20 -> WaveformMid
                            else -> WaveformHigh
                        }

                        // Upper reflection
                        drawRoundRect(
                            color = baseColor,
                            topLeft = Offset(x, centerY - halfBarH),
                            size = Size(barWidth, halfBarH),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                        // Lower reflection (subtle dimming)
                        drawRoundRect(
                            color = baseColor.copy(alpha = 0.65f),
                            topLeft = Offset(x, centerY),
                            size = Size(barWidth, halfBarH),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }

                    // Center hairline
                    drawLine(
                        color = Color(0x44FFFFFF),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1f
                    )
                }
            }
        }

        // Frequency Legend Labels (60Hz, 250Hz, 1kHz, 4kHz, 16kHz)
        if (showFrequencyLabels) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("25Hz", "60Hz", "250Hz", "1kHz", "4kHz", "10kHz", "20kHz").forEach { freq ->
                    Text(
                        text = freq,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
