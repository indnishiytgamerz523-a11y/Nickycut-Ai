package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatioOption(
    val label: String,
    val ratioW: Int,
    val ratioH: Int
) {
    VERTICAL_9_16("9:16 Vertical", 9, 16),
    LANDSCAPE_16_9("16:9 Landscape", 16, 9),
    SQUARE_1_1("1:1 Square", 1, 1);

    fun dimensionsFor(resolution: ResolutionOption): Pair<Int, Int> {
        val shortEdge = resolution.shortEdgePx
        return when (this) {
            VERTICAL_9_16 -> shortEdge to (shortEdge * 16 / 9)
            LANDSCAPE_16_9 -> (shortEdge * 16 / 9) to shortEdge
            SQUARE_1_1 -> shortEdge to shortEdge
        }
    }
}

@Serializable
enum class ResolutionOption(val label: String, val shortEdgePx: Int) {
    HD_720P("720p HD", 720),
    FHD_1080P("1080p Full HD", 1080)
}

@Serializable
enum class TransitionType(val label: String) {
    NONE("Cut"),
    FADE("Crossfade"),
    ZOOM_IN("Zoom Punch"),
    SLIDE_LEFT("Whip Slide"),
    GLITCH("Cyber Glitch")
}

@Serializable
enum class AccentColorType {
    EMERALD, CYAN, PURPLE, ORANGE
}

@Serializable
data class VideoClipItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val isImage: Boolean = false,
    val durationMs: Long = 5000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5000L,
    val volume: Float = 1f,
    val transition: TransitionType = TransitionType.NONE,
    val rotationDegrees: Int = 0
) {
    val effectiveDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(300L)
}

@Serializable
data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long = 0L,
    val endMs: Long = 4000L,
    val fontSizeSp: Float = 28f,
    val colorHex: Long = 0xFFFFFFFF,
    val highlightColorHex: Long = 0xFF10F59A,
    val verticalPosFraction: Float = 0.78f,
    val styleName: String = "Neon Glow"
)

@Serializable
data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val startMs: Long = 0L,
    val durationMs: Long = 10000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 10000L,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

@Serializable
data class ProjectTimeline(
    val clips: List<VideoClipItem> = emptyList(),
    val overlays: List<TextOverlayItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val aspectRatio: AspectRatioOption = AspectRatioOption.VERTICAL_9_16,
    val resolution: ResolutionOption = ResolutionOption.HD_720P,
    val fps: Int = 30
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(0L)
}

@Serializable
enum class KineticStyle(val label: String, val description: String) {
    NEON_PULSE("Neon Pulse", "Space Grotesk bold with emerald word glow"),
    BOLD_IMPACT("Bold Impact", "Bebas Neue condensed high-energy punch"),
    CYBER_MONO("Cyber Mono", "JetBrains Mono terminal highlight bar"),
    CINEMA_CLEAN("Cinema Clean", "Plus Jakarta crisp subtitle aesthetic"),
    KINETIC_STACK("Kinetic Stack", "Centered word-stack bounce emphasis")
}

@Serializable
data class KineticConfig(
    val text: String = "Create Scroll-Stopping Videos Locally on Android",
    val highlightedWords: List<String> = listOf("Scroll-Stopping", "Locally"),
    val style: KineticStyle = KineticStyle.NEON_PULSE,
    val fontSizeSp: Float = 42f,
    val primaryColorHex: Long = 0xFFF2FAF5,
    val highlightColorHex: Long = 0xFF10F59A,
    val backgroundColorHex: Long = 0xFF06130E,
    val verticalPosition: Float = 0.5f,
    val wordsPerSecond: Float = 2.4f,
    val startDelayMs: Long = 300L,
    val durationMs: Long = 6000L,
    val videoUriString: String? = null,
    val audioUriString: String? = null
)

@Serializable
enum class CaptionStyle(val label: String) {
    HORMOZI_POP("Bold Pop"),
    EMERALD_BOX("Emerald Pill"),
    CLEAN_CINEMA("Clean Cinema"),
    KARAOKE_GLOW("Word Glow")
}

@Serializable
data class SubtitleSegment(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val highlightWord: String = ""
)

@Serializable
data class CaptionsExportConfig(
    val segments: List<SubtitleSegment>,
    val style: CaptionStyle,
    val videoUriString: String? = null,
    val audioUriString: String? = null,
    val durationMs: Long = 8000L
)

@Serializable
data class SceneItem(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val narration: String,
    val visualDescription: String,
    val overlayText: String,
    val durationMs: Long = 3500L,
    val mediaUriString: String? = null,
    val isVideo: Boolean = false
)

@Serializable
data class ScriptToVideoConfig(
    val title: String,
    val scenes: List<SceneItem>,
    val audioUriString: String? = null
)

@Serializable
enum class ViralEffectType(val label: String, val subtitle: String) {
    NEWSPAPER_CUTOUT("Newspaper Cutout", "Animated press headline & highlighter sweep"),
    FLIGHT_ROUTE("Flight Route Map", "Animated arc path between cities"),
    KINETIC_POP("Kinetic Beat Pop", "Rhythmic high-contrast word bursts"),
    ZOOM_PULSE("Dynamic Zoom", "Smooth Ken Burns punch-in motion"),
    CYBER_GLITCH("Cyber Glitch", "RGB chromatic split & scanlines"),
    SMOOTH_WHIP("Whip Transition", "Directional emerald light-leak sweep")
}

@Serializable
data class ViralEffectConfig(
    val effectType: ViralEffectType = ViralEffectType.NEWSPAPER_CUTOUT,
    val headlineText: String = "BREAKING: OFFLINE MOBILE STUDIO LAUNCHED",
    val originCity: String = "Mumbai",
    val destinationCity: String = "Tokyo",
    val intensity: Float = 0.75f,
    val speed: Float = 1.0f,
    val accentColorHex: Long = 0xFF10F59A,
    val durationMs: Long = 5000L,
    val mediaUriString: String? = null,
    val mediaIsVideo: Boolean = false
)

@Serializable
enum class ExportSourceType {
    TIMELINE, KINETIC, CAPTIONS, SCRIPT_TO_VIDEO, VIRAL_EFFECT
}

@Serializable
data class ExportRequest(
    val sourceType: ExportSourceType,
    val title: String,
    val aspectRatio: AspectRatioOption = AspectRatioOption.VERTICAL_9_16,
    val resolution: ResolutionOption = ResolutionOption.HD_720P,
    val fps: Int = 30,
    val timeline: ProjectTimeline? = null,
    val kinetic: KineticConfig? = null,
    val captions: CaptionsExportConfig? = null,
    val scriptToVideo: ScriptToVideoConfig? = null,
    val viralEffect: ViralEffectConfig? = null
)

@Serializable
data class AiScriptResult(
    val title: String,
    val hook: String,
    val narration: String,
    val sceneDescriptions: List<String>,
    val cta: String
)

@Serializable
data class AiEditSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val category: String, // "Cut", "Caption", "Music", "Transition"
    val title: String,
    val detail: String,
    val startMs: Long = 0L,
    val endMs: Long = 2000L,
    val approved: Boolean = false
)

@Serializable
data class AiVideoAnalysisResult(
    val summary: String,
    val suggestions: List<AiEditSuggestion>
)

@Serializable
data class AiCreatorPlanResult(
    val titles: List<String>,
    val description: String,
    val hashtags: List<String>,
    val improvements: List<String>
)
