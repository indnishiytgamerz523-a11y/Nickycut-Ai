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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.core.MediaStorageUtils
import com.example.data.model.AiCreatorPlanResult
import com.example.data.model.AiEditSuggestion
import com.example.data.model.ProjectTimeline
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipItem
import com.example.data.repository.GeminiCreatorRepository
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
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun AiVideoEditorScreen(
    geminiRepository: GeminiCreatorRepository,
    onBack: () -> Unit,
    onApplyTimelineToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = AI Video Editor, 1 = AI Creator Assistant

    // Tab 0 state
    var localVideoUri by remember { mutableStateOf<String?>(null) }
    var videoName by remember { mutableStateOf("No video imported") }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoDimensions by remember { mutableStateOf("") }
    var creatorGoal by remember { mutableStateOf("Make a fast-paced vertical Reel with punchy captions") }
    var analysisSummary by remember { mutableStateOf<String?>(null) }
    val suggestions = remember { mutableStateListOf<AiEditSuggestion>() }
    var isAnalyzing by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }

    // Tab 1 state (AI Creator Assistant)
    var ideaInput by remember { mutableStateOf("") }
    var targetPlatform by remember { mutableStateOf("YouTube Shorts") }
    var assistantLang by remember { mutableStateOf("English") }
    var creatorPlan by remember { mutableStateOf<AiCreatorPlanResult?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                editorError = null
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "ai_vid", "mp4")
                    val localUri = Uri.fromFile(copied)
                    val meta = MediaStorageUtils.extractVideoMetadata(context, localUri)
                    localUri to meta
                }.onSuccess { (localUri, meta) ->
                    localVideoUri = localUri.toString()
                    videoName = meta.displayName
                    videoDurationMs = meta.durationMs
                    videoDimensions = "${meta.width}x${meta.height}"
                    suggestions.clear()
                    analysisSummary = null
                }.onFailure { e ->
                    editorError = e.localizedMessage ?: "Failed to load video."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("ai_video_editor_screen")
    ) {
        NickyCutTopBar(
            title = "AI Video & Assistant",
            subtitle = "Gemini cut approval & Reels/Shorts strategist",
            onBack = onBack
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceCard,
            contentColor = NeonEmerald
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("AI Video Editor") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Creator Assistant") }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StatusNoticeBanner(
                        message = "Original video is never modified. Review and approve each Gemini suggestion before applying to the editor.",
                        type = NoticeType.INFO
                    )
                }

                item {
                    StudioGlowCard(accentColor = CyanAccent) {
                        Text(videoName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (videoDurationMs > 0L) {
                            Text(
                                "${MediaStorageUtils.formatDuration(videoDurationMs)} • $videoDimensions",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanAccent
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = creatorGoal,
                            onValueChange = { creatorGoal = it },
                            label = { Text("Editing Goal") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(4.dp))
                                Text("Import Video", color = TextPrimary)
                            }
                            Button(
                                onClick = {
                                    val vUri = localVideoUri
                                    if (vUri == null) {
                                        editorError = "Import a video first."
                                        return@Button
                                    }
                                    if (!MediaStorageUtils.isNetworkAvailable(context)) {
                                        editorError = "Internet connection required for Gemini keyframe analysis."
                                        return@Button
                                    }
                                    scope.launch {
                                        isAnalyzing = true
                                        editorError = null
                                        runCatching {
                                            val frames = MediaStorageUtils.extractVideoKeyframes(context, Uri.parse(vUri), frameCount = 4, maxDimension = 480)
                                            geminiRepository.analyzeVideoForEdits(
                                                keyframes = frames,
                                                durationMs = videoDurationMs,
                                                aspectInfo = videoDimensions,
                                                creatorGoal = creatorGoal
                                            )
                                        }.onSuccess { res ->
                                            analysisSummary = res.summary
                                            suggestions.clear()
                                            suggestions.addAll(res.suggestions)
                                        }.onFailure { e ->
                                            editorError = e.localizedMessage ?: "AI video analysis failed."
                                        }
                                        isAnalyzing = false
                                    }
                                },
                                enabled = !isAnalyzing && localVideoUri != null,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_analyze_video_button")
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(color = ObsidianBg, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Analyze", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                editorError?.let { err ->
                    item { StatusNoticeBanner(message = err, type = NoticeType.ERROR) }
                }

                analysisSummary?.let { summary ->
                    item {
                        StudioGlowCard(accentColor = NeonEmerald) {
                            Text("AI Assessment", style = MaterialTheme.typography.titleMedium, color = NeonEmerald)
                            Spacer(Modifier.height(6.dp))
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    itemsIndexed(suggestions, key = { _, s -> s.id }) { idx, item ->
                        StudioGlowCard(
                            accentColor = if (item.approved) NeonEmerald else PurpleAccent,
                            onClick = { suggestions[idx] = item.copy(approved = !item.approved) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = item.approved,
                                        onCheckedChange = { suggestions[idx] = item.copy(approved = it) },
                                        colors = CheckboxDefaults.colors(checkedColor = NeonEmerald, checkmarkColor = ObsidianBg)
                                    )
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        TimecodePill(
                                            "${item.category} • ${MediaStorageUtils.formatDuration(item.startMs)}–${MediaStorageUtils.formatDuration(item.endMs)}",
                                            CyanAccent
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }

                    item {
                        val approvedCount = suggestions.count { it.approved }
                        Button(
                            onClick = {
                                val vUri = localVideoUri ?: return@Button
                                val approved = suggestions.filter { it.approved }
                                val cut = approved.firstOrNull { it.category == "Cut" }
                                val transition = if (approved.any { it.category == "Transition" }) TransitionType.ZOOM_IN else TransitionType.NONE
                                val overlays = approved.filter { it.category == "Caption" }.map {
                                    TextOverlayItem(
                                        text = it.title,
                                        startMs = it.startMs,
                                        endMs = it.endMs.coerceAtLeast(it.startMs + 1200L)
                                    )
                                }
                                val clip = VideoClipItem(
                                    uriString = vUri,
                                    displayName = videoName,
                                    durationMs = videoDurationMs,
                                    trimStartMs = cut?.startMs ?: 0L,
                                    trimEndMs = cut?.endMs?.takeIf { it > (cut.startMs + 500L) } ?: videoDurationMs,
                                    transition = transition
                                )
                                val timeline = ProjectTimeline(clips = listOf(clip), overlays = overlays)
                                onApplyTimelineToEditor(json.encodeToString(timeline))
                            },
                            enabled = approvedCount > 0 && localVideoUri != null,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("apply_approved_edits_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply $approvedCount Approved Edits in Video Editor", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // AI Creator Assistant Tab
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StudioGlowCard(accentColor = PurpleAccent) {
                        Text("Reels & YouTube Shorts Planner", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ideaInput,
                            onValueChange = { ideaInput = it },
                            label = { Text("Describe your video or paste transcript") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("creator_assistant_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("YouTube Shorts", "Instagram Reels").forEach { p ->
                                FilterChip(
                                    selected = targetPlatform == p,
                                    onClick = { targetPlatform = p },
                                    label = { Text(p) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldDim,
                                        selectedLabelColor = NeonEmerald,
                                        containerColor = SurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            listOf("English", "Hindi", "Hinglish").forEach { l ->
                                FilterChip(
                                    selected = assistantLang == l,
                                    onClick = { assistantLang = l },
                                    label = { Text(l) },
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
                                if (ideaInput.isBlank()) {
                                    planError = "Enter your video concept or transcript first."
                                    return@Button
                                }
                                if (!MediaStorageUtils.isNetworkAvailable(context)) {
                                    planError = "Internet connection required for AI Creator Assistant."
                                    return@Button
                                }
                                scope.launch {
                                    isPlanning = true
                                    planError = null
                                    copiedNotice = null
                                    runCatching {
                                        geminiRepository.generateCreatorPlan(ideaInput.trim(), targetPlatform, assistantLang)
                                    }.onSuccess { plan ->
                                        creatorPlan = plan
                                    }.onFailure { e ->
                                        planError = e.localizedMessage ?: "Creator planning failed."
                                    }
                                    isPlanning = false
                                }
                            },
                            enabled = !isPlanning,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("generate_creator_plan_button")
                        ) {
                            if (isPlanning) {
                                CircularProgressIndicator(color = ObsidianBg, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Titles, Tags & Tips", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                planError?.let { item { StatusNoticeBanner(message = it, type = NoticeType.ERROR) } }
                copiedNotice?.let { item { StatusNoticeBanner(message = it, type = NoticeType.SUCCESS) } }

                creatorPlan?.let { plan ->
                    item {
                        StudioGlowCard(accentColor = NeonEmerald) {
                            Text("Title Ideas", style = MaterialTheme.typography.titleMedium, color = NeonEmerald)
                            plan.titles.forEachIndexed { idx, t ->
                                Text("${idx + 1}. $t", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                            Spacer(Modifier.height(10.dp))

                            Text("Description", style = MaterialTheme.typography.titleMedium, color = CyanAccent)
                            Text(plan.description, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Spacer(Modifier.height(10.dp))

                            Text("Hashtags", style = MaterialTheme.typography.titleMedium, color = PurpleAccent)
                            Text(plan.hashtags.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(10.dp))

                            Text("Retention Improvements", style = MaterialTheme.typography.titleMedium, color = NeonEmerald)
                            plan.improvements.forEach { tip ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.padding(top = 2.dp))
                                    Text(tip, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val text = "TITLES:\n${plan.titles.joinToString("\n")}\n\nDESCRIPTION:\n${plan.description}\n\nHASHTAGS:\n${plan.hashtags.joinToString(" ")}"
                                    clipboard.setText(AnnotatedString(text))
                                    copiedNotice = "Copied titles, description & hashtags."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Metadata Pack", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
