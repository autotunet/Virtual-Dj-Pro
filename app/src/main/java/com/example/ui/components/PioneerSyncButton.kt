package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.exp

@Composable
fun PioneerSyncButton(
    deckId: String,
    isSyncActive: Boolean,
    isMasterDeck: Boolean,
    currentBeatInBar: Int, // 1, 2, 3, 4
    bpm: Float,
    beatPhase: Float = 0f,
    isPlaying: Boolean = false,
    primaryColor: Color = DeckACyan,
    onSyncClick: () -> Unit,
    onMasterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Calculate tempo duration per beat in milliseconds
    val safeBpm = bpm.coerceIn(40f, 240f)
    val beatDurationMs = (60_000f / safeBpm).toInt().coerceIn(250, 1500)

    // 2. Tempo-synchronized infinite pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "SyncBeatPulseTransition")

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = beatDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    // Compute instantaneous pulse intensity:
    // If track is playing, use physical audio beatPhase; otherwise use animated pulsePhase
    val currentPhase = if (isPlaying) beatPhase else pulsePhase
    // Sharp attack at start of beat (decaying exponentially like an analog LED capacitor discharge)
    val ledIntensity = if (isSyncActive) {
        exp(-currentPhase * 4.2f).coerceIn(0.15f, 1.0f)
    } else {
        0f
    }

    // Dynamic LED color transitions
    val masterLedColor by animateColorAsState(
        targetValue = if (isMasterDeck) LedAmber else Color(0xFF231C10),
        label = "masterLedColor"
    )

    val baseLedColor = LedBlue
    val pulsingBorderColor = if (isSyncActive) {
        baseLedColor.copy(alpha = 0.45f + 0.55f * ledIntensity)
    } else {
        Color(0xFF2C3545)
    }

    val pulsingGlowAlpha = if (isSyncActive) {
        0.25f + 0.65f * ledIntensity
    } else {
        0f
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, DjSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Button: MASTER CLOCK REFERENCE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(masterLedColor)
                .border(
                    width = 1.dp,
                    color = if (isMasterDeck) LedAmber else Color(0xFF332A15),
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable { onMasterClick() }
                .testTag("btn_master_$deckId"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MASTER",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = if (isMasterDeck) Color.Black else TextSecondary,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Center Button: BEAT-MATCHING SYNC with Pulsing LED Animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSyncActive) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E5BB5).copy(alpha = 0.6f + 0.4f * ledIntensity),
                                Color(0xFF0A2244)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1C222E), Color(0xFF12161E))
                        )
                    }
                )
                .border(
                    width = if (isSyncActive) 1.5.dp else 1.dp,
                    color = pulsingBorderColor,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onSyncClick() }
                .testTag("btn_sync_$deckId"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pulsing LED Indicator Light (Analog LED Diode with Radiant Halo)
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncActive) {
                        // Radiant outer pulse glow
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(0.85f + 0.35f * ledIntensity)
                                .clip(CircleShape)
                                .background(baseLedColor.copy(alpha = pulsingGlowAlpha * 0.4f))
                        )
                    }

                    // Inner bright LED core
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(if (isSyncActive) 0.9f + 0.25f * ledIntensity else 1.0f)
                            .clip(CircleShape)
                            .background(
                                if (isSyncActive) {
                                    if (ledIntensity > 0.7f) Color(0xFFE0F7FA) else baseLedColor
                                } else {
                                    Color(0xFF384357)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSyncActive) Color.White.copy(alpha = ledIntensity) else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Illuminated "SYNC" text label
                Text(
                    text = "SYNC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSyncActive) {
                        Color.White.copy(alpha = 0.8f + 0.2f * ledIntensity)
                    } else {
                        TextSecondary
                    },
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4-Step Beat Grid Phase Meter (1 - 2 - 3 - 4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (beat in 1..4) {
                val isBeatActive = (currentBeatInBar == beat)
                val beatColor = if (isBeatActive) {
                    val alpha = if (isSyncActive) 0.75f + 0.25f * ledIntensity else 1.0f
                    if (beat == 1) LedRed.copy(alpha = alpha) else LedGreen.copy(alpha = alpha)
                } else {
                    Color(0xFF161A22)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(beatColor)
                        .border(
                            0.5.dp,
                            if (isBeatActive) Color.White.copy(alpha = if (isSyncActive) ledIntensity else 0.5f) else Color.Transparent
                        )
                )
            }
        }
    }
}
