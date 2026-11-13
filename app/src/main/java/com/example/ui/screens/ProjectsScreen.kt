package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaStorageUtils
import com.example.data.local.ProjectEntity
import com.example.data.repository.ProjectRepository
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.StudioGlowCard
import com.example.ui.components.TimecodePill
import com.example.ui.navigation.AppRoute
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projectRepository: ProjectRepository,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by projectRepository.allProjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var projectToRename by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("projects_screen")
    ) {
        NickyCutTopBar(
            title = "Local Projects",
            subtitle = "${projects.size} saved on device • Source media untouched",
            actions = {
                Button(
                    onClick = { onNavigate(AppRoute.VideoEditor()) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("new_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold)
                }
            }
        )

        if (projects.isEmpty()) {
            EmptyStudioState(
                title = "No Projects Yet",
                description = "Create a video timeline and save it locally. Projects restore automatically across app restarts without touching original gallery files.",
                actionText = "Open Video Editor",
                onAction = { onNavigate(AppRoute.VideoEditor()) }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    val dateLabel = remember(project.updatedAt) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(project.updatedAt))
                    }
                    StudioGlowCard(
                        accentColor = NeonEmerald,
                        onClick = { onNavigate(AppRoute.VideoEditor(projectId = project.id)) },
                        modifier = Modifier.testTag("project_card_${project.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${project.aspectRatio} • ${project.resolution} • ${project.clipCount} clips",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            TimecodePill(
                                text = MediaStorageUtils.formatDuration(project.durationMs),
                                accent = CyanAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Updated $dateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onNavigate(AppRoute.VideoEditor(projectId = project.id)) },
                                    modifier = Modifier.testTag("open_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Open Editor", tint = NeonEmerald)
                                }
                                IconButton(
                                    onClick = {
                                        projectToRename = project
                                        renameText = project.name
                                    },
                                    modifier = Modifier.testTag("rename_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = TextPrimary)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { projectRepository.duplicateProject(project) }
                                    },
                                    modifier = Modifier.testTag("duplicate_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = CyanAccent)
                                }
                                IconButton(
                                    onClick = { projectToDelete = project },
                                    modifier = Modifier.testTag("delete_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    projectToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            containerColor = SurfaceCard,
            title = { Text("Rename Project", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Project Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { projectRepository.renameProject(target.id, renameText) }
                        projectToRename = null
                    }
                ) {
                    Text("Save", color = NeonEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    projectToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = SurfaceCard,
            title = { Text("Delete Project?", color = TextPrimary) },
            text = {
                Text(
                    text = "Remove \"${target.name}\" from NickyCut AI? Your original gallery videos, photos, and audio files will NOT be deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { projectRepository.deleteProject(target.id) }
                        projectToDelete = null
                    }
                ) {
                    Text("Delete", color = RoseError)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
