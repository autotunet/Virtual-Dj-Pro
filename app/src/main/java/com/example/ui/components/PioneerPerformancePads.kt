package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DrumSound
import com.example.ui.PadMode
import com.example.ui.theme.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PioneerPerformancePads(
    deckId: String,
    padMode: PadMode,
    padSounds: List<DrumSound>,
    accentColor: Color,
    onPadModeChange: (PadMode) -> Unit,
    onPadTrigger: (padIndex: Int) -> Unit,
    onPadLongPress: (padIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF10131A), RoundedCornerShape(8.dp))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        // Mode Selector Tab Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val modes = listOf(
                PadMode.PERCUSSION to "PERC / CYMBALS",
                PadMode.HOT_CUE to "HOT CUE",
                PadMode.PAD_FX to "PAD FX",
                PadMode.LOOP_ROLL to "LOOP ROLL"
            )
            modes.forEach { (mode, label) ->
                val isSelected = padMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) accentColor else Color(0xFF181D26))
                        .combinedClickable(onClick = { onPadModeChange(mode) })
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 8 RGB Performance Pads (2 rows of 4)
        for (row in 0..1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0..3) {
                    val padIndex = row * 4 + col
                    val padInfo = getPadLabel(padMode, padIndex, padSounds)
                    var isPressed by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("pad_${deckId}_$padIndex")
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isPressed) {
                                        listOf(padInfo.color, padInfo.color.copy(alpha = 0.8f))
                                    } else {
                                        listOf(Color(0xFF1E2430), Color(0xFF141922))
                                    }
                                )
                            )
                            .border(
                                width = if (isPressed) 2.dp else 1.dp,
                                color = if (isPressed) Color.White else padInfo.color.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    isPressed = true
                                    onPadTrigger(padIndex)
                                },
                                onLongClick = {
                                    onPadLongPress(padIndex)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        LaunchedEffect(isPressed) {
                            if (isPressed) {
                                kotlinx.coroutines.delay(100)
                                isPressed = false
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = padInfo.title,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPressed) Color.Black else Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                            if (padInfo.subtitle.isNotEmpty()) {
                                Text(
                                    text = padInfo.subtitle,
                                    fontSize = 7.sp,
                                    color = if (isPressed) Color(0xFF222222) else TextSecondary,
                                    lineHeight = 8.sp
                                )
                            }
                        }
                    }
                }
            }
            if (row == 0) Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private data class PadDisplayInfo(
    val title: String,
    val subtitle: String,
    val color: Color
)

private fun getPadLabel(mode: PadMode, index: Int, sounds: List<DrumSound>): PadDisplayInfo {
    return when (mode) {
        PadMode.PERCUSSION -> {
            val sound = if (index in sounds.indices) sounds[index] else DrumSound.KICK_808
            val color = when (sound) {
                DrumSound.CRASH_PIONEER, DrumSound.RIDE_PIONEER -> Color(0xFFFFD600) // Gold cymbals
                DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN -> Color(0xFF00E5FF)
                DrumSound.KICK_808, DrumSound.KICK_909 -> Color(0xFFFF1744)
                DrumSound.SNARE_TRAP, DrumSound.CLAP -> Color(0xFF00E676)
                DrumSound.AIRHORN, DrumSound.SCRATCH_DROP -> Color(0xFFFF9100)
                else -> Color(0xFFBB86FC)
            }
            PadDisplayInfo(sound.displayName, sound.category, color)
        }
        PadMode.HOT_CUE -> {
            val color = when (index % 4) {
                0 -> Color(0xFFFF1744)
                1 -> Color(0xFF00E5FF)
                2 -> Color(0xFF00E676)
                else -> Color(0xFFFFD600)
            }
            PadDisplayInfo("CUE ${index + 1}", "Point", color)
        }
        PadMode.PAD_FX -> {
            val (name, col) = when (index) {
                0 -> "ECHO 1/2" to Color(0xFF00E5FF)
                1 -> "REVERB" to Color(0xFFBB86FC)
                2 -> "FLANGER" to Color(0xFF00E676)
                3 -> "BITCRUSH" to Color(0xFFFF1744)
                4 -> "DUB DELAY" to Color(0xFFFF9100)
                5 -> "SPACE VERB" to Color(0xFF7C4DFF)
                6 -> "BRAKE" to Color(0xFFFFAB00)
                else -> "GLITCH" to Color(0xFF00B0FF)
            }
            PadDisplayInfo(name, "VST", col)
        }
        PadMode.LOOP_ROLL -> {
            val beats = when (index) {
                0 -> "1/16"
                1 -> "1/8"
                2 -> "1/4"
                3 -> "1/2"
                4 -> "1 BEAT"
                5 -> "2 BEATS"
                6 -> "4 BEATS"
                else -> "8 BEATS"
            }
            PadDisplayInfo(beats, "ROLL", Color(0xFF2979FF))
        }
    }
}
