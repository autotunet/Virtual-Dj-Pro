package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

@Composable
fun DjRotaryKnob(
    value: Float, // Normalized -1f to 1f OR 0f to 1f
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    label: String,
    accentColor: Color = DeckACyan,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val normVal = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextSecondary,
            lineHeight = 10.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .testTag("knob_$label")
                .pointerInput(label) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Vertical drag gesture: dragging up increases, down decreases
                        val delta = -dragAmount.y / 150f
                        val currentRange = range.endInclusive - range.start
                        val newVal = (value + delta * currentRange).coerceIn(range.start, range.endInclusive)
                        onValueChange(newVal)
                    }
                }
        ) {
            if (size.minDimension < 10f) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f - 3.dp.toPx()).coerceAtLeast(2f)

            // 1. Knob background body
            drawCircle(color = KnobBody, radius = radius, center = center)
            drawCircle(
                color = DjSurfaceBorder,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 2. Arc Track (from 135 deg to 405 deg = 270 deg sweep)
            val startAngle = 135f
            val sweepTotal = 270f
            drawArc(
                color = Color(0xFF222834),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Active Arc Value
            val activeSweep = normVal * sweepTotal
            drawArc(
                color = accentColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Knob Needle pointer
            val pointerAngle = (startAngle + activeSweep) * (Math.PI.toFloat() / 180f)
            val innerR = radius * 0.3f
            val outerR = radius * 0.85f
            drawLine(
                color = Color.White,
                start = Offset(center.x + innerR * cos(pointerAngle), center.y + innerR * sin(pointerAngle)),
                end = Offset(center.x + outerR * cos(pointerAngle), center.y + outerR * sin(pointerAngle)),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun DjVerticalFader(
    value: Float, // 0.0 to 1.0
    label: String,
    accentColor: Color = DeckACyan,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .width(28.dp)
                .height(110.dp)
                .testTag("fader_$label")
                .pointerInput(label) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / size.height
                        onValueChange((value + delta).coerceIn(0f, 1f))
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val trackX = width / 2f
            val thumbHeight = 18.dp.toPx()
            val thumbWidth = 24.dp.toPx()

            // Track slot
            drawRoundRect(
                color = Color(0xFF0B0D12),
                topLeft = Offset(trackX - 3.dp.toPx(), 0f),
                size = Size(6.dp.toPx(), height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Scale tick marks
            for (i in 0..6) {
                val y = height * (i / 6f)
                drawLine(
                    color = if (i == 3) accentColor else Color(0xFF333B48),
                    start = Offset(2.dp.toPx(), y),
                    end = Offset(trackX - 5.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = if (i == 3) accentColor else Color(0xFF333B48),
                    start = Offset(trackX + 5.dp.toPx(), y),
                    end = Offset(width - 2.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Fader thumb
            val thumbY = (1f - value) * (height - thumbHeight)
            drawRoundRect(
                color = FaderThumb,
                topLeft = Offset((width - thumbWidth) / 2f, thumbY),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // White centerline on thumb
            drawLine(
                color = accentColor,
                start = Offset(2.dp.toPx(), thumbY + thumbHeight / 2f),
                end = Offset(width - 2.dp.toPx(), thumbY + thumbHeight / 2f),
                strokeWidth = 2.dp.toPx()
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextSecondary,
            lineHeight = 10.sp
        )
    }
}

@Composable
fun DjVuMeter(
    level: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    val numBars = 10
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(6.dp)
            .height(90.dp)
            .background(Color(0xFF090B0E), RoundedCornerShape(2.dp))
            .padding(1.dp)
    ) {
        // Render from top (high level = red) to bottom (low level = green)
        for (i in (numBars - 1) downTo 0) {
            val barThreshold = i.toFloat() / numBars
            val isLit = level >= barThreshold
            val barColor = when {
                i >= 8 -> if (isLit) LedRed else LedRedDim
                i >= 6 -> if (isLit) LedAmber else Color(0xFF3E3500)
                else -> if (isLit) LedGreen else LedGreenDim
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(barColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
fun DjCrossfader(
    value: Float, // -1.0f to +1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val normVal = ((value + 1f) / 2f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "A (CH1)", fontSize = 10.sp, color = DeckACyan)
            Text(text = "CROSSFADER", fontSize = 10.sp, color = TextSecondary)
            Text(text = "B (CH2)", fontSize = 10.sp, color = DeckBAmber)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("crossfader")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = (dragAmount.x / size.width) * 2f
                        onValueChange((value + delta).coerceIn(-1f, 1f))
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val trackY = height / 2f
            val thumbWidth = 24.dp.toPx()
            val thumbHeight = height - 4.dp.toPx()

            // Crossfader track slot
            drawRoundRect(
                color = Color(0xFF0B0D12),
                topLeft = Offset(0f, trackY - 3.dp.toPx()),
                size = Size(width, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Center notch
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(width / 2f, 2.dp.toPx()),
                end = Offset(width / 2f, height - 2.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )

            // Thumb
            val thumbX = normVal * (width - thumbWidth)
            drawRoundRect(
                color = FaderThumb,
                topLeft = Offset(thumbX, 2.dp.toPx()),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Center indicator stripe
            drawLine(
                color = if (value < -0.05f) DeckACyan else if (value > 0.05f) DeckBAmber else Color.White,
                start = Offset(thumbX + thumbWidth / 2f, 4.dp.toPx()),
                end = Offset(thumbX + thumbWidth / 2f, height - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
