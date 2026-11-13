package com.example.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface BackgroundReplacement {
    data object Transparent : BackgroundReplacement
    data class SolidColor(val argb: Int) : BackgroundReplacement
    data class ImageBackdrop(val bitmap: Bitmap) : BackgroundReplacement
}

data class SegmentationResult(
    val foregroundOnly: Bitmap,
    val maskBuffer: FloatArray,
    val maskWidth: Int,
    val maskHeight: Int
)

object BackgroundRemovalEngine {

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    suspend fun segmentSubject(sourceBitmap: Bitmap): SegmentationMask =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
            segmenter.process(inputImage)
                .addOnSuccessListener { mask ->
                    if (cont.isActive) cont.resume(mask)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    suspend fun extractRawMask(sourceBitmap: Bitmap): SegmentationResult =
        withContext(Dispatchers.Default) {
            val mask = segmentSubject(sourceBitmap)
            val maskWidth = mask.width
            val maskHeight = mask.height
            val rawBuffer = mask.buffer
            rawBuffer.rewind()
            val floatBuffer: FloatBuffer = rawBuffer.asFloatBuffer()
            val maskArray = FloatArray(maskWidth * maskHeight)
            floatBuffer.get(maskArray)

            val cutout = compositeWithMask(
                sourceBitmap = sourceBitmap,
                maskArray = maskArray,
                maskWidth = maskWidth,
                maskHeight = maskHeight,
                threshold = 0.5f,
                replacement = BackgroundReplacement.Transparent
            )
            SegmentationResult(
                foregroundOnly = cutout,
                maskBuffer = maskArray,
                maskWidth = maskWidth,
                maskHeight = maskHeight
            )
        }

    suspend fun compositeWithMask(
        sourceBitmap: Bitmap,
        maskArray: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float,
        replacement: BackgroundReplacement
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = sourceBitmap.width
        val h = sourceBitmap.height
        val srcPixels = IntArray(w * h)
        sourceBitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        when (replacement) {
            is BackgroundReplacement.Transparent -> Unit
            is BackgroundReplacement.SolidColor -> {
                val c = Canvas(outBitmap)
                c.drawColor(replacement.argb)
            }
            is BackgroundReplacement.ImageBackdrop -> {
                val c = Canvas(outBitmap)
                val scaledBg = centerCropBitmap(replacement.bitmap, w, h)
                c.drawBitmap(scaledBg, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
            }
        }

        val bgPixels = IntArray(w * h)
        outBitmap.getPixels(bgPixels, 0, w, 0, 0, w, h)

        val feather = 0.14f
        val low = (threshold - feather).coerceIn(0f, 1f)
        val high = (threshold + feather).coerceIn(0.01f, 1f)

        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val my = (y * maskHeight) / h
            for (x in 0 until w) {
                val mx = (x * maskWidth) / w
                val conf = maskArray[my * maskWidth + mx]
                val alpha = when {
                    conf <= low -> 0f
                    conf >= high -> 1f
                    else -> (conf - low) / (high - low)
                }

                val idx = y * w + x
                val fg = srcPixels[idx]
                if (replacement is BackgroundReplacement.Transparent) {
                    val outA = (alpha * 255f).toInt().coerceIn(0, 255)
                    outPixels[idx] = (outA shl 24) or (fg and 0x00FFFFFF)
                } else {
                    val bg = bgPixels[idx]
                    val inv = 1f - alpha
                    val r = ((Color.red(fg) * alpha) + (Color.red(bg) * inv)).toInt().coerceIn(0, 255)
                    val g = ((Color.green(fg) * alpha) + (Color.green(bg) * inv)).toInt().coerceIn(0, 255)
                    val b = ((Color.blue(fg) * alpha) + (Color.blue(bg) * inv)).toInt().coerceIn(0, 255)
                    outPixels[idx] = Color.argb(255, r, g, b)
                }
            }
        }

        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        outBitmap
    }

    private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val xOff = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val yOff = ((scaledH - targetH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(
            scaled,
            xOff,
            yOff,
            targetW.coerceAtMost(scaledW - xOff),
            targetH.coerceAtMost(scaledH - yOff)
        )
    }
}
