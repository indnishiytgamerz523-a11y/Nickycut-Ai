package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.MediaStorageUtils
import com.example.data.model.AspectRatioOption
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.ResolutionOption
import com.example.media.VideoCanvasRenderer
import com.example.media.VideoTransformerEngine
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
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

@Composable
fun ExportScreen(
    exportPayloadJson: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val request = remember(exportPayloadJson) {
        runCatching { json.decodeFromString<ExportRequest>(exportPayloadJson) }
            .getOrElse { ExportRequest(ExportSourceType.KINETIC, "NickyCut Export") }
    }

    var resolution by remember { mutableStateOf(request.resolution) }
    var aspectRatio by remember { mutableStateOf(request.aspectRatio) }
    val fps = 30

    var isRendering by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var savedGalleryUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var renderJob by remember { mutableStateOf<Job?>(null) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            renderJob?.cancel()
            exoPlayer.release()
        }
    }

    val (outW, outH) = aspectRatio.dimensionsFor(resolution)
    val availableBytes = remember { MediaStorageUtils.getAvailableBytes(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("export_screen")
    ) {
        NickyCutTopBar(
            title = "Export Video",
            subtitle = "${request.title} • ${outW}x$outH @ $fps FPS",
            onBack = {
                renderJob?.cancel()
                onBack()
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Text("Resolution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ResolutionOption.entries.forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { if (!isRendering) resolution = res },
                                label = { Text(res.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Text(
                        "Aspect Ratio",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        AspectRatioOption.entries.forEach { ar ->
                            FilterChip(
                                selected = aspectRatio == ar,
                                onClick = { if (!isRendering) aspectRatio = ar },
                                label = { Text(ar.label.substringBefore(" ")) },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Frame Rate: $fps FPS (H.264 / AAC)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        TimecodePill(
                            text = "Free: ${MediaStorageUtils.formatBytes(availableBytes)}",
                            accent = CyanAccent
                        )
                    }
                }
            }

            item {
                if (isRendering) {
                    StudioGlowCard(accentColor = CyanAccent) {
                        Text("Rendering MP4 Locally...", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = NeonEmerald,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = NeonEmerald)
                            OutlinedButton(
                                onClick = {
                                    renderJob?.cancel()
                                    isRendering = false
                                    errorMessage = "Export cancelled."
                                }
                            ) {
                                Text("Cancel", color = TextPrimary)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (!MediaStorageUtils.hasEnoughStorageForExport(context)) {
                                errorMessage = "Insufficient device storage (under 50 MB free). Free up space before exporting."
                                return@Button
                            }
                            errorMessage = null
                            exportedFile = null
                            savedGalleryUri = null
                            isRendering = true
                            progress = 0.02f

                            renderJob = scope.launch {
                                try {
                                    val tempOut = MediaStorageUtils.createTempFile(context, "nickycut_export", "mp4")
                                    val resultFile = when (request.sourceType) {
                                        ExportSourceType.TIMELINE -> VideoTransformerEngine.exportTimelineWithTransformer(
                                            context = context,
                                            timeline = request.timeline ?: error("Missing timeline"),
                                            resolution = resolution,
                                            aspectRatio = aspectRatio,
                                            fps = fps,
                                            outputFile = tempOut,
                                            onProgress = { progress = it }
                                        )
                                        ExportSourceType.KINETIC -> VideoCanvasRenderer.renderKineticVideo(
                                            context = context,
                                            config = request.kinetic ?: error("Missing kinetic config"),
                                            width = outW,
                                            height = outH,
                                            fps = fps,
                                            outputFile = tempOut,
                                            onProgress = { progress = it }
                                        )
                                        ExportSourceType.CAPTIONS -> VideoCanvasRenderer.renderCaptionsVideo(
                                            context = context,
                                            config = request.captions ?: error("Missing captions config"),
                                            width = outW,
                                            height = outH,
                                            fps = fps,
                                            outputFile = tempOut,
                                            onProgress = { progress = it }
                                        )
                                        ExportSourceType.SCRIPT_TO_VIDEO -> VideoCanvasRenderer.renderScriptToVideo(
                                            context = context,
                                            config = request.scriptToVideo ?: error("Missing script-to-video config"),
                                            width = outW,
                                            height = outH,
                                            fps = fps,
                                            outputFile = tempOut,
                                            onProgress = { progress = it }
                                        )
                                        ExportSourceType.VIRAL_EFFECT -> VideoCanvasRenderer.renderViralEffectVideo(
                                            context = context,
                                            config = request.viralEffect ?: error("Missing viral effect config"),
                                            width = outW,
                                            height = outH,
                                            fps = fps,
                                            outputFile = tempOut,
                                            onProgress = { progress = it }
                                        )
                                    }
                                    exportedFile = resultFile
                                    savedGalleryUri = MediaStorageUtils.saveVideoToGallery(context, resultFile, request.title)
                                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(resultFile)))
                                    exoPlayer.prepare()
                                    isRendering = false
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (e: Exception) {
                                    isRendering = false
                                    errorMessage = e.localizedMessage ?: "Video export failed."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Render & Save to Movies/NickyCut AI", fontWeight = FontWeight.Bold)
                    }
                }
            }

            errorMessage?.let { err ->
                item { StatusNoticeBanner(message = err, type = NoticeType.ERROR) }
            }

            exportedFile?.let { file ->
                item {
                    StudioGlowCard(accentColor = NeonEmerald) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald)
                            Column {
                                Text("Export Complete!", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    "Saved to Gallery (${MediaStorageUtils.formatBytes(file.length())})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
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

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val shareUri = MediaStorageUtils.getShareUriForFile(context, file)
                                MediaStorageUtils.launchShareIntent(context, shareUri, "video/mp4", "Share Video")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_exported_video_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Share Exported Video", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
