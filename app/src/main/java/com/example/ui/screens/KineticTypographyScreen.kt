package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.MediaStorageUtils
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.KineticConfig
import com.example.data.model.KineticStyle
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.StudioGlowCard
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KineticTypographyScreen(
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }

    var config by remember { mutableStateOf(KineticConfig()) }
    var isPreviewPlaying by remember { mutableStateOf(true) }
    var activeWordCount by remember { mutableIntStateOf(1) }
    var mediaSummary by remember { mutableStateOf("Studio Emerald Canvas") }

    val words = remember(config.text) {
        config.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    LaunchedEffect(isPreviewPlaying, words, config.wordsPerSecond) {
        if (!isPreviewPlaying || words.isEmpty()) return@LaunchedEffect
        val stepMs = (1000f / config.wordsPerSecond.coerceAtLeast(0.5f)).toLong()
        while (isPreviewPlaying) {
            activeWordCount = if (activeWordCount >= words.size) 1 else activeWordCount + 1
            delay(stepMs)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "kin_v", "mp4")
                    val localUri = Uri.fromFile(copied)
                    val meta = MediaStorageUtils.extractVideoMetadata(context, localUri)
                    localUri to meta
                }.onSuccess { (localUri, meta) ->
                    config = config.copy(
                        videoUriString = localUri.toString(),
                        durationMs = meta.durationMs.coerceIn(2000L, 60_000L)
                    )
                    mediaSummary = "Video: ${meta.displayName}"
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "kin_a", "m4a")
                    Uri.fromFile(copied) to MediaStorageUtils.getDisplayName(context, uri)
                }.onSuccess { (localUri, name) ->
                    config = config.copy(audioUriString = localUri.toString())
                    mediaSummary = "Audio: $name"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("kinetic_typography_screen")
    ) {
        NickyCutTopBar(
            title = "Kinetic Typography",
            subtitle = "Word-by-word motion text & MP4 render",
            onBack = onBack,
            actions = {
                Button(
                    onClick = {
                        val req = ExportRequest(
                            sourceType = ExportSourceType.KINETIC,
                            title = "Kinetic_${config.style.name}",
                            kinetic = config
                        )
                        onExport(json.encodeToString(req))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("kinetic_export_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", fontWeight = FontWeight.Bold)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Animated Preview Box
            item {
                val previewFamily: FontFamily = when (config.style) {
                    KineticStyle.BOLD_IMPACT -> BebasNeueFontFamily
                    KineticStyle.CYBER_MONO -> JetBrainsMonoFontFamily
                    KineticStyle.CINEMA_CLEAN -> PlusJakartaFontFamily
                    else -> SpaceGroteskFontFamily
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(config.backgroundColorHex))
                        .border(1.5.dp, NeonEmerald.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = mediaSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.TopStart)
                    )

                    IconButton(
                        onClick = { isPreviewPlaying = !isPreviewPlaying },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                    ) {
                        Icon(
                            if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Preview",
                            tint = NeonEmerald
                        )
                    }

                    val shownWords = words.take(activeWordCount.coerceAtMost(words.size))
                    val highlightSet = config.highlightedWords.map { it.lowercase() }.toSet()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(
                                when {
                                    config.verticalPosition < 0.35f -> Alignment.TopCenter
                                    config.verticalPosition > 0.65f -> Alignment.BottomCenter
                                    else -> Alignment.Center
                                }
                            )
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (config.style == KineticStyle.KINETIC_STACK) {
                            val current = shownWords.lastOrNull().orEmpty()
                            val clean = current.trim(',', '.', '!', '?').lowercase()
                            Text(
                                text = current.uppercase(),
                                fontFamily = previewFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (config.fontSizeSp * 0.9f).sp,
                                color = if (clean in highlightSet) Color(config.highlightColorHex) else Color(config.primaryColorHex),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                shownWords.forEachIndexed { idx, w ->
                                    val clean = w.trim(',', '.', '!', '?').lowercase()
                                    val isHl = clean in highlightSet || idx == shownWords.lastIndex
                                    Text(
                                        text = w,
                                        fontFamily = previewFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (config.fontSizeSp * 0.56f).sp,
                                        color = if (isHl) Color(config.highlightColorHex) else Color(config.primaryColorHex),
                                        modifier = if (isHl && config.style != KineticStyle.CINEMA_CLEAN) {
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(config.highlightColorHex).copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Media Import & Script Input
            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null, tint = NeonEmerald)
                            Spacer(Modifier.width(4.dp))
                            Text("Import Video", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Import Audio", color = TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.text,
                        onValueChange = { config = config.copy(text = it) },
                        label = { Text("Kinetic Script") },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceElevated,
                            unfocusedContainerColor = SurfaceElevated,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kinetic_text_input")
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Tap words to highlight:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        words.forEach { w ->
                            val clean = w.trim(',', '.', '!', '?').lowercase()
                            val selected = config.highlightedWords.any { it.equals(clean, ignoreCase = true) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = config.highlightedWords.toMutableList()
                                    if (selected) next.removeAll { it.equals(clean, ignoreCase = true) }
                                    else next.add(clean)
                                    config = config.copy(highlightedWords = next)
                                },
                                label = { Text(w) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Style, Color, Position & Timing Controls
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Text("Typography Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        items(KineticStyle.entries) { st ->
                            FilterChip(
                                selected = config.style == st,
                                onClick = { config = config.copy(style = st) },
                                label = { Text(st.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Text("Highlight Color", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        listOf(0xFF10F59A, 0xFF22D3EE, 0xFFA855F7, 0xFFFB923C, 0xFFFACC15).forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (config.highlightColorHex == hex) 3.dp else 1.dp,
                                        color = if (config.highlightColorHex == hex) Color.White else BorderSubtle,
                                        shape = CircleShape
                                    )
                                    .clickable { config = config.copy(highlightColorHex = hex) }
                            )
                        }
                    }

                    Text("Font Size: ${config.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = config.fontSizeSp,
                        onValueChange = { config = config.copy(fontSizeSp = it) },
                        valueRange = 24f..64f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )

                    Text("Vertical Position: ${(config.verticalPosition * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = config.verticalPosition,
                        onValueChange = { config = config.copy(verticalPosition = it) },
                        valueRange = 0.2f..0.82f,
                        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                    )

                    Text("Reveal Speed: ${String.format("%.1f", config.wordsPerSecond)} words/sec", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = config.wordsPerSecond,
                        onValueChange = { config = config.copy(wordsPerSecond = it) },
                        valueRange = 1f..5f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )
                }
            }
        }
    }
}
