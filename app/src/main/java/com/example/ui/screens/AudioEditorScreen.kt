package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.MediaStorageUtils
import com.example.media.AudioEngine
import com.example.media.AudioMergeMode
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioEditorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var primaryFile by remember { mutableStateOf<File?>(null) }
    var primaryName by remember { mutableStateOf("No audio imported") }
    var durationMs by remember { mutableLongStateOf(0L) }
    var waveform by remember { mutableStateOf<List<Float>>(emptyList()) }

    var secondaryFile by remember { mutableStateOf<File?>(null) }
    var secondaryName by remember { mutableStateOf<String?>(null) }
    var mergeMode by remember { mutableStateOf(AudioMergeMode.NONE) }

    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var fadeInMs by remember { mutableFloatStateOf(0f) }
    var fadeOutMs by remember { mutableFloatStateOf(0f) }
    var outputFormat by remember { mutableStateOf("M4A") }

    var isBusy by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    val primaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                statusMsg = null
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "aud_src", "m4a")
                    val wf = AudioEngine.extractWaveform(context, Uri.fromFile(copied), barCount = 64)
                    Triple(copied, MediaStorageUtils.getDisplayName(context, uri), wf)
                }.onSuccess { (file, name, wf) ->
                    primaryFile = file
                    primaryName = name
                    waveform = wf.peaks
                    durationMs = wf.durationMs.coerceAtLeast(500L)
                    trimStartMs = 0L
                    trimEndMs = durationMs
                    exportedFile = null
                }.onFailure { e ->
                    statusMsg = e.localizedMessage ?: "Unable to decode audio."
                }
                isBusy = false
            }
        }
    }

    val secondaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "aud_merge", "m4a")
                    copied to MediaStorageUtils.getDisplayName(context, uri)
                }.onSuccess { (file, name) ->
                    secondaryFile = file
                    secondaryName = name
                    if (mergeMode == AudioMergeMode.NONE) mergeMode = AudioMergeMode.APPEND
                }.onFailure { e ->
                    statusMsg = e.localizedMessage ?: "Failed to load second audio file."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("audio_editor_screen")
    ) {
        NickyCutTopBar(
            title = "Audio Editor",
            subtitle = "Offline PCM waveform, trim, fade, merge & convert",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(primaryName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                if (durationMs > 0L) "Duration: ${MediaStorageUtils.formatDuration(durationMs)}"
                                else "Supports MP3, AAC, M4A, WAV, OGG",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { primaryPicker.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("audio_import_button")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isBusy) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = NeonEmerald)
                        }
                    } else if (waveform.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        val startFrac = (trimStartMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val endFrac = (trimEndMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .background(SurfaceElevated, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            val barW = size.width / waveform.size
                            waveform.forEachIndexed { i, amp ->
                                val frac = i.toFloat() / waveform.size
                                val active = frac in startFrac..endFrac
                                val h = (amp * size.height).coerceAtLeast(6f)
                                drawRoundRect(
                                    color = if (active) NeonEmerald else TextMuted.copy(alpha = 0.4f),
                                    topLeft = Offset(i * barW + 1f, (size.height - h) / 2f),
                                    size = Size((barW - 2f).coerceAtLeast(2f), h),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TimecodePill("In: ${MediaStorageUtils.formatDuration(trimStartMs)}", NeonEmerald)
                            TimecodePill("Out: ${MediaStorageUtils.formatDuration(trimEndMs)}", CyanAccent)
                        }

                        Slider(
                            value = trimStartMs.toFloat(),
                            onValueChange = { trimStartMs = it.toLong().coerceAtMost(trimEndMs - 200L) },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(500f),
                            colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                        )
                        Slider(
                            value = trimEndMs.toFloat(),
                            onValueChange = { trimEndMs = it.toLong().coerceAtLeast(trimStartMs + 200L) },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(500f),
                            colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                        )
                    }
                }
            }

            if (primaryFile != null) {
                item {
                    StudioGlowCard(accentColor = CyanAccent) {
                        Text("Gain & Fade Controls", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            valueRange = 0.1f..2f,
                            colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                        )
                        Text("Fade In: ${String.format("%.1f", fadeInMs / 1000f)}s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Slider(
                            value = fadeInMs,
                            onValueChange = { fadeInMs = it },
                            valueRange = 0f..5000f,
                            colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                        )
                        Text("Fade Out: ${String.format("%.1f", fadeOutMs / 1000f)}s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Slider(
                            value = fadeOutMs,
                            onValueChange = { fadeOutMs = it },
                            valueRange = 0f..5000f,
                            colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = OrangeAccent)
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val src = exportedFile ?: primaryFile ?: return@OutlinedButton
                                    if (isPlaying) {
                                        runCatching { mediaPlayer.stop() }
                                        isPlaying = false
                                    } else {
                                        runCatching {
                                            mediaPlayer.reset()
                                            mediaPlayer.setDataSource(src.absolutePath)
                                            mediaPlayer.prepare()
                                            mediaPlayer.setOnCompletionListener { isPlaying = false }
                                            mediaPlayer.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = NeonEmerald)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPlaying) "Stop" else "Preview", color = TextPrimary)
                            }
                        }
                    }
                }

                item {
                    StudioGlowCard(accentColor = OrangeAccent) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Merge Second Audio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(secondaryName ?: "Optional: append or layer another track", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            OutlinedButton(onClick = { secondaryPicker.launch(arrayOf("audio/*")) }) {
                                Icon(Icons.Default.MergeType, contentDescription = null, tint = OrangeAccent)
                                Spacer(Modifier.width(4.dp))
                                Text("Pick 2nd", color = TextPrimary)
                            }
                        }
                        if (secondaryFile != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                listOf(
                                    AudioMergeMode.APPEND to "Append End",
                                    AudioMergeMode.MIX_OVERLAY to "Mix Layer",
                                    AudioMergeMode.NONE to "Ignore"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = mergeMode == mode,
                                        onClick = { mergeMode = mode },
                                        label = { Text(label) },
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

                item {
                    StudioGlowCard(accentColor = NeonEmerald) {
                        Text("Convert & Export Format", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            listOf("M4A" to "M4A (AAC)", "WAV" to "WAV (PCM)").forEach { (fmt, label) ->
                                FilterChip(
                                    selected = outputFormat == fmt,
                                    onClick = { outputFormat = fmt },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldDim,
                                        selectedLabelColor = NeonEmerald,
                                        containerColor = SurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val src = primaryFile ?: return@Button
                                scope.launch {
                                    isBusy = true
                                    statusMsg = null
                                    runCatching {
                                        val ext = outputFormat.lowercase()
                                        val outFile = MediaStorageUtils.createTempFile(context, "audio_edit", ext)
                                        AudioEngine.processAndExportAudio(
                                            context = context,
                                            primaryUri = Uri.fromFile(src),
                                            secondaryUri = secondaryFile?.let { Uri.fromFile(it) },
                                            mergeMode = mergeMode,
                                            trimStartMs = trimStartMs,
                                            trimEndMs = trimEndMs,
                                            volume = volume,
                                            fadeInMs = fadeInMs.toLong(),
                                            fadeOutMs = fadeOutMs.toLong(),
                                            outputFormat = outputFormat,
                                            outputFile = outFile
                                        )
                                        val mime = if (ext == "wav") "audio/wav" else "audio/mp4"
                                        MediaStorageUtils.saveAudioToLibrary(context, outFile, "NickyCut_Audio", mime, ext)
                                        outFile
                                    }.onSuccess { out ->
                                        exportedFile = out
                                        statusMsg = "Saved ${outputFormat} audio to Music/NickyCut AI (${MediaStorageUtils.formatBytes(out.length())})."
                                    }.onFailure { e ->
                                        statusMsg = e.localizedMessage ?: "Audio export failed."
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("audio_export_button")
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Render & Save $outputFormat", fontWeight = FontWeight.Bold)
                        }

                        exportedFile?.let { out ->
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val shareUri = MediaStorageUtils.getShareUriForFile(context, out)
                                    val mime = if (outputFormat == "WAV") "audio/wav" else "audio/mp4"
                                    MediaStorageUtils.launchShareIntent(context, shareUri, mime, "Share Audio")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(8.dp))
                                Text("Share Exported Audio", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            statusMsg?.let {
                item {
                    StatusNoticeBanner(
                        message = it,
                        type = if (exportedFile != null) NoticeType.SUCCESS else NoticeType.INFO
                    )
                }
            }
        }
    }
}
