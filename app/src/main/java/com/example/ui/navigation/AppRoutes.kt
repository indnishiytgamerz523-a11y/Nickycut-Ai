package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.AccentColorType
import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Tools : AppRoute
    @Serializable data class VideoEditor(
        val projectId: Long = -1L,
        val prefillJson: String = ""
    ) : AppRoute
    @Serializable data object KineticTypography : AppRoute
    @Serializable data object AutoCaptions : AppRoute
    @Serializable data class ScriptToVideo(val initialScript: String = "") : AppRoute
    @Serializable data class TextToVoice(val initialText: String = "") : AppRoute
    @Serializable data object ViralEffects : AppRoute
    @Serializable data object AudioEditor : AppRoute
    @Serializable data object BackgroundRemover : AppRoute
    @Serializable data object AiScriptWriter : AppRoute
    @Serializable data object AiVideoEditor : AppRoute
    @Serializable data object Projects : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data class Export(val exportPayloadJson: String) : AppRoute
}

data class StudioToolDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val badge: String,
    val isAiPowered: Boolean,
    val accent: AccentColorType,
    val icon: ImageVector,
    val route: AppRoute
)

object StudioToolCatalog {
    val allTools: List<StudioToolDescriptor> = listOf(
        StudioToolDescriptor(
            id = "video_editor",
            title = "Video Editor",
            subtitle = "Multi-track trim, split, overlays & transitions",
            category = "Core Studio",
            badge = "OFFLINE",
            isAiPowered = false,
            accent = AccentColorType.EMERALD,
            icon = Icons.Default.ContentCut,
            route = AppRoute.VideoEditor()
        ),
        StudioToolDescriptor(
            id = "kinetic_typography",
            title = "Kinetic Typography",
            subtitle = "Word-by-word animated text & highlights",
            category = "Typography & Captions",
            badge = "OFFLINE",
            isAiPowered = false,
            accent = AccentColorType.CYAN,
            icon = Icons.Default.Animation,
            route = AppRoute.KineticTypography
        ),
        StudioToolDescriptor(
            id = "auto_captions",
            title = "Auto Captions",
            subtitle = "Hindi, English & Hinglish speech subtitles",
            category = "Typography & Captions",
            badge = "SPEECH + SRT",
            isAiPowered = false,
            accent = AccentColorType.EMERALD,
            icon = Icons.Default.ClosedCaption,
            route = AppRoute.AutoCaptions
        ),
        StudioToolDescriptor(
            id = "script_to_video",
            title = "Script to Video",
            subtitle = "Scene storyboard, gallery media & voice",
            category = "Storytelling",
            badge = "OFFLINE",
            isAiPowered = false,
            accent = AccentColorType.ORANGE,
            icon = Icons.Default.MovieCreation,
            route = AppRoute.ScriptToVideo()
        ),
        StudioToolDescriptor(
            id = "text_to_voice",
            title = "Text to Voice",
            subtitle = "Hindi & English TTS studio with speed/pitch",
            category = "Audio & Voice",
            badge = "TTS ENGINE",
            isAiPowered = false,
            accent = AccentColorType.CYAN,
            icon = Icons.Default.RecordVoiceOver,
            route = AppRoute.TextToVoice()
        ),
        StudioToolDescriptor(
            id = "viral_effects",
            title = "Viral Effects",
            subtitle = "Newspaper, flight map, glitch & zoom FX",
            category = "Visual FX",
            badge = "OFFLINE",
            isAiPowered = false,
            accent = AccentColorType.ORANGE,
            icon = Icons.Default.SmartDisplay,
            route = AppRoute.ViralEffects
        ),
        StudioToolDescriptor(
            id = "audio_editor",
            title = "Audio Editor",
            subtitle = "Waveform trim, fade, merge & M4A/WAV",
            category = "Audio & Voice",
            badge = "OFFLINE",
            isAiPowered = false,
            accent = AccentColorType.EMERALD,
            icon = Icons.Default.GraphicEq,
            route = AppRoute.AudioEditor
        ),
        StudioToolDescriptor(
            id = "background_remover",
            title = "Background Remover",
            subtitle = "On-device ML cutout & backdrop studio",
            category = "Visual FX",
            badge = "ON-DEVICE ML",
            isAiPowered = false,
            accent = AccentColorType.PURPLE,
            icon = Icons.Default.FaceRetouchingNatural,
            route = AppRoute.BackgroundRemover
        ),
        StudioToolDescriptor(
            id = "ai_script_writer",
            title = "AI Script Writer",
            subtitle = "Reels & Shorts scripts in EN/HI/Hinglish",
            category = "Gemini AI",
            badge = "GEMINI AI",
            isAiPowered = true,
            accent = AccentColorType.PURPLE,
            icon = Icons.Default.Description,
            route = AppRoute.AiScriptWriter
        ),
        StudioToolDescriptor(
            id = "ai_video_editor",
            title = "AI Video & Creator",
            subtitle = "Smart cut review & Shorts metadata assistant",
            category = "Gemini AI",
            badge = "GEMINI AI",
            isAiPowered = true,
            accent = AccentColorType.CYAN,
            icon = Icons.Default.AutoAwesome,
            route = AppRoute.AiVideoEditor
        )
    )
}
