package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.MediaStorageUtils
import com.example.data.model.ExportRequest
import com.example.data.model.ExportSourceType
import com.example.data.model.ViralEffectConfig
import com.example.data.model.ViralEffectType
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.StudioGlowCard
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ViralEffectsScreen(
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }

    var config by remember { mutableStateOf(ViralEffectConfig()) }
    var animProgress by remember { mutableFloatStateOf(0f) }
    var mediaLabel by remember { mutableStateOf("Studio Canvas") }

    LaunchedEffect(config.speed, config.effectType) {
        while (true) {
            animProgress = (animProgress + 0.02f * config.speed) % 1f
            delay(33L)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "vfx_img", "jpg")
                config = config.copy(mediaUriString = Uri.fromFile(copied).toString(), mediaIsVideo = false)
                mediaLabel = "Photo: ${MediaStorageUtils.getDisplayName(context, uri)}"
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = MediaStorageUtils.copyUriToAppMedia(context, uri, "vfx_vid", "mp4")
                config = config.copy(mediaUriString = Uri.fromFile(copied).toString(), mediaIsVideo = true)
                mediaLabel = "Video: ${MediaStorageUtils.getDisplayName(context, uri)}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("viral_effects_screen")
    ) {
        NickyCutTopBar(
            title = "Viral Effects",
            subtitle = "Newspaper, Flight Map, Glitch, Zoom & Kinetic FX",
            onBack = onBack,
            actions = {
                Button(
                    onClick = {
                        val req = ExportRequest(
                            sourceType = ExportSourceType.VIRAL_EFFECT,
                            title = "ViralFX_${config.effectType.name}",
                            viralEffect = config
                        )
                        onExport(json.encodeToString(req))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("viral_fx_export_button")
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
            // Live Effect Preview
            item {
                val accent = Color(config.accentColorHex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF06120D))
                        .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        when (config.effectType) {
                            ViralEffectType.NEWSPAPER_CUTOUT -> {
                                val cardW = size.width * 0.82f
                                val cardH = size.height * 0.48f
                                val left = (size.width - cardW) / 2f
                                val top = (size.height - cardH) / 2f
                                drawRoundRect(
                                    color = Color(0xFFF3ECE1),
                                    topLeft = Offset(left, top),
                                    size = Size(cardW, cardH),
                                    cornerRadius = CornerRadius(18f, 18f)
                                )
                                drawRect(
                                    color = accent.copy(alpha = 0.55f),
                                    topLeft = Offset(left + 20f, size.height / 2f - 18f),
                                    size = Size((cardW - 40f) * animProgress, 38f)
                                )
                            }
                            ViralEffectType.FLIGHT_ROUTE -> {
                                val p0 = Offset(size.width * 0.16f, size.height * 0.68f)
                                val p1 = Offset(size.width * 0.50f, size.height * 0.18f)
                                val p2 = Offset(size.width * 0.84f, size.height * 0.36f)
                                val path = Path().apply {
                                    moveTo(p0.x, p0.y)
                                    quadraticTo(p1.x, p1.y, p2.x, p2.y)
                                }
                                drawPath(path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 6f))
                                val t = animProgress
                                val omt = 1f - t
                                val px = omt * omt * p0.x + 2 * omt * t * p1.x + t * t * p2.x
                                val py = omt * omt * p0.y + 2 * omt * t * p1.y + t * t * p2.y
                                drawCircle(accent, radius = 10f, center = p0)
                                drawCircle(accent, radius = 10f, center = p2)
                                drawCircle(Color.White, radius = 14f, center = Offset(px, py))
                            }
                            ViralEffectType.ZOOM_PULSE -> {
                                val zoom = 0.5f + 0.35f * config.intensity * (0.5f + 0.5f * sin(animProgress * 2f * PI.toFloat()))
                                val rW = size.width * zoom
                                val rH = size.height * zoom
                                drawRoundRect(
                                    brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.4f), CyanAccent.copy(alpha = 0.3f))),
                                    topLeft = Offset((size.width - rW) / 2f, (size.height - rH) / 2f),
                                    size = Size(rW, rH),
                                    cornerRadius = CornerRadius(24f, 24f)
                                )
                            }
                            ViralEffectType.CYBER_GLITCH -> {
                                val barY = size.height * animProgress
                                drawRect(accent.copy(alpha = 0.5f), Offset(0f, barY), Size(size.width, 14f))
                                drawRect(CyanAccent.copy(alpha = 0.4f), Offset(0f, (barY + 48f) % size.height), Size(size.width, 8f))
                            }
                            ViralEffectType.SMOOTH_WHIP -> {
                                val x = -size.width * 0.3f + size.width * 1.6f * animProgress
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, accent.copy(alpha = 0.65f), Color.Transparent),
                                        startX = x - 140f,
                                        endX = x + 140f
                                    )
                                )
                            }
                            ViralEffectType.KINETIC_POP -> {
                                drawCircle(
                                    color = accent.copy(alpha = 0.22f),
                                    radius = 50f + 70f * animProgress
                                )
                            }
                        }
                    }

                    // Overlay preview text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        when (config.effectType) {
                            ViralEffectType.NEWSPAPER_CUTOUT -> Text(
                                text = config.headlineText.take(36),
                                color = Color(0xFF101814),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )
                            ViralEffectType.FLIGHT_ROUTE -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(config.originCity.uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(config.destinationCity.uppercase(), color = NeonEmerald, fontWeight = FontWeight.Bold)
                            }
                            else -> Text(
                                text = config.headlineText.take(28),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = mediaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }

            // Preset Selection
            item {
                StudioGlowCard(accentColor = OrangeAccent) {
                    Text("Effect Presets", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        items(ViralEffectType.entries) { fx ->
                            FilterChip(
                                selected = config.effectType == fx,
                                onClick = { config = config.copy(effectType = fx) },
                                label = { Text(fx.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Text(config.effectType.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            // Customization & Background Media
            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Photo", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null, tint = NeonEmerald)
                            Spacer(Modifier.width(4.dp))
                            Text("Video", color = TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (config.effectType == ViralEffectType.FLIGHT_ROUTE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.originCity,
                                onValueChange = { config = config.copy(originCity = it) },
                                label = { Text("Origin City") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = config.destinationCity,
                                onValueChange = { config = config.copy(destinationCity = it) },
                                label = { Text("Destination") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = config.headlineText,
                            onValueChange = { config = config.copy(headlineText = it) },
                            label = { Text("Headline / Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Accent Color", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        listOf(0xFF10F59A, 0xFF22D3EE, 0xFFA855F7, 0xFFFB923C).forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (config.accentColorHex == hex) 3.dp else 1.dp,
                                        color = if (config.accentColorHex == hex) Color.White else BorderSubtle,
                                        shape = CircleShape
                                    )
                                    .clickable { config = config.copy(accentColorHex = hex) }
                            )
                        }
                    }

                    Text("Intensity: ${(config.intensity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = config.intensity,
                        onValueChange = { config = config.copy(intensity = it) },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                    )

                    Text("Speed: ${String.format("%.1fx", config.speed)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = config.speed,
                        onValueChange = { config = config.copy(speed = it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                    )
                }
            }
        }
    }
}
