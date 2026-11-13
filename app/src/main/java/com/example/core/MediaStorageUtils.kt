package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaMetadataInfo(
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val hasAudio: Boolean,
    val fileSizeBytes: Long
)

object MediaStorageUtils {

    private const val MIN_EXPORT_FREE_BYTES = 50L * 1024L * 1024L // 50 MB minimum safety margin

    fun getRenderCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "nickycut_renders")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectsMediaDir(context: Context): File {
        val dir = File(context.filesDir, "nickycut_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createTempFile(context: Context, prefix: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(getRenderCacheDir(context), "${prefix}_${stamp}.$extension")
    }

    fun getAvailableBytes(context: Context): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes
    }

    fun hasEnoughStorageForExport(context: Context, requiredBytes: Long = MIN_EXPORT_FREE_BYTES): Boolean {
        return getAvailableBytes(context) >= requiredBytes
    }

    fun getDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySizeBytes(file) else file.length()
        }
        return size
    }

    fun clearRenderCache(context: Context): Long {
        val dir = getRenderCacheDir(context)
        val freed = getDirectorySizeBytes(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name.ifBlank { "Media file" }
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "Media file"
            }
        }
        return uri.lastPathSegment ?: "Media file"
    }

    suspend fun copyUriToAppMedia(context: Context, uri: Uri, prefix: String, fallbackExt: String): File =
        withContext(Dispatchers.IO) {
            val originalName = getDisplayName(context, uri)
            val ext = originalName.substringAfterLast('.', fallbackExt).lowercase(Locale.US)
                .takeIf { it.length in 2..5 } ?: fallbackExt
            val outFile = File(getProjectsMediaDir(context), "${prefix}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")
            outFile
        }

    suspend fun extractVideoMetadata(context: Context, uri: Uri): MediaMetadataInfo =
        withContext(Dispatchers.IO) {
            val name = getDisplayName(context, uri)
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    retriever.setDataSource(uri.path)
                } else {
                    retriever.setDataSource(context, uri)
                }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 5000L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 1080
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 1920
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                val sizeBytes = getFileSize(context, uri)
                MediaMetadataInfo(name, duration, width, height, rotation, hasAudio, sizeBytes)
            } finally {
                runCatching { retriever.release() }
            }
        }

    suspend fun extractVideoKeyframes(
        context: Context,
        uri: Uri,
        frameCount: Int = 4,
        maxDimension: Int = 512
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 4000L
            val step = (durationMs / (frameCount + 1).coerceAtLeast(1)).coerceAtLeast(250L)
            for (i in 1..frameCount) {
                val timeUs = (i * step).coerceAtMost(durationMs - 100L).coerceAtLeast(0L) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    bitmaps.add(scaleBitmapMax(frame, maxDimension))
                }
            }
        } finally {
            runCatching { retriever.release() }
        }
        bitmaps
    }

    suspend fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sampleSize = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalStateException("Could not decode image.")
            scaleBitmapMax(decoded, maxDim)
        }

    fun scaleBitmapMax(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val ratio = maxDimension.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file" && uri.path != null) {
            return File(uri.path!!).length()
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) return c.getLong(idx)
            }
        }
        return 0L
    }

    suspend fun saveVideoToGallery(context: Context, sourceFile: File, displayTitle: String): Uri =
        withContext(Dispatchers.IO) {
            val safeTitle = displayTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "NickyCut_Export" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/NickyCut AI")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore video entry.")
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Failed to write exported video.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        }

    suspend fun saveImageToGallery(context: Context, bitmap: Bitmap, displayTitle: String): Uri =
        withContext(Dispatchers.IO) {
            val safeTitle = displayTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "NickyCut_Cutout" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NickyCut AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore image entry.")
            resolver.openOutputStream(itemUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("Failed to write PNG image.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        }

    suspend fun saveAudioToLibrary(
        context: Context,
        sourceFile: File,
        displayTitle: String,
        mimeType: String,
        extension: String
    ): Uri = withContext(Dispatchers.IO) {
        val safeTitle = displayTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "NickyCut_Audio" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/NickyCut AI")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore audio entry.")
        resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Failed to write audio file.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }
        itemUri
    }

    fun getShareUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun launchShareIntent(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms.coerceAtLeast(0L) % 1000L) / 100L
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMin = minutes % 60
            String.format(Locale.US, "%d:%02d:%02d", hours, remMin, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
