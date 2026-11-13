package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaStorageUtils
import com.example.data.local.SettingsRepository
import com.example.data.local.StudioSettings
import com.example.data.model.AspectRatioOption
import com.example.data.model.ResolutionOption
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.NoticeType
import com.example.ui.components.StatusNoticeBanner
import com.example.ui.components.StudioGlowCard
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = StudioSettings())
    var cacheBytes by remember {
        mutableLongStateOf(MediaStorageUtils.getDirectorySizeBytes(MediaStorageUtils.getRenderCacheDir(context)))
    }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("settings_screen")
    ) {
        NickyCutTopBar(
            title = "Studio Settings",
            subtitle = "Export defaults, storage & offline capabilities"
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudioGlowCard(accentColor = NeonEmerald) {
                    Text("Default Export Resolution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResolutionOption.entries.forEach { res ->
                            FilterChip(
                                selected = settings.defaultResolution == res,
                                onClick = { scope.launch { settingsRepository.setDefaultResolution(res) } },
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
                        "Default Aspect Ratio",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatioOption.entries.forEach { ar ->
                            FilterChip(
                                selected = settings.defaultAspectRatio == ar,
                                onClick = { scope.launch { settingsRepository.setDefaultAspectRatio(ar) } },
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

                    Text(
                        "Audio Export Format",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("M4A", "WAV").forEach { fmt ->
                            FilterChip(
                                selected = settings.preferredAudioFormat == fmt,
                                onClick = { scope.launch { settingsRepository.setPreferredAudioFormat(fmt) } },
                                label = { Text(if (fmt == "M4A") "M4A (AAC)" else "WAV (Lossless)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDim,
                                    selectedLabelColor = NeonEmerald,
                                    containerColor = SurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-save Timeline Edits", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Keep local Room project synced", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoSaveProjects,
                            onCheckedChange = { scope.launch { settingsRepository.setAutoSaveProjects(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonEmerald)
                        )
                    }
                }
            }

            item {
                StudioGlowCard(accentColor = CyanAccent) {
                    Text("Render Cache & Storage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "Free device storage: ${MediaStorageUtils.formatBytes(MediaStorageUtils.getAvailableBytes(context))}\nTemporary render cache: ${MediaStorageUtils.formatBytes(cacheBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            val freed = MediaStorageUtils.clearRenderCache(context)
                            cacheBytes = 0L
                            cacheClearedMessage = "Cleared ${MediaStorageUtils.formatBytes(freed)} of temporary render files."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("clear_cache_button")
                    ) {
                        Text("Clear Render Cache")
                    }
                    cacheClearedMessage?.let {
                        StatusNoticeBanner(
                            message = it,
                            type = NoticeType.SUCCESS,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            item {
                StudioGlowCard(accentColor = PurpleAccent) {
                    Text("Offline Engines & Gemini AI Disclosure", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "• Video Editing, Kinetic Typography, Viral Effects, Audio Editor, WAV/M4A Export, and Image Background Removal (ML Kit) run 100% locally on your device.\n• No login, account, or subscription is required.\n• Gemini AI tools (AI Script Writer, AI Video Editor suggestions, AI Creator Assistant) connect via Firebase AI Logic + App Check and require an active internet connection and project quota.\n• Original media files in your gallery are never modified or deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
