package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.MediaStorageUtils
import com.example.media.BackgroundRemovalEngine
import com.example.media.BackgroundReplacement
import com.example.media.SegmentationResult
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.theme.BorderSubtle
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

@Composable
fun BackgroundRemoverScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Image, 1 = Video (Capability Info)
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var segResult by remember { mutableStateOf<SegmentationResult?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var threshold by remember { mutableFloatStateOf(0.5f) }
    var replacement by remember { mutableStateOf<BackgroundReplacement>(BackgroundReplacement.Transparent) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                statusMessage = null
                runCatching {
                    val bmp = MediaStorageUtils.decodeBitmapFromUri(context, uri, maxDim = 1080)
                    val result = BackgroundRemovalEngine.extractRawMask(bmp)
                    bmp to result
                }.onSuccess { (bmp, result) ->
                    sourceBitmap = bmp
                    segResult = result
                    previewBitmap = result.foregroundOnly
                    replacement = BackgroundReplacement.Transparent
                    statusMessage = "Background segmented offline using ML Kit Selfie Segmentation."
                }.onFailure { e ->
                    statusMessage = e.localizedMessage ?: "Failed to segment image."
                }
                isProcessing = false
            }
        }
    }

    val backdropPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    MediaStorageUtils.decodeBitmapFromUri(context, uri, maxDim = 1080)
                }.onSuccess { bgBmp ->
                    replacement = BackgroundReplacement.ImageBackdrop(bgBmp)
                }
            }
        }
    }

    // Re-composite whenever threshold or replacement changes
    LaunchedEffect(sourceBitmap, segResult, threshold, replacement) {
        val src = sourceBitmap ?: return@LaunchedEffect
        val seg = segResult ?: return@LaunchedEffect
        previewBitmap = BackgroundRemovalEngine.compositeWithMask(
            sourceBitmap = src,
            maskArray = seg.maskBuffer,
            maskWidth = seg.maskWidth,
            maskHeight = seg.maskHeight,
            threshold = threshold,
            replacement = replacement
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("background_remover_screen")
    ) {
        NickyCutTopBar(
            title = "Background Remover",
            subtitle = "On-device ML cutout & transparent PNG export",
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
                text = { Text("Image Cutout (Offline)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Video Matting Status") }
            )
        }

        if (selectedTab == 1) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StatusNoticeBanner(
                        message = "Offline per-frame video background removal is NOT enabled in this version.",
                        type = NoticeType.WARNING
                    )
                }
                item {
                    StudioGlowCard(accentColor = PurpleAccent) {
                        Text("Why Video Removal Is Disabled", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Accurate video matting requires GPU-accelerated frame segmentation (such as MediaPipe Selfie/Hair Segmentation or green-screen chroma shaders) per video frame.\n\nTo avoid slow CPU stalls or fake simulated results, NickyCut AI provides full offline ML Kit cutout for photos/thumbnails and will never pretend video background removal succeeded when no dedicated video matting engine is connected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Transparency checkerboard
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cell = 24f
                            val cols = (size.width / cell).toInt() + 1
                            val rows = (size.height / cell).toInt() + 1
                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    val light = (r + c) % 2 == 0
                                    drawRect(
                                        color = if (light) Color(0xFF1A2822) else Color(0xFF0E1713),
                                        topLeft = Offset(c * cell, r * cell),
                                        size = Size(cell, cell)
                                    )
                                }
                            }
                        }

                        if (isProcessing) {
                            CircularProgressIndicator(color = NeonEmerald)
                        } else if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Cutout Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    "Import a portrait or subject photo to remove its background offline.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = {
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                                    modifier = Modifier.testTag("bg_pick_image_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Select Image", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (previewBitmap != null) {
                    item {
                        StudioGlowCard(accentColor = PurpleAccent) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cutout Tuning & Backdrop", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                OutlinedButton(
                                    onClick = {
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                ) {
                                    Text("New Photo", color = TextPrimary)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text("Mask Edge Threshold: ${(threshold * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Slider(
                                value = threshold,
                                onValueChange = { threshold = it },
                                valueRange = 0.15f..0.85f,
                                colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                FilterChip(
                                    selected = replacement is BackgroundReplacement.Transparent,
                                    onClick = { replacement = BackgroundReplacement.Transparent },
                                    label = { Text("Transparent") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldDim,
                                        selectedLabelColor = NeonEmerald,
                                        containerColor = SurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                                FilterChip(
                                    selected = replacement is BackgroundReplacement.ImageBackdrop,
                                    onClick = {
                                        val studioBg = BitmapFactory.decodeResource(context.resources, R.drawable.img_neon_backdrop)
                                        if (studioBg != null) replacement = BackgroundReplacement.ImageBackdrop(studioBg)
                                    },
                                    label = { Text("Neon Studio") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldDim,
                                        selectedLabelColor = NeonEmerald,
                                        containerColor = SurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                                OutlinedButton(
                                    onClick = {
                                        backdropPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                ) {
                                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Custom BG", color = TextPrimary)
                                }
                            }

                            Text("Solid Color Backdrop", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                listOf(0xFF06140E, 0xFF10F59A, 0xFFFFFFFF, 0xFF00FF00, 0xFFA855F7, 0xFF22D3EE).forEach { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                            .border(1.dp, BorderSubtle, CircleShape)
                                            .clickable {
                                                replacement = BackgroundReplacement.SolidColor(hex.toInt())
                                            }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val outBmp = previewBitmap ?: return@Button
                                    scope.launch {
                                        runCatching {
                                            MediaStorageUtils.saveImageToGallery(context, outBmp, "NickyCut_Cutout")
                                        }.onSuccess {
                                            statusMessage = "Saved PNG to Pictures/NickyCut AI."
                                        }.onFailure { e ->
                                            statusMessage = e.localizedMessage ?: "Failed to save PNG."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("bg_save_png_button")
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Transparent / Composited PNG", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                statusMessage?.let {
                    item {
                        StatusNoticeBanner(
                            message = it,
                            type = if (it.contains("Saved") || it.contains("segmented")) NoticeType.SUCCESS else NoticeType.ERROR
                        )
                    }
                }
            }
        }
    }
}
