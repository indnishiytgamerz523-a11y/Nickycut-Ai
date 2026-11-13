package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.MediaStorageUtils
import com.example.data.model.CaptionStyle
import com.example.data.model.CaptionsExportConfig
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.SubtitleSegment
import com.example.data.repository.GeminiCreatorRepository
import com.example.media.LiveSpeechController
import com.example.media.VideoCanvasRenderer
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun AutoCaptionsScreen(
    geminiRepository: GeminiCreatorRepository,
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val speechController = remember { LiveSpeechController(context) }

    DisposableEffect(Unit) {
        onDispose { speechController.stopListening() }
    }

    val segments = remember {
        mutableStateListOf(
            SubtitleSegment(startMs = 0L, endMs = 2400L, text = "Welcome to NickyCut AI", highlightWord = "NickyCut"),
            SubtitleSegment(startMs = 2500L, endMs = 5200L, text = "Edit captions in Hindi or English", highlightWord = "Hindi")
        )
    }
    var selectedLanguage by remember { mutableStateOf("English" to "en-US") }
    var captionStyle by remember { mutableStateOf(CaptionStyle.HORMOZI_POP) }
    var importedVideoUri by remember { mutableStateOf<String?>(null) }
    var importedMediaLabel by remember { mutableStateOf("No media imported (Live Mic / Manual ready)") }
    var mediaDurationMs by remember { mutableStateOf(6000L) }
    var isListening by remember { mutableStateOf(false) }
    var partialSpeech by remember { mutableStateOf("") }
    var isPolishing by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "cap_v", "mp4")
                    val localUri = Uri.fromFile(copied)
                    val meta = MediaStorageUtils.extractVideoMetadata(context, localUri)
                    localUri to meta
                }.onSuccess { (localUri, meta) ->
                    importedVideoUri = localUri.toString()
                    mediaDurationMs = meta.durationMs.coerceAtLeast(2000L)
                    importedMediaLabel = "Imported: ${meta.displayName} (${MediaStorageUtils.formatDuration(meta.durationMs)})"
                    statusNotice = "Android SpeechRecognizer listens to live microphone input rather than decoding pre-recorded video files offline. Tap Record Mic to dictate captions or edit segments below."
                }.onFailure { e ->
                    statusNotice = e.localizedMessage ?: "Failed to import video."
                }
            }
        }
    }

    val srtExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val srt = VideoCanvasRenderer.generateSrtString(segments)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(srt.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                statusNotice = "Saved SRT subtitle file successfully."
            }.onFailure { e ->
                statusNotice = e.localizedMessage ?: "Failed to save SRT file."
            }
        }
    }

    fun startSpeechCapture() {
        isListening = true
        partialSpeech = ""
        statusNotice = null
        speechController.startListening(
            languageTag = selectedLanguage.second,
            onPartialResult = { partialSpeech = it },
            onFinalResult = { text ->
                isListening = false
                partialSpeech = ""
                val nextStart = (segments.maxOfOrNull { it.endMs } ?: 0L) + 150L
                val nextEnd = nextStart + (text.split(" ").size * 420L).coerceIn(1800L, 5500L)
                val firstLongWord = text.split(" ").firstOrNull { it.length > 3 }.orEmpty()
                segments.add(SubtitleSegment(startMs = nextStart, endMs = nextEnd, text = text, highlightWord = firstLongWord))
            },
            onError = { err ->
                isListening = false
                partialSpeech = ""
                statusNotice = err
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeechCapture()
        else statusNotice = "Microphone permission is required for SpeechRecognizer live captions."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("auto_captions_screen")
    ) {
        NickyCutTopBar(
            title = "Auto Captions",
            subtitle = "SpeechRecognizer + SRT + Burned-in Video",
            onBack = onBack,
            actions = {
                Button(
                    onClick = {
                        val req = ExportRequest(
                            sourceType = ExportSourceType.CAPTIONS,
                            title = "NickyCut_Captioned",
                            captions = CaptionsExportConfig(
                                segments = segments.toList(),
                                style = captionStyle,
                                videoUriString = importedVideoUri,
                                durationMs = mediaDurationMs
                            )
                        )
                        onExport(json.encodeToString(req))
                    },
                    enabled = segments.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("captions_export_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Video", fontWeight = FontWeight.Bold)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Text(importedMediaLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Pick Video", color = TextPrimary)
                        }
                        Button(
                            onClick = {
                                if (isListening) {
                                    speechController.stopListening()
                                    isListening = false
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) startSpeechCapture()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) RoseError else NeonEmerald,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("captions_mic_button")
                        ) {
                            Icon(if (isListening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isListening) "Stop Mic" else "Dictate Mic", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (partialSpeech.isNotBlank()) {
                        Text(
                            text = "Listening: \"$partialSpeech\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonEmerald,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Recognition Language", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(
                            "English" to "en-US",
                            "Hindi" to "hi-IN",
                            "Hinglish" to "en-IN"
                        ).forEach { pair ->
                            FilterChip(
                                selected = selectedLanguage == pair,
                                onClick = { selectedLanguage = pair },
                                label = { Text(pair.first) },
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

            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Text("Caption Style & Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        items(CaptionStyle.entries) { style ->
                            FilterChip(
                                selected = captionStyle == style,
                                onClick = { captionStyle = style },
                                label = { Text(style.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    // Live preview pill
                    val previewSeg = segments.firstOrNull()
                    if (previewSeg != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF040907))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = previewSeg.text,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (captionStyle == CaptionStyle.EMERALD_BOX) NeonEmerald else Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { srtExporter.launch("nickycut_captions.srt") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_srt_button")
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Save .SRT", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isPolishing = true
                                    statusNotice = null
                                    runCatching {
                                        val polished = geminiRepository.polishCaptionsToHinglish(segments.map { it.text })
                                        polished.forEachIndexed { idx, txt ->
                                            if (idx < segments.size) {
                                                segments[idx] = segments[idx].copy(text = txt)
                                            }
                                        }
                                    }.onSuccess {
                                        statusNotice = "Polished captions into natural Hinglish via Gemini."
                                    }.onFailure { e ->
                                        statusNotice = e.localizedMessage ?: "Gemini polish requires internet connection."
                                    }
                                    isPolishing = false
                                }
                            },
                            enabled = !isPolishing && segments.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleAccent)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPolishing) "..." else "AI Hinglish", color = TextPrimary)
                        }
                    }
                }
            }

            statusNotice?.let { msg ->
                item { StatusNoticeBanner(message = msg, type = NoticeType.INFO) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtitle Segments (${segments.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    OutlinedButton(
                        onClick = {
                            val start = (segments.maxOfOrNull { it.endMs } ?: 0L) + 100L
                            segments.add(SubtitleSegment(startMs = start, endMs = start + 2500L, text = "New caption line"))
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = NeonEmerald)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Line", color = TextPrimary)
                    }
                }
            }

            itemsIndexed(segments, key = { _, seg -> seg.id }) { idx, seg ->
                StudioGlowCard(accentColor = BorderSubtle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimecodePill(
                            text = "${MediaStorageUtils.formatDuration(seg.startMs)} -> ${MediaStorageUtils.formatDuration(seg.endMs)}",
                            accent = CyanAccent
                        )
                        IconButton(onClick = { segments.removeAt(idx) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Segment", tint = RoseError)
                        }
                    }
                    OutlinedTextField(
                        value = seg.text,
                        onValueChange = { segments[idx] = seg.copy(text = it) },
                        label = { Text("Caption Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = seg.highlightWord,
                        onValueChange = { segments[idx] = seg.copy(highlightWord = it) },
                        label = { Text("Highlight Word") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Duration: ${((seg.endMs - seg.startMs) / 1000f)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Slider(
                        value = (seg.endMs - seg.startMs).toFloat().coerceIn(500f, 8000f),
                        onValueChange = { dur ->
                            segments[idx] = seg.copy(endMs = seg.startMs + dur.toLong())
                        },
                        valueRange = 500f..8000f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )
                }
            }
        }
    }
}
