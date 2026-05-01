package com.github.epsilon.gui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Immutable
data class ComposeDemoState(
    val title: String = "Compose for Open Epsilon",
    val subtitle: String = "Skiko + Compose Desktop running inside a Minecraft Screen",
    val worldName: String = "Local World",
    val playerName: String = "Player",
    val progress: Float = 0.42f,
    val accentArgb: Int = 0xFF7C4DFF.toInt(),
    val backgroundArgb: Int = 0xFF10131A.toInt()
)

@Composable
fun ComposeDemoPanel(state: ComposeDemoState) {
    val accent = remember(state.accentArgb) { Color(state.accentArgb) }
    val background = remember(state.backgroundArgb) { Color(state.backgroundArgb) }
    val progress = state.progress.coerceIn(0f, 1f)
    val colorScheme = remember(accent, background) {
        darkColorScheme(
            primary = accent,
            secondary = accent.copy(alpha = 0.82f),
            surface = background,
            surfaceVariant = Color(0xFF1A1F29),
            onSurface = Color(0xFFF2F5FA),
            onSurfaceVariant = Color(0xFFB1BAC8)
        )
    }
    val panelBrush = remember(accent, background) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.22f),
                background,
                Color(0xFF0C0F15)
            )
        )
    }

    var noteText by remember { mutableStateOf("Hello from Compose in Open Epsilon") }
    var compactMode by remember { mutableStateOf(false) }
    var actionCount by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(panelBrush)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(accent.copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ε",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = state.worldName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Player: ${state.playerName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.16f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DemoStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Progress",
                                value = "${(progress * 100).toInt()}%",
                                accent = accent
                            )
                            DemoStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Actions",
                                value = actionCount.toString(),
                                accent = accent
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Compose input test") },
                    supportingText = { Text("Try typing here to verify keyboard event bridging.") },
                    singleLine = !compactMode,
                    maxLines = if (compactMode) 1 else 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { actionCount++ },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Pulse")
                    }

                    TextButton(onClick = {
                        actionCount = 0
                        noteText = "Hello from Compose in Open Epsilon"
                    }) {
                        Text("Reset")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Compact",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = compactMode,
                        onCheckedChange = { compactMode = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

