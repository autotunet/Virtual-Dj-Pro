package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

@Composable
fun WaveformView(
    deckId: String,
    waveformPeaks: FloatArray,
    waveformBass: FloatArray = FloatArray(256) { 0.5f },
    waveformMids: FloatArray = FloatArray(256) { 0.5f },
    waveformHighs: FloatArray = FloatArray(256) { 0.5f },
    liveAudioSignal: FloatArray = FloatArray(64),
    isPlaying: Boolean = false,
    eqLow: Float = 0.0f,
    eqMid: Float = 0.0f,
    eqHigh: Float = 0.0f,
    currentPositionMs: Long,
    durationMs: Long,
    bpm: Float,
    deckColor: Color,
    onSeek: ((positionMs: Long) -> Unit)? = null,
    onScrub: ((deltaMs: Long) -> Unit)? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val totalMs = if (durationMs > 0) durationMs else 60000L
    val progress = (currentPositionMs.toFloat() / totalMs).coerceIn(0f, 1f)

    // Zoom level: visible seconds in dynamic window (4s = 2X, 8s = 4X, 16s = 8X)
    var zoomSeconds by remember { mutableStateOf(8f) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Multi-band frequency gain scales influenced directly by deck EQ controls
    val lowEqScale = (1.0f + eqLow / 12f).coerceIn(0.05f, 2.0f)
    val midEqScale = (1.0f + eqMid / 12f).coerceIn(0.05f, 2.0f)
    val highEqScale = (1.0f + eqHigh / 12f).coerceIn(0.05f, 2.0f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF090B0F))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(4.dp)
            .testTag("waveform_container_$deckId")
    ) {
        // Top Toolbar: Zoom Controls & Time Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom Selector
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(4f to "ZOOM 2X", 8f to "ZOOM 4X", 16f to "ZOOM 8X").forEach { (zoom, label) ->
                    val isSel = zoomSeconds == zoom
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSel) deckColor else Color(0xFF161A22))
                            .clickable { zoomSeconds = zoom }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.Black else TextSecondary
                        )
                    }
                }
            }

            // Real-time live status / Scrubbing indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isScrubbing) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF332000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SCRUBBING",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            color = LedAmber
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isPlaying) Color(0xFF0D2818) else Color(0xFF1E222A),
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "LIVE AUDIO" else "STANDBY",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) LedGreen else TextMuted
                        )
                    }
                }

                // Remaining time
                val remMs = (totalMs - currentPositionMs).coerceAtLeast(0)
                val remMin = remMs / 60000
                val remSec = (remMs % 60000) / 1000
                val remTenths = (remMs % 1000) / 100
                Text(
                    text = String.format("-%02d:%02d.%d", remMin, remSec, remTenths),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 1. MAIN DYNAMIC SCROLLING WAVEFORM (Pioneer 3-Band RGB + Live Audio Oscilloscope)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("waveform_scroller_$deckId")
                .pointerInput(deckId) {
                    detectDragGestures(
                        onDragStart = {
                            isScrubbing = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Dragging scrubs track forward or backward
                            val pixelsPerSecond = size.width / zoomSeconds
                            val deltaSec = -dragAmount.x / pixelsPerSecond
                            val deltaMs = (deltaSec * 1000f).toLong()
                            onScrub?.invoke(deltaMs)
                        },
                        onDragEnd = {
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            if (width <= 1f || height <= 1f) return@Canvas

            val centerY = height / 2f
            val centerX = width / 2f // Centered playhead

            // Background canvas grid
            drawRect(color = Color(0xFF07090E), size = size)

            val windowDurationMs = (zoomSeconds * 1000f)
            val windowStartMs = currentPositionMs - (windowDurationMs / 2f)
            val windowEndMs = currentPositionMs + (windowDurationMs / 2f)

            // Beat Grid Lines (Bars & Beats)
            val safeBpm = if (bpm > 0f) bpm else 128f
            val msPerBeat = (60000f / safeBpm)
            val startBeat = (windowStartMs / msPerBeat).toInt()
            val endBeat = (windowEndMs / msPerBeat).toInt() + 1

            for (b in startBeat..endBeat) {
                val beatMs = b * msPerBeat
                val beatFraction = (beatMs - windowStartMs) / windowDurationMs
                val beatX = beatFraction * width

                if (beatX in 0f..width) {
                    val isDownbeat = (b % 4 == 0)
                    drawLine(
                        color = if (isDownbeat) Color(0x88FF1744) else Color(0x22FFFFFF),
                        start = Offset(beatX, 0f),
                        end = Offset(beatX, height),
                        strokeWidth = if (isDownbeat) 1.5.dp.toPx() else 0.75.dp.toPx()
                    )
                }
            }

            // 3-Band Color Waveform Rendering (CDJ-3000 Style: Bass = Cyan, Mids = Green, Highs = Amber/Orange)
            val numPeaks = waveformPeaks.size
            if (numPeaks > 0) {
                val msPerPeak = totalMs.toFloat() / numPeaks
                val startPeakIdx = (windowStartMs / msPerPeak).toInt().coerceAtLeast(0)
                val endPeakIdx = (windowEndMs / msPerPeak).toInt().coerceAtMost(numPeaks - 1)

                val barStep = (width / (zoomSeconds * 16f)).coerceIn(2.5f, 7f)
                val barHalfWidth = barStep * 0.42f

                for (idx in startPeakIdx..endPeakIdx) {
                    val peakMs = idx * msPerPeak
                    val peakProgress = (peakMs - windowStartMs) / windowDurationMs
                    val peakX = peakProgress * width

                    if (peakX < -10f || peakX > width + 10f) continue

                    // Raw band magnitudes modulated by live EQ gain sliders
                    val bassVal = (if (idx < waveformBass.size) waveformBass[idx] else 0.5f) * lowEqScale
                    val midVal = (if (idx < waveformMids.size) waveformMids[idx] else 0.5f) * midEqScale
                    val highVal = (if (idx < waveformHighs.size) waveformHighs[idx] else 0.5f) * highEqScale

                    val totalAmp = (highVal * 0.35f + midVal * 0.45f + bassVal * 0.55f).coerceIn(0.05f, 1.0f)
                    val maxH = (height * 0.46f)

                    val upperMidHighH = (midVal * 0.5f + highVal * 0.5f) * maxH * totalAmp
                    val lowerBassH = bassVal * maxH * totalAmp

                    // Draw Lower Bass/Sub band (Neon Cyan, projecting downwards)
                    drawLine(
                        color = WaveformBass.copy(alpha = 0.9f),
                        start = Offset(peakX, centerY),
                        end = Offset(peakX, centerY + lowerBassH.coerceAtLeast(2f)),
                        strokeWidth = barHalfWidth * 2f,
                        cap = StrokeCap.Round
                    )

                    // Draw Upper Mid band (Acid Green, projecting upwards)
                    drawLine(
                        color = WaveformMid.copy(alpha = 0.92f),
                        start = Offset(peakX, centerY),
                        end = Offset(peakX, centerY - upperMidHighH.coerceAtLeast(2f)),
                        strokeWidth = barHalfWidth * 2f,
                        cap = StrokeCap.Round
                    )

                    // Draw High Frequencies (Bright Amber/Orange transient tips)
                    if (highVal > 0.25f) {
                        val tipH = highVal * maxH * 0.75f
                        drawLine(
                            color = WaveformHigh,
                            start = Offset(peakX, centerY - (upperMidHighH * 0.6f)),
                            end = Offset(peakX, centerY - tipH.coerceAtLeast(upperMidHighH)),
                            strokeWidth = barHalfWidth * 1.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Real-Time Physical Audio Signal Oscilloscope Wave (DSP live buffer)
            if (liveAudioSignal.isNotEmpty() && (isPlaying || isScrubbing)) {
                val oscPath = Path()
                val oscHalfWindowWidth = width * 0.22f // Visible within +/- 22% of playhead
                val oscStartX = centerX - oscHalfWindowWidth
                val oscEndX = centerX + oscHalfWindowWidth
                val oscSamples = liveAudioSignal.size
                val oscStepX = (oscEndX - oscStartX) / (oscSamples - 1).coerceAtLeast(1)

                var first = true
                for (i in 0 until oscSamples) {
                    val rawSample = liveAudioSignal[i]
                    val sample = if (rawSample.isNaN() || rawSample.isInfinite()) 0f else rawSample.coerceIn(-1.5f, 1.5f)
                    val oscX = oscStartX + (i * oscStepX)
                    // Window envelope (Hann window so edges blend smoothly to center line)
                    val windowFrac = sin(PI * i.toDouble() / (oscSamples - 1)).toFloat()
                    val oscY = centerY - (sample * (height * 0.38f) * windowFrac)

                    if (first) {
                        oscPath.moveTo(oscX, oscY)
                        first = false
                    } else {
                        oscPath.lineTo(oscX, oscY)
                    }
                }

                // Draw glowing live audio trace
                drawPath(
                    path = oscPath,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = oscPath,
                    color = deckColor.copy(alpha = 0.45f),
                    style = Stroke(width = 4.0.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Center Reference Zero-Axis Line
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // Dynamic Playhead Pulse Glow (Reacts to live audio amplitude)
            val currentAmp = if (liveAudioSignal.isNotEmpty()) {
                liveAudioSignal.map { if (it.isNaN() || it.isInfinite()) 0f else abs(it) }.maxOrNull() ?: 0f
            } else 0f
            val pulseGlowRadius = (4.dp.toPx() + (currentAmp * 12.dp.toPx())).coerceIn(2.dp.toPx(), 24.dp.toPx()).coerceAtLeast(1f)

            if (pulseGlowRadius > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(deckColor.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(centerX, centerY),
                        radius = pulseGlowRadius.coerceAtLeast(1f)
                    ),
                    radius = pulseGlowRadius,
                    center = Offset(centerX, centerY)
                )
            }

            // Centered CDJ Laser Needle (Pure White with deck-accent edge)
            drawLine(
                color = Color.White,
                start = Offset(centerX, 0f),
                end = Offset(centerX, height),
                strokeWidth = 2.dp.toPx()
            )

            // Playhead Triangular Marker on top and bottom
            val topNeedlePath = Path().apply {
                moveTo(centerX, 0f)
                lineTo(centerX - 4.dp.toPx(), 7.dp.toPx())
                lineTo(centerX + 4.dp.toPx(), 7.dp.toPx())
                close()
            }
            drawPath(path = topNeedlePath, color = deckColor)

            val bottomNeedlePath = Path().apply {
                moveTo(centerX, height)
                lineTo(centerX - 4.dp.toPx(), height - 7.dp.toPx())
                lineTo(centerX + 4.dp.toPx(), height - 7.dp.toPx())
                close()
            }
            drawPath(path = bottomNeedlePath, color = deckColor)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. FULL TRACK OVERVIEW MINIMAP (Shows whole song progression with tap-to-seek)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .testTag("waveform_overview_$deckId")
                .pointerInput(deckId) {
                    detectTapGestures { offset ->
                        val targetFrac = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek?.invoke((targetFrac * totalMs).toLong())
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            if (width <= 1f || height <= 1f) return@Canvas

            val centerY = height / 2f

            drawRect(color = Color(0xFF0C0E14), size = size)

            val peaksCount = waveformPeaks.size
            if (peaksCount > 0) {
                val stepX = width / peaksCount
                for (i in 0 until peaksCount) {
                    val p = waveformPeaks[i]
                    val b = if (i < waveformBass.size) waveformBass[i] else 0.5f
                    val m = if (i < waveformMids.size) waveformMids[i] else 0.5f
                    val h = if (i < waveformHighs.size) waveformHighs[i] else 0.5f

                    val totalH = (p * height * 0.85f).coerceAtLeast(2f)
                    val x = i * stepX
                    val isPast = (i.toFloat() / peaksCount) <= progress

                    val color = if (isPast) {
                        // 3-band tint past playhead
                        when {
                            b > 0.65f -> WaveformBass
                            m > 0.65f -> WaveformMid
                            h > 0.65f -> WaveformHigh
                            else -> deckColor
                        }
                    } else {
                        Color(0xFF232A36)
                    }

                    drawLine(
                        color = color,
                        start = Offset(x, centerY - totalH / 2f),
                        end = Offset(x, centerY + totalH / 2f),
                        strokeWidth = stepX.coerceAtLeast(1.2f)
                    )
                }
            }

            // Minimap Zoom Window Viewport (Indicates currently zoomed segment)
            val windowRatio = ((zoomSeconds * 1000f) / totalMs).coerceIn(0.01f, 1f)
            val maxWindowWidth = width.coerceAtLeast(12f)
            val windowWidthPx = (windowRatio * width).coerceIn(8f, maxWindowWidth)
            val maxLeft = (width - windowWidthPx).coerceAtLeast(0f)
            val windowLeftPx = if (maxLeft > 0f) {
                (progress * width - windowWidthPx / 2f).coerceIn(0f, maxLeft)
            } else 0f

            drawRoundRect(
                color = deckColor.copy(alpha = 0.25f),
                topLeft = Offset(windowLeftPx, 1f),
                size = Size(windowWidthPx, height - 2f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(windowLeftPx, 1f),
                size = Size(windowWidthPx, height - 2f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Current playhead line in minimap
            val playheadX = progress * width
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
