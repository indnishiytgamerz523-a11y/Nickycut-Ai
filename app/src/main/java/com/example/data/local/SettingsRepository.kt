package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AspectRatioOption
import com.example.data.model.ResolutionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "nickycut_settings")

data class StudioSettings(
    val defaultResolution: ResolutionOption = ResolutionOption.HD_720P,
    val defaultAspectRatio: AspectRatioOption = AspectRatioOption.VERTICAL_9_16,
    val defaultFps: Int = 30,
    val preferredAudioFormat: String = "M4A",
    val autoSaveProjects: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val RESOLUTION = stringPreferencesKey("default_resolution")
        val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val FPS = intPreferencesKey("default_fps")
        val AUDIO_FORMAT = stringPreferencesKey("preferred_audio_format")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_projects")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val res = runCatching {
            ResolutionOption.valueOf(prefs[Keys.RESOLUTION] ?: ResolutionOption.HD_720P.name)
        }.getOrDefault(ResolutionOption.HD_720P)
        val aspect = runCatching {
            AspectRatioOption.valueOf(prefs[Keys.ASPECT_RATIO] ?: AspectRatioOption.VERTICAL_9_16.name)
        }.getOrDefault(AspectRatioOption.VERTICAL_9_16)
        StudioSettings(
            defaultResolution = res,
            defaultAspectRatio = aspect,
            defaultFps = prefs[Keys.FPS] ?: 30,
            preferredAudioFormat = prefs[Keys.AUDIO_FORMAT] ?: "M4A",
            autoSaveProjects = prefs[Keys.AUTO_SAVE] ?: true
        )
    }

    suspend fun setDefaultResolution(option: ResolutionOption) {
        context.studioDataStore.edit { it[Keys.RESOLUTION] = option.name }
    }

    suspend fun setDefaultAspectRatio(option: AspectRatioOption) {
        context.studioDataStore.edit { it[Keys.ASPECT_RATIO] = option.name }
    }

    suspend fun setPreferredAudioFormat(format: String) {
        context.studioDataStore.edit { it[Keys.AUDIO_FORMAT] = format }
    }

    suspend fun setAutoSaveProjects(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }
}
