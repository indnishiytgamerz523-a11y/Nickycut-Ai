package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val toolType: String,
    val aspectRatio: String,
    val resolution: String,
    val durationMs: Long,
    val clipCount: Int,
    val timelineJson: String,
    val thumbnailUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_scripts")
data class SavedScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val language: String,
    val category: String,
    val hook: String,
    val narration: String,
    val scenesJson: String,
    val cta: String,
    val createdAt: Long = System.currentTimeMillis()
)
