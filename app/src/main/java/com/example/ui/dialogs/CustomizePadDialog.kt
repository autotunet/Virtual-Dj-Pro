package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.DrumSound
import com.example.ui.theme.*

@Composable
fun CustomizePadDialog(
    padIndex: Int,
    currentSound: DrumSound,
    onSelectSound: (DrumSound) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DjSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DjSurfaceBorder)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CUSTOMIZE PERCUSSION PAD ${padIndex + 1}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Select Pioneer Cymbal or Electronic Drum Sound",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(DrumSound.values()) { sound ->
                        val isSelected = sound == currentSound
                        val soundColor = when (sound) {
                            DrumSound.CRASH_PIONEER, DrumSound.RIDE_PIONEER -> Color(0xFFFFD600)
                            DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN -> Color(0xFF00E5FF)
                            DrumSound.KICK_808, DrumSound.KICK_909 -> Color(0xFFFF1744)
                            DrumSound.SNARE_TRAP, DrumSound.CLAP -> Color(0xFF00E676)
                            DrumSound.AIRHORN, DrumSound.SCRATCH_DROP -> Color(0xFFFF9100)
                            else -> Color(0xFFBB86FC)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) soundColor else Color(0xFF141922))
                                .clickable { onSelectSound(sound) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sound.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = sound.category,
                                    fontSize = 9.sp,
                                    color = if (isSelected) Color(0xFF222222) else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262E3D)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}
