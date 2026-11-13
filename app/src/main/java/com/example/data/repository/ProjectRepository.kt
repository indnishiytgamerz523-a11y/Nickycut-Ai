package com.example.data.repository

import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.SavedScriptDao
import com.example.data.local.SavedScriptEntity
import com.example.data.model.AiScriptResult
import com.example.data.model.ProjectTimeline
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val savedScriptDao: SavedScriptDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val allProjects: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()
    val recentProjects: Flow<List<ProjectEntity>> = projectDao.observeRecentProjects(6)
    val allSavedScripts: Flow<List<SavedScriptEntity>> = savedScriptDao.observeAllScripts()

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun saveTimelineProject(
        existingId: Long? = null,
        name: String,
        toolType: String = "Video Editor",
        timeline: ProjectTimeline
    ): Long {
        val serialized = json.encodeToString(timeline)
        val thumbUri = timeline.clips.firstOrNull()?.uriString
        val now = System.currentTimeMillis()
        val entity = if (existingId != null && existingId > 0L) {
            val existing = projectDao.getProjectById(existingId)
            ProjectEntity(
                id = existingId,
                name = name.ifBlank { existing?.name ?: "Untitled Edit" },
                toolType = existing?.toolType ?: toolType,
                aspectRatio = timeline.aspectRatio.label,
                resolution = timeline.resolution.label,
                durationMs = timeline.totalDurationMs,
                clipCount = timeline.clips.size,
                timelineJson = serialized,
                thumbnailUri = thumbUri ?: existing?.thumbnailUri,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        } else {
            ProjectEntity(
                name = name.ifBlank { "Studio Project" },
                toolType = toolType,
                aspectRatio = timeline.aspectRatio.label,
                resolution = timeline.resolution.label,
                durationMs = timeline.totalDurationMs,
                clipCount = timeline.clips.size,
                timelineJson = serialized,
                thumbnailUri = thumbUri,
                createdAt = now,
                updatedAt = now
            )
        }
        return projectDao.insertProject(entity)
    }

    fun decodeTimeline(timelineJson: String): ProjectTimeline {
        return runCatching {
            json.decodeFromString<ProjectTimeline>(timelineJson)
        }.getOrElse { ProjectTimeline() }
    }

    suspend fun renameProject(id: Long, newName: String) {
        if (newName.isNotBlank()) {
            projectDao.renameProject(id, newName.trim())
        }
    }

    suspend fun duplicateProject(project: ProjectEntity): Long {
        val now = System.currentTimeMillis()
        return projectDao.insertProject(
            project.copy(
                id = 0L,
                name = "${project.name} (Copy)",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }

    suspend fun saveScript(
        language: String,
        category: String,
        result: AiScriptResult
    ): Long {
        return savedScriptDao.insertScript(
            SavedScriptEntity(
                title = result.title,
                language = language,
                category = category,
                hook = result.hook,
                narration = result.narration,
                scenesJson = json.encodeToString(result.sceneDescriptions),
                cta = result.cta
            )
        )
    }

    suspend fun deleteScript(id: Long) {
        savedScriptDao.deleteScriptById(id)
    }

    fun decodeScenes(scenesJson: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(scenesJson) }.getOrDefault(emptyList())
    }
}
