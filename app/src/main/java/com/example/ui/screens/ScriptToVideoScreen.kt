package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.MediaStorageUtils
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.SceneItem
import com.example.data.model.ScriptToVideoConfig
import com.example.media.TextToSpeechController
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ScriptToVideoScreen(
    initialScript: String,
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val ttsController = remember { TextToSpeechController(context) }

    DisposableEffect(Unit) {
        onDispose { ttsController.release() }
    }
    val voices by ttsController.voices.collectAsStateWithLifecycle()

    var scriptInput by remember {
        mutableStateOf(
            initialScript.ifBlank {
                "Most creators spend hours editing simple Reels.\nWith NickyCut AI, split your script into clean scenes offline.\nAdd your own photos, video clips, and voiceover in minutes."
            }
        )
    }
    val scenes = remember { mutableStateListOf<SceneItem>() }
    var targetSceneIndex by remember { mutableIntStateOf(0) }
    var audioUriString by remember { mutableStateOf<String?>(null) }
    var audioLabel by remember { mutableStateOf("No narration audio attached") }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    fun splitScriptIntoScenes() {
        val parts = scriptInput
            .split(Regex("\\n+|(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        scenes.clear()
        parts.forEachIndexed { idx, line ->
            val dur = (line.split(" ").size * 450L).coerceIn(2500L, 7000L)
            scenes.add(
                SceneItem(
                    index = idx + 1,
                    narration = line,
                    visualDescription = "Visual clip or photo for scene ${idx + 1}",
                    overlayText = line,
                    durationMs = dur
                )
            )
        }
    }

    remember(Unit) { splitScriptIntoScenes(); true }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && targetSceneIndex in scenes.indices) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "s2v_img", "jpg")
                    Uri.fromFile(copied).toString()
                }.onSuccess { localUri ->
                    scenes[targetSceneIndex] = scenes[targetSceneIndex].copy(mediaUriString = localUri, isVideo = false)
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && targetSceneIndex in scenes.indices) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "s2v_vid", "mp4")
                    Uri.fromFile(copied).toString()
                }.onSuccess { localUri ->
                    scenes[targetSceneIndex] = scenes[targetSceneIndex].copy(mediaUriString = localUri, isVideo = true)
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "s2v_aud", "m4a")
                    Uri.fromFile(copied).toString() to MediaStorageUtils.getDisplayName(context, uri)
                }.onSuccess { (localUri, name) ->
                    audioUriString = localUri
                    audioLabel = "Attached audio: $name"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("script_to_video_screen")
    ) {
        NickyCutTopBar(
            title = "Script to Video",
            subtitle = "Storyboard assembly using your gallery media & voice",
            onBack = onBack,
            actions = {
                Button(
                    onClick = {
                        val req = ExportRequest(
                            sourceType = ExportSourceType.SCRIPT_TO_VIDEO,
                            title = "NickyCut_Storyboard",
                            scriptToVideo = ScriptToVideoConfig(
                                title = "NickyCut Storyboard",
                                scenes = scenes.toList(),
                                audioUriString = audioUriString
                            )
                        )
                        onExport(json.encodeToString(req))
                    },
                    enabled = scenes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("s2v_export_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Render", fontWeight = FontWeight.Bold)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusNoticeBanner(
                    message = "Assembles your selected gallery photos/videos, text overlays, and narration. Does not generate synthetic AI stock footage.",
                    type = NoticeType.INFO
                )
            }

            item {
                StudioGlowCard(accentColor = OrangeAccent) {
                    Text("Script Input", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scriptInput,
                        onValueChange = { scriptInput = it },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("s2v_script_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { splitScriptIntoScenes() },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("s2v_split_scenes_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Split Scenes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Text("Voice Narration", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(audioLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Import Audio", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val wav = MediaStorageUtils.createTempFile(context, "s2v_tts", "wav")
                                        val fullNarration = scenes.joinToString(". ") { it.narration }
                                        ttsController.synthesizeToWavFile(
                                            text = fullNarration,
                                            voiceOption = voices.firstOrNull { !it.requiresNetwork } ?: voices.firstOrNull(),
                                            speed = 1.0f,
                                            pitch = 1.0f,
                                            outputFile = wav
                                        )
                                        Uri.fromFile(wav).toString()
                                    }.onSuccess { uriStr ->
                                        audioUriString = uriStr
                                        audioLabel = "Generated offline TTS narration"
                                        statusNotice = "TTS narration generated and linked to storyboard."
                                    }.onFailure { e ->
                                        statusNotice = e.localizedMessage ?: "Failed to generate TTS narration."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = NeonEmerald)
                            Spacer(Modifier.width(4.dp))
                            Text("Auto TTS", color = TextPrimary)
                        }
                    }
                }
            }

            statusNotice?.let {
                item { StatusNoticeBanner(message = it, type = NoticeType.SUCCESS) }
            }

            item {
                Text("Storyboard Timeline (${scenes.size} Scenes)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }

            itemsIndexed(scenes, key = { _, s -> s.id }) { idx, scene ->
                StudioGlowCard(accentColor = NeonEmerald) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimecodePill(text = "SCENE ${scene.index} • ${MediaStorageUtils.formatDuration(scene.durationMs)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    targetSceneIndex = idx
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(4.dp))
                                Text("Photo", color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    targetSceneIndex = idx
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = null, tint = NeonEmerald)
                                Spacer(Modifier.width(4.dp))
                                Text("Video", color = TextPrimary)
                            }
                        }
                    }

                    if (!scene.mediaUriString.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = Uri.parse(scene.mediaUriString),
                            contentDescription = "Scene ${scene.index} media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scene.overlayText,
                        onValueChange = { scenes[idx] = scene.copy(overlayText = it) },
                        label = { Text("Overlay Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = scene.visualDescription,
                        onValueChange = { scenes[idx] = scene.copy(visualDescription = it) },
                        label = { Text("Scene Visual Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Scene Duration: ${String.format("%.1f", scene.durationMs / 1000f)}s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = scene.durationMs.toFloat(),
                        onValueChange = { scenes[idx] = scene.copy(durationMs = it.toLong()) },
                        valueRange = 1500f..10000f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )
                }
            }
        }
    }
}
