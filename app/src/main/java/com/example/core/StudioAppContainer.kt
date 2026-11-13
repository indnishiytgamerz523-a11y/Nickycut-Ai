package com.example.core

import android.content.Context
import com.example.data.local.SettingsRepository
import com.example.data.local.StudioDatabase
import com.example.data.repository.GeminiCreatorRepository
import com.example.data.repository.ProjectRepository

class StudioAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { StudioDatabase.getInstance(appContext) }

    val projectRepository by lazy {
        ProjectRepository(
            projectDao = database.projectDao(),
            savedScriptDao = database.savedScriptDao()
        )
    }

    val settingsRepository by lazy {
        SettingsRepository(appContext)
    }

    val geminiRepository by lazy {
        GeminiCreatorRepository()
    }
}
