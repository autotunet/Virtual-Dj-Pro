package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Flagship Pioneer CDJ-3000 Style Jog Wheel Platter ("Platillo Pro").
 * Features a heavy-duty knurled metal chassis, brushed vinyl platter with anisotropic reflections,
 * micro-grooves, illuminated perimeter ring, 4-point beat markers, and high-contrast LCD center screen.
 */
@Composable
fun PioneerJogWheel(
    deckId: String,
    jogAngle: Float,
    isPlaying: Boolean,
    isScratching: Boolean,
    isSlipMode: Boolean,
    isScratchMode: Boolean = true,
    scratchSpeed: Float = 1.0f,
    currentPositionMs: Long,
    durationMs: Long,
    primaryColor: Color,
    onJogTouchDown: () -> Unit,
    onJogTouchMove: (deltaDegrees: Float) -> Unit,
    onJogTouchUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previousTouchAngle by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("jog_wheel_deck_$deckId")
            .pointerInput(deckId) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onJogTouchDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = atan2(offset.y - center.y, offset.x - center.x) * (180f / Math.PI.toFloat())
                        previousTouchAngle = angle
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val currentAngle = atan2(change.position.y - center.y, change.position.x - center.x) * (180f / Math.PI.toFloat())
                        val prev = previousTouchAngle
                        if (prev != null) {
                            var delta = currentAngle - prev
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            onJogTouchMove(delta)
                        }
                        previousTouchAngle = currentAngle
                    },
                    onDragEnd = {
                        previousTouchAngle = null
                        onJogTouchUp()
                    },
                    onDragCancel = {
                        previousTouchAngle = null
                        onJogTouchUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            if (minDim < 30f) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (minDim / 2f - 2.dp.toPx()).coerceAtLeast(15f)

            // 1. Heavy Metal Bezel Base & Drop Shadow
            drawCircle(
                color = Color(0xFF090B0F),
                radius = outerRadius,
                center = center
            )

            // Beveled Outer Metal Trim Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF333D4D), Color(0xFF141821),
                        Color(0xFF333D4D), Color(0xFF141821),
                        Color(0xFF333D4D)
                    ),
                    center = center
                ),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. 48 Precision Milled Aluminum Grip Notches (Knurled Platter Outer Ring)
            val teethCount = 48
            val toothInnerRadius = (outerRadius * 0.92f).coerceAtLeast(10f)
            for (i in 0 until teethCount) {
                val toothAngle = (i * (360f / teethCount)) * (Math.PI.toFloat() / 180f)
                val toothStartX = center.x + toothInnerRadius * cos(toothAngle)
                val toothStartY = center.y + toothInnerRadius * sin(toothAngle)
                val toothEndX = center.x + (outerRadius - 1.5.dp.toPx()) * cos(toothAngle)
                val toothEndY = center.y + (outerRadius - 1.5.dp.toPx()) * sin(toothAngle)

                drawLine(
                    color = if (i % 4 == 0) Color(0xFF4A5568) else Color(0xFF202733),
                    start = Offset(toothStartX, toothStartY),
                    end = Offset(toothEndX, toothEndY),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Outer Rubber Pitch-Bend Track Divider
            val pitchBendRimRadius = (outerRadius * 0.88f).coerceAtLeast(12f)
            drawCircle(
                color = Color(0xFF10131A),
                radius = pitchBendRimRadius,
                center = center
            )
            drawCircle(
                color = Color(0xFF05070A),
                radius = pitchBendRimRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Realistic Brushed Vinyl Platter Surface ("Platillo Central")
            val vinylRadius = (pitchBendRimRadius * 0.94f).coerceAtLeast(10f)

            // Carbon-Black Vinyl Body
            drawCircle(
                color = Color(0xFF12151D),
                radius = vinylRadius,
                center = center
            )

            // Realistic Anisotropic Metallic Light Sheen (Cross Reflections)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0x33FFFFFF), Color(0x05000000),
                        Color(0x28FFFFFF), Color(0x05000000),
                        Color(0x33FFFFFF), Color(0x05000000),
                        Color(0x28FFFFFF), Color(0x33FFFFFF)
                    ),
                    center = center
                ),
                radius = vinylRadius,
                center = center
            )

            // 8 Ultra-fine Physical Vinyl Micro-Grooves
            for (r in 1..8) {
                val grooveRadius = vinylRadius * (0.42f + r * 0.065f)
                val isMajorGroove = (r % 3 == 0)
                drawCircle(
                    color = if (isMajorGroove) Color(0x33FFFFFF) else Color(0x18FFFFFF),
                    radius = grooveRadius.coerceAtLeast(1f),
                    center = center,
                    style = Stroke(width = if (isMajorGroove) 1.2.dp.toPx() else 0.75.dp.toPx())
                )
            }

            // 4. High-Intensity Illuminated Pioneer Neon Halo Ring
            val neonRingRadius = (vinylRadius * 0.62f).coerceAtLeast(8f)
            val ringGlowColor = if (isScratching) Color(0xFFFF5252) else primaryColor
            val ringWidth = if (isScratching) 4.5.dp.toPx() else 3.dp.toPx()

            // Soft Ambient Glow
            drawCircle(
                color = ringGlowColor.copy(alpha = if (isScratching) 0.35f else 0.18f),
                radius = (neonRingRadius + 3.dp.toPx()).coerceAtLeast(1f),
                center = center,
                style = Stroke(width = 6.dp.toPx())
            )
            // Crisp Core LED Line
            drawCircle(
                color = ringGlowColor,
                radius = neonRingRadius,
                center = center,
                style = Stroke(width = ringWidth)
            )

            // 5. 4-Point Beat & Cue Reference Markers (12, 3, 6, 9 o'clock)
            val markerDist = neonRingRadius
            val markerAngles = listOf(0f, 90f, 180f, 270f)
            for (deg in markerAngles) {
                val rad = (deg - 90f) * (Math.PI.toFloat() / 180f)
                val mx = center.x + markerDist * cos(rad)
                val my = center.y + markerDist * sin(rad)
                drawCircle(
                    color = if (deg == 0f) Color(0xFFFF1744) else Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(mx, my)
                )
            }

            // 6. Deep Glass CDJ LCD Center Display Bezel
            val lcdRadius = (neonRingRadius * 0.88f).coerceAtLeast(6f)
            if (lcdRadius > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0C0F14), Color(0xFF040608)),
                        center = center,
                        radius = lcdRadius.coerceAtLeast(1f)
                    ),
                    radius = lcdRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF1E2533),
                    radius = lcdRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 7. Dynamic Rotating Vinyl Playhead Needle (CDJ Laser Marker)
            rotate(degrees = jogAngle, pivot = center) {
                val markerLength = lcdRadius * 0.88f
                val markerStart = lcdRadius * 0.38f

                // Glowing Trail Beam
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y - markerStart),
                    end = Offset(center.x, center.y - markerLength),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Red/Accent Tip Bead
                drawCircle(
                    color = if (isScratching) Color(0xFFFF1744) else primaryColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(center.x, center.y - markerLength)
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = Offset(center.x, center.y - markerLength)
                )
            }
        }

        // 8. Center LCD Digital Information Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            // Deck Badge & Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) LedGreen else TextMuted)
                )
                Text(
                    text = "DECK $deckId",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryColor,
                    letterSpacing = 0.5.sp
                )
            }

            // Elapsed Track Time Display
            val minutes = (currentPositionMs / 60000)
            val seconds = (currentPositionMs % 60000) / 1000
            val tenths = (currentPositionMs % 1000) / 100
            Text(
                text = String.format("%02d:%02d.%d", minutes, seconds, tenths),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Real-Time Platter Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            isScratching && isScratchMode -> Color(0xFF4A1015)
                            isSlipMode -> Color(0xFF332000)
                            isScratching && !isScratchMode -> Color(0xFF10283A)
                            isPlaying -> Color(0xFF0D2518)
                            else -> Color(0xFF141822)
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = when {
                        isScratching && isScratchMode -> {
                            val sign = if (scratchSpeed >= 0) "+" else ""
                            "SCRATCH $sign${String.format("%.1fx", scratchSpeed)}"
                        }
                        isScratching && !isScratchMode -> "CDJ NUDGE"
                        isSlipMode -> "SLIP ACTIVE"
                        isPlaying && isScratchMode -> "VINYL PLAY"
                        isPlaying && !isScratchMode -> "CDJ PLAY"
                        isScratchMode -> "VINYL READY"
                        else -> "CDJ READY"
                    },
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        isScratching && isScratchMode -> LedRed
                        isSlipMode -> LedAmber
                        isScratching && !isScratchMode -> DeckACyan
                        isPlaying -> LedGreen
                        else -> TextSecondary
                    }
                )
            }
        }
    }
}
