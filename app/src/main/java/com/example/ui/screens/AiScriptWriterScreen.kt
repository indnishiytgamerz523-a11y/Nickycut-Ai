package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaStorageUtils
import com.example.data.model.AiScriptResult
import com.example.data.repository.GeminiCreatorRepository
import com.example.data.repository.ProjectRepository
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScriptWriterScreen(
    geminiRepository: GeminiCreatorRepository,
    projectRepository: ProjectRepository,
    onBack: () -> Unit,
    onOpenScriptToVideo: (String) -> Unit,
    onOpenTextToVoice: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val savedScripts by projectRepository.allSavedScripts.collectAsStateWithLifecycle(initialValue = emptyList())

    val categories = remember {
        listOf("Facts", "Business", "Education", "Technology", "Motivation", "Entertainment")
    }
    val languages = remember { listOf("English", "Hindi", "Hinglish") }
    val durations = remember { listOf(15, 30, 60) }

    var topic by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Technology") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedDuration by remember { mutableIntStateOf(30) }

    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiScriptResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("ai_script_writer_screen")
    ) {
        NickyCutTopBar(
            title = "AI Script Writer",
            subtitle = "Gemini 3.8 Flash • English, Hindi & Hinglish",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusNoticeBanner(
                    message = "Requires internet & Firebase AI Logic quota. Generated scripts are saved locally.",
                    type = NoticeType.INFO
                )
            }

            item {
                StudioGlowCard(accentColor = PurpleAccent) {
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Video Topic or Idea") },
                        placeholder = { Text("e.g., 3 AI tools saving creators 10 hours a week", color = TextSecondary) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_script_topic_input")
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Language", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = selectedLanguage == lang,
                                onClick = { selectedLanguage = lang },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Category", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Target Duration", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        durations.forEach { sec ->
                            FilterChip(
                                selected = selectedDuration == sec,
                                onClick = { selectedDuration = sec },
                                label = { Text("${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (topic.isBlank()) {
                                errorMessage = "Enter a topic first."
                                return@Button
                            }
                            if (!MediaStorageUtils.isNetworkAvailable(context)) {
                                errorMessage = "No internet connection detected. Gemini AI Script Writer requires online access."
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                statusMessage = null
                                runCatching {
                                    geminiRepository.generateScript(
                                        topic = topic.trim(),
                                        category = selectedCategory,
                                        language = selectedLanguage,
                                        durationSeconds = selectedDuration
                                    )
                                }.onSuccess { res ->
                                    result = res
                                }.onFailure { e ->
                                    errorMessage = e.localizedMessage ?: "Failed to generate script."
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("ai_generate_script_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = ObsidianBg, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generating...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Script", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                item { StatusNoticeBanner(message = err, type = NoticeType.ERROR) }
            }
            statusMessage?.let { msg ->
                item { StatusNoticeBanner(message = msg, type = NoticeType.SUCCESS) }
            }

            result?.let { script ->
                item {
                    StudioGlowCard(accentColor = NeonEmerald) {
                        Text(script.title, style = MaterialTheme.typography.titleLarge, color = NeonEmerald)
                        Spacer(Modifier.height(10.dp))

                        Text("HOOK (0–2s)", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                        Text(script.hook, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))

                        Text("NARRATION", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                        Text(script.narration, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))

                        Text("SCENE DESCRIPTIONS", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                        script.sceneDescriptions.forEachIndexed { idx, desc ->
                            Text("${idx + 1}. $desc", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Spacer(Modifier.height(10.dp))

                        Text("CTA", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                        Text(script.cta, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val full = "${script.title}\n\nHOOK:\n${script.hook}\n\nNARRATION:\n${script.narration}\n\nCTA:\n${script.cta}"
                                    clipboard.setText(AnnotatedString(full))
                                    statusMessage = "Copied full script to clipboard."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        projectRepository.saveScript(selectedLanguage, selectedCategory, script)
                                        statusMessage = "Saved script locally."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = NeonEmerald)
                                Spacer(Modifier.width(4.dp))
                                Text("Save", color = TextPrimary)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenScriptToVideo("${script.hook}\n${script.narration}\n${script.cta}") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianBg),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.MovieCreation, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("To Video", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onOpenTextToVoice("${script.hook}. ${script.narration} ${script.cta}") },
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = TextPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("To Voice", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (savedScripts.isNotEmpty()) {
                item {
                    Text("Saved Scripts (${savedScripts.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                items(savedScripts, key = { it.id }) { saved ->
                    StudioGlowCard(accentColor = CyanAccent) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(saved.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                TimecodePill("${saved.language} • ${saved.category}", PurpleAccent)
                            }
                            IconButton(onClick = { scope.launch { projectRepository.deleteScript(saved.id) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Script", tint = RoseError)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(saved.narration, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 3)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onOpenScriptToVideo("${saved.hook}\n${saved.narration}\n${saved.cta}") }
                        ) {
                            Text("Send to Script to Video", color = NeonEmerald)
                        }
                    }
                }
            }
        }
    }
}
