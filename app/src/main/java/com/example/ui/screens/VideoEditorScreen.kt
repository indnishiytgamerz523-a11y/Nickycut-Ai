package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.MediaStorageUtils
import com.example.data.model.AspectRatioOption
import com.example.data.model.AudioTrackItem
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.ProjectTimeline
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipItem
import com.example.data.repository.ProjectRepository
import com.example.media.AudioEngine
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
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun VideoEditorScreen(
    projectId: Long,
    prefillJson: String,
    projectRepository: ProjectRepository,
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }

    var currentProjectId by remember { mutableLongStateOf(projectId) }
    var projectName by remember { mutableStateOf("Studio Edit") }
    var timeline by remember { mutableStateOf(ProjectTimeline()) }

    val undoStack = remember { mutableStateListOf<ProjectTimeline>() }
    val redoStack = remember { mutableStateListOf<ProjectTimeline>() }

    var selectedClipIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newOverlayText by remember { mutableStateOf("") }

    fun pushTimeline(next: ProjectTimeline) {
        undoStack.add(timeline)
        if (undoStack.size > 25) undoStack.removeAt(0)
        redoStack.clear()
        timeline = next
    }

    // Load existing project or AI prefill timeline
    LaunchedEffect(projectId, prefillJson) {
        if (projectId > 0L) {
            projectRepository.getProject(projectId)?.let { entity ->
                currentProjectId = entity.id
                projectName = entity.name
                timeline = projectRepository.decodeTimeline(entity.timelineJson)
            }
        } else if (prefillJson.isNotBlank()) {
            runCatching { json.decodeFromString<ProjectTimeline>(prefillJson) }
                .onSuccess { timeline = it }
        }
    }

    // ExoPlayer for real video clip preview
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    val selectedClip = timeline.clips.getOrNull(selectedClipIndex)

    LaunchedEffect(selectedClip?.id, selectedClip?.uriString, selectedClip?.isImage) {
        if (selectedClip != null && !selectedClip.isImage) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(selectedClip.uriString)))
            exoPlayer.prepare()
            exoPlayer.volume = selectedClip.volume
            exoPlayer.seekTo(selectedClip.trimStartMs)
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(isPlaying, selectedClip?.id) {
        while (isPlaying) {
            if (selectedClip != null && !selectedClip.isImage) {
                val pos = exoPlayer.currentPosition
                playheadMs = pos
                if (pos >= selectedClip.trimEndMs) {
                    exoPlayer.seekTo(selectedClip.trimStartMs)
                }
            } else if (selectedClip != null && selectedClip.isImage) {
                delay(100L)
                playheadMs = (playheadMs + 100L) % selectedClip.effectiveDurationMs
            } else {
                isPlaying = false
            }
            delay(100L)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "clip", "mp4")
                    val localUri = Uri.fromFile(copied)
                    val meta = MediaStorageUtils.extractVideoMetadata(context, localUri)
                    VideoClipItem(
                        uriString = localUri.toString(),
                        displayName = meta.displayName,
                        isImage = false,
                        durationMs = meta.durationMs.coerceAtLeast(1000L),
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs.coerceAtLeast(1000L)
                    )
                }.onSuccess { clip ->
                    pushTimeline(timeline.copy(clips = timeline.clips + clip))
                    selectedClipIndex = timeline.clips.lastIndex.coerceAtLeast(0)
                    statusMessage = "Imported video: ${clip.displayName}"
                }.onFailure { e ->
                    statusMessage = e.localizedMessage ?: "Failed to import video."
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "img", "jpg")
                    val localUri = Uri.fromFile(copied)
                    VideoClipItem(
                        uriString = localUri.toString(),
                        displayName = MediaStorageUtils.getDisplayName(context, uri),
                        isImage = true,
                        durationMs = 4000L,
                        trimStartMs = 0L,
                        trimEndMs = 4000L
                    )
                }.onSuccess { clip ->
                    pushTimeline(timeline.copy(clips = timeline.clips + clip))
                    selectedClipIndex = timeline.clips.lastIndex.coerceAtLeast(0)
                    statusMessage = "Added photo clip (${clip.displayName})"
                }.onFailure { e ->
                    statusMessage = e.localizedMessage ?: "Failed to import photo."
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "bgm", "m4a")
                    val localUri = Uri.fromFile(copied)
                    val wf = AudioEngine.extractWaveform(context, localUri, barCount = 20)
                    AudioTrackItem(
                        uriString = localUri.toString(),
                        displayName = MediaStorageUtils.getDisplayName(context, uri),
                        durationMs = wf.durationMs.coerceAtLeast(1000L),
                        trimStartMs = 0L,
                        trimEndMs = wf.durationMs.coerceAtLeast(1000L)
                    )
                }.onSuccess { audio ->
                    pushTimeline(timeline.copy(audioTracks = timeline.audioTracks + audio))
                    statusMessage = "Added audio track: ${audio.displayName}"
                }.onFailure { e ->
                    statusMessage = e.localizedMessage ?: "Failed to read audio file."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("video_editor_screen")
    ) {
        NickyCutTopBar(
            title = projectName,
            subtitle = "${timeline.aspectRatio.label} • ${MediaStorageUtils.formatDuration(timeline.totalDurationMs)}",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            redoStack.add(timeline)
                            timeline = undoStack.removeAt(undoStack.lastIndex)
                        }
                    },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                    )
                }
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            undoStack.add(timeline)
                            timeline = redoStack.removeAt(redoStack.lastIndex)
                        }
                    },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                    )
                }
                IconButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.testTag("editor_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save Project", tint = CyanAccent)
                }
                Button(
                    onClick = {
                        if (timeline.clips.isEmpty()) {
                            statusMessage = "Add at least one video or image clip before exporting."
                        } else {
                            val req = ExportRequest(
                                sourceType = ExportSourceType.TIMELINE,
                                title = projectName,
                                aspectRatio = timeline.aspectRatio,
                                resolution = timeline.resolution,
                                fps = timeline.fps,
                                timeline = timeline
                            )
                            onExport(json.encodeToString(req))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("editor_export_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", fontWeight = FontWeight.Bold)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Video / Image Preview Viewport
            item {
                val previewRatio = when (timeline.aspectRatio) {
                    AspectRatioOption.VERTICAL_9_16 -> 0.78f
                    AspectRatioOption.LANDSCAPE_16_9 -> 16f / 9f
                    AspectRatioOption.SQUARE_1_1 -> 1f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF030705))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedClip == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Import a video or photo to start editing", color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg)
                                ) {
                                    Icon(Icons.Default.VideoCall, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Video")
                                }
                                OutlinedButton(
                                    onClick = {
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = CyanAccent)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Photo", color = TextPrimary)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .aspectRatio(previewRatio)
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedClip.isImage) {
                                AsyncImage(
                                    model = Uri.parse(selectedClip.uriString),
                                    contentDescription = selectedClip.displayName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            player = exoPlayer
                                            layoutParams = FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Active Text Overlays Preview
                            timeline.overlays.lastOrNull()?.let { ov ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(14.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xBB06130E))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = ov.text,
                                        color = Color(ov.colorHex),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Playback & Quick Add Toolbar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                if (selectedClip != null) {
                                    isPlaying = !isPlaying
                                    if (!selectedClip.isImage) {
                                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                                    }
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonEmerald)
                                .testTag("editor_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ObsidianBg
                            )
                        }
                        TimecodePill(text = MediaStorageUtils.formatDuration(playheadMs))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                                .testTag("editor_add_video_button")
                        ) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Add Video", tint = NeonEmerald)
                        }
                        IconButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                                .testTag("editor_add_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Image", tint = CyanAccent)
                        }
                        IconButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                                .testTag("editor_add_audio_button")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = "Add Audio", tint = OrangeAccent)
                        }
                        IconButton(
                            onClick = {
                                newOverlayText = ""
                                showTextDialog = true
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                                .testTag("editor_add_text_button")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = "Add Text", tint = PurpleAccent)
                        }
                    }
                }
            }

            statusMessage?.let { msg ->
                item { StatusNoticeBanner(message = msg, type = NoticeType.INFO) }
            }

            // Multi-track Timeline Panel (V1 Video/Photo, T1 Text, A1 Audio)
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Text("Multi-Track Timeline", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))

                    Text("V1 • Video & Photo Track (${timeline.clips.size})", style = MaterialTheme.typography.labelSmall, color = NeonEmerald)
                    Spacer(Modifier.height(6.dp))
                    if (timeline.clips.isEmpty()) {
                        Text("No media clips on V1 yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(timeline.clips) { idx, clip ->
                                val isSelected = idx == selectedClipIndex
                                Surface(
                                    modifier = Modifier
                                        .width(142.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) NeonEmerald else BorderSubtle,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedClipIndex = idx },
                                    color = SurfaceElevated
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = clip.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${MediaStorageUtils.formatDuration(clip.effectiveDurationMs)} • ${clip.transition.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("T1 • Text Overlays (${timeline.overlays.size})", style = MaterialTheme.typography.labelSmall, color = PurpleAccent)
                    if (timeline.overlays.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            itemsIndexed(timeline.overlays) { idx, ov ->
                                Surface(
                                    color = PurpleAccent.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ov.text, color = TextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove Text",
                                            tint = RoseError,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    pushTimeline(
                                                        timeline.copy(
                                                            overlays = timeline.overlays.toMutableList().also { it.removeAt(idx) }
                                                        )
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("A1 • Audio Track (${timeline.audioTracks.size})", style = MaterialTheme.typography.labelSmall, color = OrangeAccent)
                    if (timeline.audioTracks.isNotEmpty()) {
                        timeline.audioTracks.forEachIndexed { idx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrangeAccent.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track.displayName, color = TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove Audio",
                                    tint = RoseError,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            pushTimeline(
                                                timeline.copy(
                                                    audioTracks = timeline.audioTracks.toMutableList().also { it.removeAt(idx) }
                                                )
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Selected Clip Controls: Trim, Split, Duration, Transition, Volume
            if (selectedClip != null) {
                item {
                    StudioGlowCard(accentColor = CyanAccent) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clip Inspector: ${selectedClip.displayName}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                            Row {
                                IconButton(
                                    onClick = {
                                        // Split clip at midpoint or playhead
                                        val mid = if (!selectedClip.isImage && playheadMs in (selectedClip.trimStartMs + 350L)..(selectedClip.trimEndMs - 350L)) {
                                            playheadMs
                                        } else {
                                            (selectedClip.trimStartMs + selectedClip.trimEndMs) / 2L
                                        }
                                        if (mid - selectedClip.trimStartMs >= 300L && selectedClip.trimEndMs - mid >= 300L) {
                                            val firstHalf = selectedClip.copy(trimEndMs = mid)
                                            val secondHalf = selectedClip.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                trimStartMs = mid
                                            )
                                            val updated = timeline.clips.toMutableList()
                                            updated[selectedClipIndex] = firstHalf
                                            updated.add(selectedClipIndex + 1, secondHalf)
                                            pushTimeline(timeline.copy(clips = updated))
                                            statusMessage = "Split clip at ${MediaStorageUtils.formatDuration(mid)}"
                                        }
                                    },
                                    modifier = Modifier.testTag("editor_split_button")
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Split Clip", tint = NeonEmerald)
                                }
                                IconButton(
                                    onClick = {
                                        val updated = timeline.clips.toMutableList()
                                        updated.removeAt(selectedClipIndex)
                                        pushTimeline(timeline.copy(clips = updated))
                                        selectedClipIndex = selectedClipIndex.coerceAtMost((updated.size - 1).coerceAtLeast(0))
                                    },
                                    modifier = Modifier.testTag("editor_delete_clip_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Clip", tint = RoseError)
                                }
                            }
                        }

                        if (selectedClip.isImage) {
                            Text(
                                "Image Duration: ${MediaStorageUtils.formatDuration(selectedClip.effectiveDurationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Slider(
                                value = selectedClip.trimEndMs.toFloat(),
                                onValueChange = { newDur ->
                                    val updated = timeline.clips.toMutableList()
                                    updated[selectedClipIndex] = selectedClip.copy(trimStartMs = 0L, trimEndMs = newDur.toLong(), durationMs = newDur.toLong())
                                    timeline = timeline.copy(clips = updated)
                                },
                                valueRange = 1000f..15000f,
                                colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                            )
                        } else {
                            Text(
                                "Trim In: ${MediaStorageUtils.formatDuration(selectedClip.trimStartMs)}  |  Trim Out: ${MediaStorageUtils.formatDuration(selectedClip.trimEndMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Slider(
                                value = selectedClip.trimStartMs.toFloat(),
                                onValueChange = { start ->
                                    val safeStart = start.toLong().coerceAtMost(selectedClip.trimEndMs - 300L)
                                    val updated = timeline.clips.toMutableList()
                                    updated[selectedClipIndex] = selectedClip.copy(trimStartMs = safeStart)
                                    timeline = timeline.copy(clips = updated)
                                },
                                valueRange = 0f..selectedClip.durationMs.toFloat().coerceAtLeast(500f),
                                colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                            )
                            Slider(
                                value = selectedClip.trimEndMs.toFloat(),
                                onValueChange = { end ->
                                    val safeEnd = end.toLong().coerceAtLeast(selectedClip.trimStartMs + 300L)
                                    val updated = timeline.clips.toMutableList()
                                    updated[selectedClipIndex] = selectedClip.copy(trimEndMs = safeEnd)
                                    timeline = timeline.copy(clips = updated)
                                },
                                valueRange = 0f..selectedClip.durationMs.toFloat().coerceAtLeast(500f),
                                colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                            )
                        }

                        Text("Clip Volume: ${(selectedClip.volume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Slider(
                            value = selectedClip.volume,
                            onValueChange = { vol ->
                                val updated = timeline.clips.toMutableList()
                                updated[selectedClipIndex] = selectedClip.copy(volume = vol)
                                timeline = timeline.copy(clips = updated)
                                exoPlayer.volume = vol
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = OrangeAccent)
                        )

                        Text("Transition", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            itemsIndexed(TransitionType.entries) { _, tr ->
                                FilterChip(
                                    selected = selectedClip.transition == tr,
                                    onClick = {
                                        val updated = timeline.clips.toMutableList()
                                        updated[selectedClipIndex] = selectedClip.copy(transition = tr)
                                        pushTimeline(timeline.copy(clips = updated))
                                    },
                                    label = { Text(tr.label) },
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
            }

            // Canvas Aspect Ratio selector
            item {
                StudioGlowCard(accentColor = BorderSubtle) {
                    Text("Canvas Format", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        AspectRatioOption.entries.forEach { ratio ->
                            FilterChip(
                                selected = timeline.aspectRatio == ratio,
                                onClick = { pushTimeline(timeline.copy(aspectRatio = ratio)) },
                                label = { Text(ratio.label.substringBefore(" ")) },
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
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Add Text Overlay", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newOverlayText,
                    onValueChange = { newOverlayText = it },
                    label = { Text("Overlay Caption") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newOverlayText.isNotBlank()) {
                            pushTimeline(
                                timeline.copy(
                                    overlays = timeline.overlays + TextOverlayItem(text = newOverlayText.trim())
                                )
                            )
                        }
                        showTextDialog = false
                    }
                ) { Text("Add", color = NeonEmerald) }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Save Project Locally", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            currentProjectId = projectRepository.saveTimelineProject(
                                existingId = currentProjectId.takeIf { it > 0L },
                                name = projectName,
                                timeline = timeline
                            )
                            statusMessage = "Project \"$projectName\" saved locally."
                        }
                        showSaveDialog = false
                    }
                ) { Text("Save", color = NeonEmerald) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}
