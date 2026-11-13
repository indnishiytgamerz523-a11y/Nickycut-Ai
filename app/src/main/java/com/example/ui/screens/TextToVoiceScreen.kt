package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaStorageUtils
import com.example.media.AudioEngine
import com.example.media.TextToSpeechController
import com.example.media.TtsVoiceOption
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TextToVoiceScreen(
    initialText: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ttsController = remember { TextToSpeechController(context) }

    DisposableEffect(Unit) {
        onDispose { ttsController.release() }
    }

    val voices by ttsController.voices.collectAsStateWithLifecycle()
    val isReady by ttsController.isReady.collectAsStateWithLifecycle()
    val isSpeaking by ttsController.isSpeaking.collectAsStateWithLifecycle()
    val initError by ttsController.initError.collectAsStateWithLifecycle()

    var scriptText by remember {
        mutableStateOf(
            initialText.ifBlank {
                "Welcome to NickyCut AI. Turn any script into crisp studio narration offline."
            }
        )
    }
    var languageFilter by remember { mutableStateOf("All") }
    var selectedVoice by remember { mutableStateOf<TtsVoiceOption?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var saveFormat by remember { mutableStateOf("M4A") }
    var isSaving by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filteredVoices = remember(voices, languageFilter) {
        when (languageFilter) {
            "English" -> voices.filter { it.languageLabel.startsWith("English") }
            "Hindi" -> voices.filter { it.languageLabel.startsWith("Hindi") }
            else -> voices
        }
    }
    val activeVoice = selectedVoice ?: filteredVoices.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("text_to_voice_screen")
    ) {
        NickyCutTopBar(
            title = "Text to Voice",
            subtitle = "Android TextToSpeech • Hindi & English voices",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            initError?.let { err ->
                item { StatusNoticeBanner(message = err, type = NoticeType.ERROR) }
            }

            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Text("Voiceover Script", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        placeholder = { Text("Enter English or Hindi script...", color = TextSecondary) },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceElevated,
                            unfocusedContainerColor = SurfaceElevated,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tts_script_input")
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Speed: ${String.format("%.2fx", speed)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )
                    Text("Pitch: ${String.format("%.2fx", pitch)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isSpeaking) ttsController.stop()
                                else ttsController.speakPreview(scriptText, activeVoice, speed, pitch)
                            },
                            enabled = isReady && scriptText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tts_preview_button")
                        ) {
                            Icon(if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSpeaking) "Stop" else "Preview Voice", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    statusMessage = null
                                    runCatching {
                                        val wavFile = MediaStorageUtils.createTempFile(context, "tts_raw", "wav")
                                        ttsController.synthesizeToWavFile(scriptText, activeVoice, speed, pitch, wavFile)
                                        val finalFile = if (saveFormat == "M4A") {
                                            val m4a = MediaStorageUtils.createTempFile(context, "tts_voice", "m4a")
                                            AudioEngine.processAndExportAudio(
                                                context = context,
                                                primaryUri = Uri.fromFile(wavFile),
                                                outputFormat = "M4A",
                                                outputFile = m4a
                                            )
                                            wavFile.delete()
                                            m4a
                                        } else {
                                            wavFile
                                        }
                                        val mime = if (saveFormat == "WAV") "audio/wav" else "audio/mp4"
                                        MediaStorageUtils.saveAudioToLibrary(
                                            context = context,
                                            sourceFile = finalFile,
                                            displayTitle = "NickyCut_Voice",
                                            mimeType = mime,
                                            extension = saveFormat.lowercase()
                                        )
                                        finalFile
                                    }.onSuccess { saved ->
                                        exportedFile = saved
                                        statusMessage = "Saved voiceover to Music/NickyCut AI as ${saveFormat}."
                                    }.onFailure { e ->
                                        statusMessage = e.localizedMessage ?: "Failed to synthesize audio file."
                                    }
                                    isSaving = false
                                }
                            },
                            enabled = isReady && !isSaving && scriptText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tts_save_button")
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSaving) "Saving..." else "Save $saveFormat", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        listOf("M4A" to "Save M4A (AAC)", "WAV" to "Save WAV").forEach { (fmt, lbl) ->
                            FilterChip(
                                selected = saveFormat == fmt,
                                onClick = { saveFormat = fmt },
                                label = { Text(lbl) },
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

            statusMessage?.let { msg ->
                item {
                    StatusNoticeBanner(
                        message = msg,
                        type = if (exportedFile != null) NoticeType.SUCCESS else NoticeType.ERROR
                    )
                }
            }

            exportedFile?.let { file ->
                item {
                    OutlinedButton(
                        onClick = {
                            val shareUri = MediaStorageUtils.getShareUriForFile(context, file)
                            val mime = if (saveFormat == "WAV") "audio/wav" else "audio/mp4"
                            MediaStorageUtils.launchShareIntent(context, shareUri, mime, "Share Voiceover")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Generated Voiceover", color = TextPrimary)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Installed Voices (${filteredVoices.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "English", "Hindi").forEach { lang ->
                            FilterChip(
                                selected = languageFilter == lang,
                                onClick = { languageFilter = lang },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceCard,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            items(filteredVoices.take(20), key = { it.id }) { voice ->
                val isSelected = activeVoice?.id == voice.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) NeonEmerald else BorderSubtle,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedVoice = voice },
                    color = SurfaceCard
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${voice.languageLabel} • ${voice.localeTag}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (voice.requiresNetwork) AmberWarning.copy(alpha = 0.16f)
                                    else NeonEmerald.copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (voice.requiresNetwork) "Requires Internet" else "Offline Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (voice.requiresNetwork) AmberWarning else NeonEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}
