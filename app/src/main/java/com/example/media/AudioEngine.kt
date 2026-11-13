package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class DecodedAudioData(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long
)

data class WaveformResult(
    val peaks: List<Float>,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int
)

enum class AudioMergeMode {
    NONE,
    APPEND,
    MIX_OVERLAY
}

object AudioEngine {

    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 1 // Mono for fast, reliable mixing & AAC encoding

    suspend fun extractWaveform(
        context: Context,
        uri: Uri,
        barCount: Int = 72
    ): WaveformResult = withContext(Dispatchers.IO) {
        val decoded = decodeToMonoPcm(context, uri, TARGET_SAMPLE_RATE)
        val samples = decoded.samples
        if (samples.isEmpty()) {
            return@withContext WaveformResult(
                peaks = List(barCount) { 0.1f },
                durationMs = decoded.durationMs,
                sampleRate = decoded.sampleRate,
                channels = decoded.channels
            )
        }
        val chunkSize = (samples.size / barCount).coerceAtLeast(1)
        val rawPeaks = MutableList(barCount) { i ->
            val start = i * chunkSize
            val end = min(samples.size, start + chunkSize)
            if (start >= end) 0f else {
                var sumSq = 0.0
                for (j in start until end) {
                    val norm = samples[j] / 32768.0
                    sumSq += norm * norm
                }
                sqrt(sumSq / (end - start)).toFloat()
            }
        }
        val maxPeak = (rawPeaks.maxOrNull() ?: 1f).coerceAtLeast(0.05f)
        val normalized = rawPeaks.map { (it / maxPeak).coerceIn(0.06f, 1f) }
        WaveformResult(
            peaks = normalized,
            durationMs = decoded.durationMs,
            sampleRate = decoded.sampleRate,
            channels = decoded.channels
        )
    }

    suspend fun processAndExportAudio(
        context: Context,
        primaryUri: Uri,
        secondaryUri: Uri? = null,
        mergeMode: AudioMergeMode = AudioMergeMode.NONE,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
        volume: Float = 1f,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        outputFormat: String = "M4A",
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val primary = decodeToMonoPcm(context, primaryUri, TARGET_SAMPLE_RATE)
        val startSample = ((trimStartMs.coerceAtLeast(0L) * TARGET_SAMPLE_RATE) / 1000L).toInt()
            .coerceIn(0, primary.samples.size)
        val endSample = if (trimEndMs == Long.MAX_VALUE) {
            primary.samples.size
        } else {
            ((trimEndMs.coerceAtLeast(trimStartMs) * TARGET_SAMPLE_RATE) / 1000L).toInt()
                .coerceIn(startSample, primary.samples.size)
        }

        val trimmedLength = (endSample - startSample).coerceAtLeast(1)
        val trimmed = ShortArray(trimmedLength)
        if (primary.samples.isNotEmpty()) {
            System.arraycopy(primary.samples, startSample, trimmed, 0, trimmedLength)
        }

        // Apply gain + fade envelopes
        val fadeInSamples = ((fadeInMs * TARGET_SAMPLE_RATE) / 1000L).toInt().coerceIn(0, trimmedLength)
        val fadeOutSamples = ((fadeOutMs * TARGET_SAMPLE_RATE) / 1000L).toInt().coerceIn(0, trimmedLength)
        for (i in trimmed.indices) {
            var env = volume.coerceIn(0f, 2f)
            if (fadeInSamples > 0 && i < fadeInSamples) {
                env *= (i.toFloat() / fadeInSamples.toFloat())
            }
            if (fadeOutSamples > 0 && i >= trimmedLength - fadeOutSamples) {
                val rem = trimmedLength - 1 - i
                env *= (rem.toFloat() / fadeOutSamples.toFloat()).coerceAtLeast(0f)
            }
            val scaled = (trimmed[i] * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            trimmed[i] = scaled.toShort()
        }

        val finalSamples = if (secondaryUri != null && mergeMode != AudioMergeMode.NONE) {
            val secondary = decodeToMonoPcm(context, secondaryUri, TARGET_SAMPLE_RATE)
            when (mergeMode) {
                AudioMergeMode.APPEND -> {
                    val combined = ShortArray(trimmed.size + secondary.samples.size)
                    System.arraycopy(trimmed, 0, combined, 0, trimmed.size)
                    System.arraycopy(secondary.samples, 0, combined, trimmed.size, secondary.samples.size)
                    combined
                }
                AudioMergeMode.MIX_OVERLAY -> {
                    val outLen = maxOf(trimmed.size, secondary.samples.size)
                    val mixed = ShortArray(outLen)
                    for (i in 0 until outLen) {
                        val a = if (i < trimmed.size) trimmed[i].toInt() else 0
                        val b = if (i < secondary.samples.size) secondary.samples[i].toInt() else 0
                        mixed[i] = ((a + b) / 1.25f).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    }
                    mixed
                }
                AudioMergeMode.NONE -> trimmed
            }
        } else {
            trimmed
        }

        if (outputFormat.equals("WAV", ignoreCase = true)) {
            writeWavFile(finalSamples, TARGET_SAMPLE_RATE, TARGET_CHANNELS, outputFile)
        } else {
            encodePcmToAacM4a(finalSamples, TARGET_SAMPLE_RATE, TARGET_CHANNELS, outputFile)
        }
        outputFile
    }

    suspend fun decodeToMonoPcm(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = TARGET_SAMPLE_RATE
    ): DecodedAudioData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIndex == -1 || format == null) {
                throw IllegalStateException("No audio track found in selected file.")
            }
            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val sourceChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBytes = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var actualSampleRate = sourceSampleRate
            var actualChannels = sourceChannels

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            actualSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            actualChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            pcmBytes.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val rawBytes = pcmBytes.toByteArray()
            val shortCount = rawBytes.size / 2
            val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val interleaved = ShortArray(shortCount)
            shortBuf.get(interleaved)

            // Downmix to mono
            val mono = if (actualChannels <= 1) {
                interleaved
            } else {
                val frameCount = interleaved.size / actualChannels
                ShortArray(frameCount) { idx ->
                    var sum = 0
                    for (c in 0 until actualChannels) {
                        sum += interleaved[idx * actualChannels + c].toInt()
                    }
                    (sum / actualChannels).toShort()
                }
            }

            // Resample linearly if needed
            val resampled = if (actualSampleRate == targetSampleRate || mono.isEmpty()) {
                mono
            } else {
                val ratio = actualSampleRate.toDouble() / targetSampleRate.toDouble()
                val outSize = (mono.size / ratio).toInt().coerceAtLeast(1)
                ShortArray(outSize) { i ->
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, mono.lastIndex)
                    val idx1 = (idx0 + 1).coerceIn(0, mono.lastIndex)
                    val frac = srcPos - idx0
                    ((mono[idx0] * (1.0 - frac)) + (mono[idx1] * frac)).toInt().toShort()
                }
            }

            val computedDurationMs = if (durationUs > 0L) {
                durationUs / 1000L
            } else {
                (resampled.size * 1000L) / targetSampleRate
            }

            DecodedAudioData(
                samples = resampled,
                sampleRate = targetSampleRate,
                channels = 1,
                durationMs = computedDurationMs
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    fun writeWavFile(samples: ShortArray, sampleRate: Int, channels: Int, outputFile: File) {
        val byteDataLen = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + byteDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // Audio format = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16.toShort()) // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(byteDataLen)
        }
        val pcmBuf = ByteBuffer.allocate(byteDataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcmBuf.putShort(s)

        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            fos.write(pcmBuf.array())
        }
    }

    fun encodePcmToAacM4a(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
        outputFile: File
    ) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        try {
            val totalBytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) totalBytes.putShort(s)
            val rawData = totalBytes.array()

            var offset = 0
            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (offset >= rawData.size) {
                            val ptsUs = (offset.toLong() / 2L * 1_000_000L) / (sampleRate * channels)
                            encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunkLen = min(inBuf.remaining(), rawData.size - offset)
                            inBuf.put(rawData, offset, chunkLen)
                            val ptsUs = (offset.toLong() / 2L * 1_000_000L) / (sampleRate * channels)
                            encoder.queueInputBuffer(inIdx, 0, chunkLen, ptsUs, 0)
                            offset += chunkLen
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted && outBuf != null) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }
}
