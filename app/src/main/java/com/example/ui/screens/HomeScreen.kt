package com.example.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaStorageUtils
import com.example.data.local.ProjectEntity
import com.example.data.repository.ProjectRepository
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
import com.example.ui.components.ToolGridCard
import com.example.ui.navigation.AppRoute
import com.example.ui.navigation.StudioToolCatalog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    projectRepository: ProjectRepository,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentProjects by projectRepository.recentProjects.collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            StudioToolCatalog.allTools
        } else {
            val q = searchQuery.trim().lowercase()
            StudioToolCatalog.allTools.filter {
                it.title.lowercase().contains(q) ||
                    it.subtitle.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
            }
        }
    }
    val toolRows = remember(filteredTools) { filteredTools.chunked(2) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .statusBarsPadding()
            .testTag("home_dashboard"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header: NickyCut AI logo + Greeting + Settings button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_nickycut_logo),
                        contentDescription = "NickyCut AI",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, NeonEmerald.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NickyCut ",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.headlineMedium,
                                color = NeonEmerald
                            )
                        }
                        Text(
                            text = "Offline-First Creator Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { onNavigate(AppRoute.Settings) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Search Tools Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search tools (captions, TTS, glitch, AI script...)",
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search tools",
                        tint = NeonEmerald
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input")
            )
        }

        // Studio Hero Card
        if (searchQuery.isBlank()) {
            item {
                val heroShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(heroShape)
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(NeonEmerald.copy(alpha = 0.65f), CyanAccent.copy(alpha = 0.35f))),
                            heroShape
                        )
                        .clickable { onNavigate(AppRoute.VideoEditor()) }
                        .testTag("hero_new_project_card")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_studio),
                        contentDescription = "NickyCut Studio Hero",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xE8050C09),
                                        Color(0xB8071610),
                                        Color(0x40050C09)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TimecodePill(text = "ZERO-LOGIN LOCAL ENGINE", accent = NeonEmerald)
                            Text(
                                text = "Cut, Caption & Render in 1080p",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "No watermarks. No mandatory account. Works offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { onNavigate(AppRoute.VideoEditor()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("hero_create_video_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "New Video Project", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Recent Projects Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Recent Projects",
                        actionLabel = if (recentProjects.isNotEmpty()) "View All" else null,
                        onAction = { onNavigate(AppRoute.Projects) }
                    )
                    if (recentProjects.isEmpty()) {
                        StudioGlowCard(
                            accentColor = BorderSubtle,
                            onClick = { onNavigate(AppRoute.VideoEditor()) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = NeonEmerald)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No saved projects yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Tap to start a new timeline edit—saved locally on your phone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentProjects, key = { it.id }) { project ->
                                RecentProjectChipCard(
                                    project = project,
                                    onClick = { onNavigate(AppRoute.VideoEditor(projectId = project.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Two-Column Tool Grid Header
        item {
            SectionHeader(
                title = if (searchQuery.isBlank()) "Creator Tools" else "Matching Tools (${filteredTools.size})",
                actionLabel = "All Tools",
                onAction = { onNavigate(AppRoute.Tools) }
            )
        }

        if (toolRows.isEmpty()) {
            item {
                StudioGlowCard(accentColor = BorderSubtle) {
                    Text(
                        text = "No tools match \"$searchQuery\". Try \"captions\", \"audio\", \"AI\", or \"glitch\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(toolRows) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tool ->
                        ToolGridCard(
                            tool = tool,
                            onClick = { onNavigate(tool.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectChipCard(
    project: ProjectEntity,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .width(196.dp)
            .clip(shape)
            .border(1.dp, BorderSubtle, shape)
            .clickable(onClick = onClick)
            .testTag("recent_project_${project.id}"),
        color = SurfaceCard,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimecodePill(text = MediaStorageUtils.formatDuration(project.durationMs), accent = CyanAccent)
                Text(
                    text = "${project.clipCount} clips",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${project.aspectRatio} • ${project.resolution}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
