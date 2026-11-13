package com.example.media

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.model.AspectRatioOption
import com.example.data.model.ProjectTimeline
import com.example.data.model.ResolutionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VideoTransformerEngine {

    @OptIn(UnstableApi::class)
    suspend fun exportTimelineWithTransformer(
        context: Context,
        timeline: ProjectTimeline,
        resolution: ResolutionOption,
        aspectRatio: AspectRatioOption,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Main) {
        if (timeline.clips.isEmpty()) {
            throw IllegalStateException("Timeline has no video or image clips to export.")
        }

        val (targetW, targetH) = aspectRatio.dimensionsFor(resolution)
        val presentationEffect = Presentation.createForWidthAndHeight(
            targetW,
            targetH,
            Presentation.LAYOUT_SCALE_TO_FIT
        )

        val overlayEffects = mutableListOf<TextureOverlay>()
        timeline.overlays.forEach { overlay ->
            if (overlay.text.isNotBlank()) {
                val spannable = SpannableString(overlay.text).apply {
                    setSpan(
                        ForegroundColorSpan(overlay.colorHex.toInt()),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        RelativeSizeSpan((overlay.fontSizeSp / 22f).coerceIn(0.8f, 2.5f)),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                overlayEffects.add(TextOverlay.createStaticTextOverlay(spannable))
            }
        }

        val editedVideoClips = timeline.clips.map { clip ->
            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(clip.uriString))
            if (clip.isImage) {
                mediaItemBuilder.setImageDurationMs(clip.effectiveDurationMs)
            } else {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 300L))
                        .build()
                )
            }

            val clipEffects = mutableListOf<Effect>()
            if (clip.rotationDegrees != 0) {
                clipEffects.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(clip.rotationDegrees.toFloat())
                        .build()
                )
            }
            clipEffects.add(presentationEffect)
            if (overlayEffects.isNotEmpty()) {
                clipEffects.add(OverlayEffect(overlayEffects))
            }

            val builder = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(androidx.media3.transformer.Effects(emptyList(), clipEffects))

            if (clip.isImage) {
                builder.setFrameRate(fps)
            } else if (clip.volume <= 0.02f) {
                builder.setRemoveAudio(true)
            }
            builder.build()
        }

        val mainSequence = EditedMediaItemSequence.Builder(editedVideoClips).build()

        val sequences = mutableListOf(mainSequence)
        val firstAudio = timeline.audioTracks.firstOrNull()
        if (firstAudio != null) {
            val audioMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(firstAudio.uriString))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(firstAudio.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(firstAudio.trimEndMs.coerceAtLeast(firstAudio.trimStartMs + 500L))
                        .build()
                )
                .build()
            val editedAudio = EditedMediaItem.Builder(audioMediaItem).build()
            sequences.add(
                EditedMediaItemSequence.Builder(listOf(editedAudio))
                    .setIsLooping(true)
                    .build()
            )
        }

        val composition = Composition.Builder(sequences).build()

        suspendCancellableCoroutine<File> { cont ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var finished = false

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished = true
                        onProgress(1f)
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finished = true
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    exportException.localizedMessage ?: "Media3 video export failed.",
                                    exportException
                                )
                            )
                        }
                    }
                })
                .build()

            transformer.start(composition, outputFile.absolutePath)

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (finished || !cont.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    handler.postDelayed(this, 180L)
                }
            }
            handler.postDelayed(pollRunnable, 150L)

            cont.invokeOnCancellation {
                finished = true
                handler.removeCallbacks(pollRunnable)
                runCatching { transformer.cancel() }
            }
        }
    }
}
