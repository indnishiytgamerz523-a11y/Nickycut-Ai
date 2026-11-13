package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.example.core.MediaStorageUtils
import com.example.data.model.CaptionStyle
import com.example.data.model.CaptionsExportConfig
import com.example.data.model.KineticConfig
import com.example.data.model.KineticStyle
import com.example.data.model.SceneItem
import com.example.data.model.ScriptToVideoConfig
import com.example.data.model.SubtitleSegment
import com.example.data.model.ViralEffectConfig
import com.example.data.model.ViralEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object VideoCanvasRenderer {

    suspend fun renderKineticVideo(
        context: Context,
        config: KineticConfig,
        width: Int,
        height: Int,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val bgFrames = if (!config.videoUriString.isNullOrBlank()) {
            loadSampledVideoFrames(context, Uri.parse(config.videoUriString), 18, maxOf(width, height))
        } else emptyList()

        val words = config.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val highlightSet = config.highlightedWords.map { it.lowercase() }.toSet()

        val videoOnlyFile = if (!config.audioUriString.isNullOrBlank()) {
            MediaStorageUtils.createTempFile(context, "kinetic_v", "mp4")
        } else outputFile

        renderSurfaceMp4(
            width = width,
            height = height,
            fps = fps,
            durationMs = config.durationMs.coerceIn(1500L, 60_000L),
            outputFile = videoOnlyFile,
            onProgress = { p ->
                val scaled = if (config.audioUriString != null) p * 0.85f else p
                onProgress(scaled)
            }
        ) { canvas, frameIndex, totalFrames, timeMs ->
            if (bgFrames.isNotEmpty()) {
                val idx = ((frameIndex * bgFrames.size) / totalFrames).coerceIn(0, bgFrames.lastIndex)
                drawCenterCropBitmap(canvas, bgFrames[idx], width, height)
                canvas.drawColor(Color.argb(145, 4, 10, 8))
            } else {
                drawEmeraldStudioBackdrop(canvas, width, height, config.backgroundColorHex.toInt(), timeMs)
            }

            drawKineticFrame(
                canvas = canvas,
                width = width,
                height = height,
                words = words,
                highlightSet = highlightSet,
                config = config,
                timeMs = timeMs
            )
        }

        if (!config.audioUriString.isNullOrBlank()) {
            muxVideoWithAudioUri(
                context = context,
                videoFile = videoOnlyFile,
                audioUri = Uri.parse(config.audioUriString),
                durationMs = config.durationMs,
                finalOutput = outputFile
            )
            videoOnlyFile.delete()
        }
        onProgress(1f)
        outputFile
    }

    suspend fun renderCaptionsVideo(
        context: Context,
        config: CaptionsExportConfig,
        width: Int,
        height: Int,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val totalDuration = (config.segments.maxOfOrNull { it.endMs } ?: config.durationMs)
            .coerceIn(2000L, 120_000L)
        val bgFrames = if (!config.videoUriString.isNullOrBlank()) {
            loadSampledVideoFrames(context, Uri.parse(config.videoUriString), 24, maxOf(width, height))
        } else emptyList()

        val audioSource = config.audioUriString ?: config.videoUriString
        val videoOnlyFile = if (!audioSource.isNullOrBlank()) {
            MediaStorageUtils.createTempFile(context, "captions_v", "mp4")
        } else outputFile

        renderSurfaceMp4(
            width = width,
            height = height,
            fps = fps,
            durationMs = totalDuration,
            outputFile = videoOnlyFile,
            onProgress = { p -> onProgress(if (audioSource != null) p * 0.85f else p) }
        ) { canvas, frameIndex, totalFrames, timeMs ->
            if (bgFrames.isNotEmpty()) {
                val idx = ((frameIndex * bgFrames.size) / totalFrames).coerceIn(0, bgFrames.lastIndex)
                drawCenterCropBitmap(canvas, bgFrames[idx], width, height)
            } else {
                drawEmeraldStudioBackdrop(canvas, width, height, 0xFF06130E.toInt(), timeMs)
            }

            val activeSegment = config.segments.firstOrNull { timeMs in it.startMs..it.endMs }
            if (activeSegment != null) {
                drawCaptionSegment(canvas, width, height, activeSegment, config.style, timeMs)
            }
        }

        if (!audioSource.isNullOrBlank()) {
            runCatching {
                muxVideoWithAudioUri(context, videoOnlyFile, Uri.parse(audioSource), totalDuration, outputFile)
            }.onFailure {
                videoOnlyFile.copyTo(outputFile, overwrite = true)
            }
            videoOnlyFile.delete()
        }
        onProgress(1f)
        outputFile
    }

    suspend fun renderViralEffectVideo(
        context: Context,
        config: ViralEffectConfig,
        width: Int,
        height: Int,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val mediaFrames: List<Bitmap> = when {
            config.mediaUriString.isNullOrBlank() -> emptyList()
            config.mediaIsVideo -> loadSampledVideoFrames(context, Uri.parse(config.mediaUriString), 20, maxOf(width, height))
            else -> listOfNotNull(runCatching {
                MediaStorageUtils.decodeBitmapFromUri(context, Uri.parse(config.mediaUriString), maxOf(width, height))
            }.getOrNull())
        }

        renderSurfaceMp4(
            width = width,
            height = height,
            fps = fps,
            durationMs = config.durationMs.coerceIn(2000L, 30_000L),
            outputFile = outputFile,
            onProgress = onProgress
        ) { canvas, frameIndex, totalFrames, timeMs ->
            val progress = (frameIndex.toFloat() / totalFrames.coerceAtLeast(1)).coerceIn(0f, 1f)
            val baseFrame = if (mediaFrames.isNotEmpty()) {
                val idx = ((frameIndex * mediaFrames.size) / totalFrames).coerceIn(0, mediaFrames.lastIndex)
                mediaFrames[idx]
            } else null

            drawViralEffectFrame(canvas, width, height, config, baseFrame, progress, timeMs)
        }
        onProgress(1f)
        outputFile
    }

    suspend fun renderScriptToVideo(
        context: Context,
        config: ScriptToVideoConfig,
        width: Int,
        height: Int,
        fps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val scenes = config.scenes.ifEmpty {
            listOf(
                SceneItem(
                    index = 1,
                    narration = config.title,
                    visualDescription = "Studio scene",
                    overlayText = config.title,
                    durationMs = 4000L
                )
            )
        }
        val sceneBitmaps = scenes.associate { scene ->
            val bmp = if (!scene.mediaUriString.isNullOrBlank()) {
                runCatching {
                    val uri = Uri.parse(scene.mediaUriString)
                    if (scene.isVideo) {
                        MediaStorageUtils.extractVideoKeyframes(context, uri, frameCount = 1, maxDimension = maxOf(width, height))
                            .firstOrNull()
                    } else {
                        MediaStorageUtils.decodeBitmapFromUri(context, uri, maxOf(width, height))
                    }
                }.getOrNull()
            } else null
            scene.id to bmp
        }

        val totalDuration = scenes.sumOf { it.durationMs.coerceAtLeast(1500L) }
        val videoOnlyFile = if (!config.audioUriString.isNullOrBlank()) {
            MediaStorageUtils.createTempFile(context, "s2v_v", "mp4")
        } else outputFile

        renderSurfaceMp4(
            width = width,
            height = height,
            fps = fps,
            durationMs = totalDuration,
            outputFile = videoOnlyFile,
            onProgress = { p -> onProgress(if (config.audioUriString != null) p * 0.85f else p) }
        ) { canvas, _, _, timeMs ->
            var cursor = 0L
            var currentScene = scenes.last()
            var localProgress = 0f
            for (s in scenes) {
                val dur = s.durationMs.coerceAtLeast(1500L)
                if (timeMs <= cursor + dur) {
                    currentScene = s
                    localProgress = ((timeMs - cursor).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    break
                }
                cursor += dur
            }

            val sceneBmp = sceneBitmaps[currentScene.id]
            if (sceneBmp != null) {
                // Subtle Ken Burns zoom
                val scale = 1f + (0.08f * localProgress)
                canvas.save()
                canvas.scale(scale, scale, width / 2f, height / 2f)
                drawCenterCropBitmap(canvas, sceneBmp, width, height)
                canvas.restore()
                canvas.drawColor(Color.argb(110, 4, 12, 9))
            } else {
                drawEmeraldStudioBackdrop(canvas, width, height, 0xFF071610.toInt(), timeMs)
            }

            // Scene number badge
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 16, 245, 154)
                style = Paint.Style.FILL
            }
            val badgeRect = RectF(width * 0.08f, height * 0.12f, width * 0.34f, height * 0.165f)
            canvas.drawRoundRect(badgeRect, 24f, 24f, badgePaint)
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF032114.toInt()
                textSize = width * 0.035f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("SCENE ${currentScene.index}", badgeRect.centerX(), badgeRect.centerY() + badgeTextPaint.textSize * 0.34f, badgeTextPaint)

            // Overlay text card
            val overlay = currentScene.overlayText.ifBlank { currentScene.narration }
            drawWrappedOverlayText(
                canvas = canvas,
                text = overlay,
                cx = width / 2f,
                cy = height * 0.72f,
                maxWidth = width * 0.84f,
                textSizePx = width * 0.056f,
                textColor = Color.WHITE,
                pillColor = Color.argb(190, 8, 22, 16)
            )
        }

        if (!config.audioUriString.isNullOrBlank()) {
            runCatching {
                muxVideoWithAudioUri(context, videoOnlyFile, Uri.parse(config.audioUriString), totalDuration, outputFile)
            }.onFailure {
                videoOnlyFile.copyTo(outputFile, overwrite = true)
            }
            videoOnlyFile.delete()
        }
        onProgress(1f)
        outputFile
    }

    private suspend fun renderSurfaceMp4(
        width: Int,
        height: Int,
        fps: Int,
        durationMs: Long,
        outputFile: File,
        onProgress: (Float) -> Unit,
        drawFrame: (canvas: Canvas, frameIndex: Int, totalFrames: Int, timeMs: Long) -> Unit
    ) {
        val safeW = if (width % 2 == 0) width else width - 1
        val safeH = if (height % 2 == 0) height else height - 1
        val totalFrames = ((durationMs / 1000.0) * fps).toInt().coerceAtLeast(fps)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, safeW, safeH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (safeW * safeH >= 1920 * 1080) 6_000_000 else 3_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outIndex >= 0) {
                    val encodedData: ByteBuffer = encoder.getOutputBuffer(outIndex)!!
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        try {
            for (frame in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val timeMs = (frame * 1000L) / fps
                val canvas = inputSurface.lockCanvas(null)
                try {
                    drawFrame(canvas, frame, totalFrames, timeMs)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(false)
                if (frame % 3 == 0) {
                    onProgress((frame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.98f))
                }
            }
            drainEncoder(true)
        } finally {
            runCatching { inputSurface.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private suspend fun muxVideoWithAudioUri(
        context: Context,
        videoFile: File,
        audioUri: Uri,
        durationMs: Long,
        finalOutput: File
    ) {
        val tempAac = MediaStorageUtils.createTempFile(context, "mux_aac", "m4a")
        try {
            AudioEngine.processAndExportAudio(
                context = context,
                primaryUri = audioUri,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                outputFormat = "M4A",
                outputFile = tempAac
            )

            val videoExtractor = MediaExtractor()
            val audioExtractor = MediaExtractor()
            val muxer = MediaMuxer(finalOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                videoExtractor.setDataSource(videoFile.absolutePath)
                audioExtractor.setDataSource(tempAac.absolutePath)

                var vTrackIn = -1
                var aTrackIn = -1
                var vTrackOut = -1
                var aTrackOut = -1

                for (i in 0 until videoExtractor.trackCount) {
                    val fmt = videoExtractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        vTrackIn = i
                        vTrackOut = muxer.addTrack(fmt)
                        videoExtractor.selectTrack(i)
                        break
                    }
                }
                for (i in 0 until audioExtractor.trackCount) {
                    val fmt = audioExtractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        aTrackIn = i
                        aTrackOut = muxer.addTrack(fmt)
                        audioExtractor.selectTrack(i)
                        break
                    }
                }

                muxer.start()
                val buf = ByteBuffer.allocate(512 * 1024)
                val info = MediaCodec.BufferInfo()

                if (vTrackIn >= 0) {
                    while (true) {
                        val size = videoExtractor.readSampleData(buf, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = videoExtractor.sampleTime
                        info.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(vTrackOut, buf, info)
                        videoExtractor.advance()
                    }
                }
                if (aTrackIn >= 0) {
                    val maxUs = durationMs * 1000L
                    while (true) {
                        val size = audioExtractor.readSampleData(buf, 0)
                        if (size < 0) break
                        val pts = audioExtractor.sampleTime
                        if (pts > maxUs) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = pts
                        info.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(aTrackOut, buf, info)
                        audioExtractor.advance()
                    }
                }
                muxer.stop()
            } finally {
                runCatching { videoExtractor.release() }
                runCatching { audioExtractor.release() }
                runCatching { muxer.release() }
            }
        } finally {
            tempAac.delete()
        }
    }

    private fun loadSampledVideoFrames(
        context: Context,
        uri: Uri,
        count: Int,
        maxDim: Int
    ): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val list = mutableListOf<Bitmap>()
        try {
            if (uri.scheme == "file" && uri.path != null) retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 4000L
            val step = (dur / count.coerceAtLeast(1)).coerceAtLeast(100L)
            for (i in 0 until count) {
                val frame = retriever.getFrameAtTime(i * step * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) list.add(MediaStorageUtils.scaleBitmapMax(frame, maxDim))
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        return list
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bmp: Bitmap, width: Int, height: Int) {
        val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        val left = (width - sw) / 2f
        val top = (height - sh) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + sw, top + sh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawEmeraldStudioBackdrop(canvas: Canvas, width: Int, height: Int, baseColor: Int, timeMs: Long) {
        val paint = Paint()
        val shift = (sin(timeMs / 1200.0) * 0.15f).toFloat()
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(baseColor, 0xFF0A241A.toInt(), 0xFF040A07.toInt()),
            floatArrayOf(0f, (0.5f + shift).coerceIn(0.2f, 0.8f), 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Subtle studio grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 16, 245, 154)
            strokeWidth = 1.5f
        }
        val step = (width / 8f).coerceAtLeast(60f)
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawKineticFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        words: List<String>,
        highlightSet: Set<String>,
        config: KineticConfig,
        timeMs: Long
    ) {
        if (words.isEmpty()) return
        val msPerWord = (1000f / config.wordsPerSecond.coerceAtLeast(0.5f)).toLong()
        val elapsed = (timeMs - config.startDelayMs).coerceAtLeast(0L)
        val visibleCount = ((elapsed / msPerWord).toInt() + 1).coerceIn(1, words.size)
        val activeIndex = visibleCount - 1

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (width / 720f) * config.fontSizeSp * 2.2f
            typeface = when (config.style) {
                KineticStyle.CYBER_MONO -> Typeface.MONOSPACE
                else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            textAlign = Paint.Align.CENTER
        }

        val yCenter = height * config.verticalPosition.coerceIn(0.18f, 0.85f)
        val maxLineW = width * 0.84f

        if (config.style == KineticStyle.KINETIC_STACK) {
            val word = words[activeIndex]
            val clean = word.trim(',', '.', '!', '?', ':').lowercase()
            val isHighlighted = clean in highlightSet
            textPaint.textSize *= 1.35f
            textPaint.color = if (isHighlighted) config.highlightColorHex.toInt() else config.primaryColorHex.toInt()
            if (isHighlighted) {
                textPaint.setShadowLayer(24f, 0f, 0f, config.highlightColorHex.toInt())
            }
            canvas.drawText(word.uppercase(), width / 2f, yCenter, textPaint)
            return
        }

        // Group revealed words into lines
        val lines = mutableListOf<List<Int>>()
        var currentLine = mutableListOf<Int>()
        var currentWidth = 0f
        val spaceW = textPaint.measureText(" ")
        for (i in 0 until visibleCount) {
            val wWidth = textPaint.measureText(words[i])
            if (currentLine.isNotEmpty() && currentWidth + spaceW + wWidth > maxLineW) {
                lines.add(currentLine)
                currentLine = mutableListOf(i)
                currentWidth = wWidth
            } else {
                currentLine.add(i)
                currentWidth += (if (currentLine.size > 1) spaceW else 0f) + wWidth
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)

        val lineHeight = textPaint.textSize * 1.35f
        val startY = yCenter - ((lines.size - 1) * lineHeight) / 2f
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.highlightColorHex.toInt()
            style = Paint.Style.FILL
        }

        lines.forEachIndexed { lineIdx, wordIndices ->
            val lineText = wordIndices.joinToString(" ") { words[it] }
            val totalLineW = textPaint.measureText(lineText)
            var cursorX = (width - totalLineW) / 2f
            val baselineY = startY + lineIdx * lineHeight
            textPaint.textAlign = Paint.Align.LEFT

            wordIndices.forEach { wIdx ->
                val w = words[wIdx]
                val wWidth = textPaint.measureText(w)
                val clean = w.trim(',', '.', '!', '?', ':').lowercase()
                val isHighlighted = clean in highlightSet || wIdx == activeIndex

                if (isHighlighted && (config.style == KineticStyle.NEON_PULSE || config.style == KineticStyle.CYBER_MONO)) {
                    val rect = RectF(
                        cursorX - 10f,
                        baselineY - textPaint.textSize * 0.88f,
                        cursorX + wWidth + 10f,
                        baselineY + textPaint.textSize * 0.22f
                    )
                    pillPaint.color = Color.argb(55, Color.red(config.highlightColorHex.toInt()), Color.green(config.highlightColorHex.toInt()), Color.blue(config.highlightColorHex.toInt()))
                    canvas.drawRoundRect(rect, 16f, 16f, pillPaint)
                }

                textPaint.color = if (isHighlighted) config.highlightColorHex.toInt() else config.primaryColorHex.toInt()
                canvas.drawText(w, cursorX, baselineY, textPaint)
                cursorX += wWidth + spaceW
            }
        }
    }

    private fun drawCaptionSegment(
        canvas: Canvas,
        width: Int,
        height: Int,
        segment: SubtitleSegment,
        style: CaptionStyle,
        timeMs: Long
    ) {
        val textSize = width * 0.062f
        val cy = height * 0.78f
        val words = segment.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return

        val segDur = (segment.endMs - segment.startMs).coerceAtLeast(300L)
        val prog = ((timeMs - segment.startMs).toFloat() / segDur.toFloat()).coerceIn(0f, 1f)
        val activeWordIdx = (prog * words.size).toInt().coerceIn(0, words.lastIndex)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val line = words.joinToString(" ")
        val totalW = paint.measureText(line).coerceAtMost(width * 0.86f)
        val boxPadX = 28f
        val boxPadY = 20f

        if (style == CaptionStyle.EMERALD_BOX || style == CaptionStyle.CLEAN_CINEMA) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (style == CaptionStyle.EMERALD_BOX) 0xE60B291D.toInt() else 0xCC070E0B.toInt()
            }
            val rect = RectF(
                (width - totalW) / 2f - boxPadX,
                cy - textSize - boxPadY,
                (width + totalW) / 2f + boxPadX,
                cy + boxPadY
            )
            canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
            if (style == CaptionStyle.EMERALD_BOX) {
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = 0xFF10F59A.toInt()
                }
                canvas.drawRoundRect(rect, 24f, 24f, border)
            }
        }

        val spaceW = paint.measureText(" ")
        val rawW = paint.measureText(line)
        if (rawW > width * 0.86f) {
            drawWrappedOverlayText(
                canvas, segment.text, width / 2f, cy, width * 0.86f, textSize,
                Color.WHITE, 0xCC091B14.toInt()
            )
            return
        }

        var cx = (width - rawW) / 2f
        words.forEachIndexed { idx, word ->
            val ww = paint.measureText(word)
            val isExplicitHighlight = segment.highlightWord.isNotBlank() &&
                word.contains(segment.highlightWord, ignoreCase = true)
            val isActive = idx == activeWordIdx && (style == CaptionStyle.KARAOKE_GLOW || style == CaptionStyle.HORMOZI_POP)

            paint.color = when {
                isExplicitHighlight || isActive -> 0xFF10F59A.toInt()
                else -> Color.WHITE
            }
            paint.setShadowLayer(8f, 0f, 3f, Color.BLACK)
            canvas.drawText(word, cx, cy, paint)
            cx += ww + spaceW
        }
    }

    private fun drawViralEffectFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: ViralEffectConfig,
        baseFrame: Bitmap?,
        progress: Float,
        timeMs: Long
    ) {
        val accent = config.accentColorHex.toInt()
        when (config.effectType) {
            ViralEffectType.NEWSPAPER_CUTOUT -> {
                if (baseFrame != null) {
                    drawCenterCropBitmap(canvas, baseFrame, width, height)
                    canvas.drawColor(Color.argb(150, 12, 14, 13))
                } else {
                    canvas.drawColor(0xFF0A110E.toInt())
                }
                val zoom = 0.94f + 0.08f * progress
                canvas.save()
                canvas.scale(zoom, zoom, width / 2f, height / 2f)
                val cardW = width * 0.84f
                val cardH = height * 0.30f
                val cardRect = RectF(
                    (width - cardW) / 2f,
                    (height - cardH) / 2f,
                    (width + cardW) / 2f,
                    (height + cardH) / 2f
                )
                val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2ECE1.toInt() }
                canvas.drawRoundRect(cardRect, 20f, 20f, paperPaint)

                // Animated highlighter sweep
                val sweepFrac = (progress * 1.4f).coerceIn(0f, 1f)
                val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(165, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
                canvas.drawRect(
                    cardRect.left + 28f,
                    cardRect.centerY() - 28f,
                    cardRect.left + 28f + (cardW - 56f) * sweepFrac,
                    cardRect.centerY() + 34f,
                    hlPaint
                )

                val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF111815.toInt()
                    textSize = width * 0.048f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(config.headlineText.take(36), cardRect.centerX(), cardRect.centerY() + 14f, headlinePaint)
                canvas.restore()
            }

            ViralEffectType.FLIGHT_ROUTE -> {
                drawEmeraldStudioBackdrop(canvas, width, height, 0xFF051319.toInt(), timeMs)
                val startX = width * 0.18f
                val startY = height * 0.62f
                val endX = width * 0.82f
                val endY = height * 0.36f
                val ctrlX = width * 0.50f
                val ctrlY = height * 0.22f

                val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                val path = Path().apply {
                    moveTo(startX, startY)
                    quadTo(ctrlX, ctrlY, endX, endY)
                }
                canvas.drawPath(path, routePaint)

                // Animated progress point along quadratic Bezier
                val t = (progress * config.speed).coerceIn(0f, 1f)
                val omt = 1f - t
                val px = omt * omt * startX + 2 * omt * t * ctrlX + t * t * endX
                val py = omt * omt * startY + 2 * omt * t * ctrlY + t * t * endY

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawCircle(startX, startY, 14f, glowPaint)
                canvas.drawCircle(endX, endY, 14f, glowPaint)
                canvas.drawCircle(px, py, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

                val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = width * 0.044f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(config.originCity.uppercase(), startX, startY + 54f, cityPaint)
                canvas.drawText(config.destinationCity.uppercase(), endX, endY - 36f, cityPaint)
            }

            ViralEffectType.ZOOM_PULSE -> {
                val zoom = 1.0f + (0.25f * config.intensity) * (0.5f + 0.5f * sin(progress * PI * 4f * config.speed).toFloat())
                canvas.save()
                canvas.scale(zoom, zoom, width / 2f, height / 2f)
                if (baseFrame != null) drawCenterCropBitmap(canvas, baseFrame, width, height)
                else drawEmeraldStudioBackdrop(canvas, width, height, 0xFF081C14.toInt(), timeMs)
                canvas.restore()
            }

            ViralEffectType.CYBER_GLITCH -> {
                if (baseFrame != null) drawCenterCropBitmap(canvas, baseFrame, width, height)
                else drawEmeraldStudioBackdrop(canvas, width, height, 0xFF070F12.toInt(), timeMs)
                val jitter = ((sin(timeMs * 0.05) * 24f) * config.intensity).toFloat()
                val bandPaint = Paint().apply {
                    color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
                val bandY = ((timeMs / 4) % height).toFloat()
                canvas.drawRect(0f, bandY, width.toFloat(), bandY + 18f, bandPaint)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = width * 0.075f
                    typeface = Typeface.MONOSPACE
                    textAlign = Paint.Align.CENTER
                }
                textPaint.color = 0xAA22D3EE.toInt()
                canvas.drawText(config.headlineText.take(22), width / 2f - jitter, height / 2f, textPaint)
                textPaint.color = accent
                canvas.drawText(config.headlineText.take(22), width / 2f + jitter, height / 2f, textPaint)
            }

            ViralEffectType.SMOOTH_WHIP -> {
                if (baseFrame != null) drawCenterCropBitmap(canvas, baseFrame, width, height)
                else drawEmeraldStudioBackdrop(canvas, width, height, 0xFF071A13.toInt(), timeMs)
                val sweepX = (-width * 0.4f) + (width * 1.8f * progress)
                val leakPaint = Paint().apply {
                    shader = LinearGradient(
                        sweepX - 180f, 0f, sweepX + 180f, 0f,
                        intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    alpha = (180 * config.intensity).toInt().coerceIn(40, 220)
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), leakPaint)
            }

            ViralEffectType.KINETIC_POP -> {
                if (baseFrame != null) {
                    drawCenterCropBitmap(canvas, baseFrame, width, height)
                    canvas.drawColor(Color.argb(135, 5, 12, 9))
                } else {
                    drawEmeraldStudioBackdrop(canvas, width, height, 0xFF06150F.toInt(), timeMs)
                }
                val words = config.headlineText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isNotEmpty()) {
                    val idx = ((progress * words.size * config.speed).toInt()) % words.size
                    val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accent
                        textSize = width * 0.11f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(26f, 0f, 0f, accent)
                    }
                    canvas.drawText(words[idx].uppercase(), width / 2f, height * 0.52f, wordPaint)
                }
            }
        }
    }

    private fun drawWrappedOverlayText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        maxWidth: Float,
        textSizePx: Float,
        textColor: Int,
        pillColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(w)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        val lineH = textSizePx * 1.28f
        val totalH = lines.size * lineH
        val maxLineW = lines.maxOfOrNull { paint.measureText(it) } ?: 100f
        val box = RectF(
            cx - maxLineW / 2f - 32f,
            cy - totalH / 2f - 24f,
            cx + maxLineW / 2f + 32f,
            cy + totalH / 2f + 24f
        )
        canvas.drawRoundRect(box, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor })

        var y = cy - totalH / 2f + textSizePx * 0.9f
        for (line in lines) {
            canvas.drawText(line, cx, y, paint)
            y += lineH
        }
    }

    fun generateSrtString(segments: List<SubtitleSegment>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            sb.append(index + 1).append('\n')
            sb.append(formatSrtTime(seg.startMs)).append(" --> ").append(formatSrtTime(seg.endMs)).append('\n')
            sb.append(seg.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val mins = (safe % 3_600_000L) / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val millis = safe % 1000L
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }
}
