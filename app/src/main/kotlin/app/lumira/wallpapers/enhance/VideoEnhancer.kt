package app.lumira.wallpapers.enhance

import android.content.Context
import android.media.MediaMetadataRetriever
import app.lumira.wallpapers.domain.model.QualityPreset
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of an enhancement run.
 */
data class EnhanceResult(
    val output: File,
    val width: Int,
    val height: Int,
    val codec: String,
    val elapsedMs: Long,
    val savedBytes: Long,
)

/**
 * One-pass, offline video enhancement ("better than the original, smaller"):
 *
 *  - Lanczos upscale to the requested short side (1080p / 1440p), never downscaling
 *  - hqdn3d for temporal + spatial noise reduction
 *  - unsharp for perceived detail
 *  - H.265 (HEVC) via the device hardware encoder when available, because it runs
 *    on the media DSP (fast, battery friendly) and halves bitrate at equal quality;
 *    falls back to the libx265 software encoder of ffmpeg-kit-video.
 *
 * Everything happens in a WorkManager job, never on the UI thread and never in real
 * time, so the screen can be off while it runs.
 */
class VideoEnhancer(private val context: Context) {

    interface Listener {
        fun onProgress(fraction: Float)
        fun onLog(line: String)
    }

    /**
     * Optional AI upscaler hook. A lightweight model (e.g. a quantized ESPCN /
     * RealESRGAN-tiny TFLite graph) can be plugged in later without touching the UI
     * or the worker. Disabled by default.
     */
    interface AiUpscaler {
        fun isAvailable(): Boolean
        /** Upscales [input] into [output]; returns false when the model is missing. */
        suspend fun upscale(input: File, output: File, targetShortSide: Int): Boolean
    }

    private val aiUpscaler: AiUpscaler = NoopAiUpscaler()

    suspend fun enhance(
        input: File,
        output: File,
        preset: QualityPreset,
        cancelFlag: AtomicBoolean,
        listener: Listener? = null,
        useAi: Boolean = false,
    ): Result<EnhanceResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(input.exists()) { "El archivo de origen no existe" }
            val started = System.currentTimeMillis()
            val (srcW, srcH) = probeSize(input)
            val targetShort = when (preset) {
                QualityPreset.NONE -> minOf(srcW, srcH)
                else -> preset.shortSide
            }
            val scale = if (preset == QualityPreset.NONE || minOf(srcW, srcH) >= targetShort) {
                1.0
            } else {
                targetShort.toDouble() / minOf(srcW, srcH)
            }

            // Even dimensions are mandatory for yuv420p / HEVC.
            var outW = (srcW * scale).toInt().let { if (it % 2 == 0) it else it + 1 }
            var outH = (srcH * scale).toInt().let { if (it % 2 == 0) it else it + 1 }
            outW = outW.coerceAtLeast(2)
            outH = outH.coerceAtLeast(2)

            var workingInput = input
            if (useAi && aiUpscaler.isAvailable()) {
                val aiOut = File(output.parentFile, "ai_${output.name}")
                if (aiUpscaler.upscale(input, aiOut, targetShort)) {
                    workingInput = aiOut
                }
            }

            val filter = buildFilters(outW, outH, scale > 1.0)
            val attempts = buildList {
                add(attemptHwHevc(workingInput, output, filter))
                add(attemptSwHevc(workingInput, output, filter))
            }

            var lastLog = ""
            var produced: File? = null
            for (command in attempts) {
                if (cancelFlag.get()) break
                output.delete()
                val durationMs = probeDurationMs(workingInput)
                val stats = java.util.concurrent.atomic.AtomicLong(-1L)
                val completion = java.util.concurrent.CountDownLatch(1)
                val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(
                    command,
                    { completion.countDown() },
                    com.arthenica.ffmpegkit.LogCallback { line ->
                        lastLog = line.message ?: ""
                    },
                    com.arthenica.ffmpegkit.StatisticsCallback { statistics ->
                        stats.set(statistics.time.toLong())
                    },
                )
                while (!completion.await(400, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (cancelFlag.get()) {
                        FFmpegKit.cancel(session.sessionId)
                        break
                    }
                    val elapsed = stats.get()
                    if (elapsed > 0 && durationMs > 0) {
                        listener?.onProgress(
                            (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.99f),
                        )
                    }
                }
                if (cancelFlag.get()) break
                lastLog = session.output?.takeLast(400) ?: lastLog
                if (output.exists() && output.length() > 0) {
                    produced = output
                    break
                }
            }

            if (cancelFlag.get()) error("CANCELED")
            val result = produced ?: error("La conversión falló: ${lastLog.takeLast(400)}")
            val (w, h) = probeSize(result)
            listener?.onProgress(1f)
            EnhanceResult(
                output = result,
                width = w,
                height = h,
                codec = "hevc",
                elapsedMs = System.currentTimeMillis() - started,
                savedBytes = input.length() - result.length(),
            )
        }
    }

    private fun buildFilters(outW: Int, outH: Int, upscale: Boolean): String {
        val filters = mutableListOf<String>()
        if (upscale) {
            // Lanczos is the sharpest of the cheap scalers; flags=lanczos+accurate_rnd
            // avoids the halo/ringing that bilinear produces on line art and text.
            filters += "scale=$outW:$outH:flags=lanczos+accurate_rnd+full_chroma_int"
        }
        filters += "hqdn3d=3:2:5:4"          // light denoise, keeps detail
        filters += "unsharp=5:5:0.8:3:3:0.4" // luma + chroma sharpening
        filters += "format=yuv420p"
        return filters.joinToString(",")
    }

    /** Hardware HEVC through MediaCodec: fast and battery friendly. */
    private fun attemptHwHevc(input: File, out: File, filter: String): Array<String> = arrayOf(
        "-hide_banner", "-y",
        "-i", input.absolutePath,
        "-vf", filter,
        "-c:v", "hevc_mediacodec",
        "-b:v", "6M",
        "-c:a", "copy",
        "-movflags", "+faststart",
        out.absolutePath,
    )

    /** Software x265 fallback (part of ffmpeg-kit-video). */
    private fun attemptSwHevc(input: File, out: File, filter: String): Array<String> = arrayOf(
        "-hide_banner", "-y",
        "-i", input.absolutePath,
        "-vf", filter,
        "-c:v", "libx265",
        "-preset", "veryfast",
        "-crf", "26",
        "-tag:v", "hvc1",
        "-c:a", "copy",
        "-movflags", "+faststart",
        out.absolutePath,
    )

    private fun probeSize(file: File): Pair<Int, Int> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } catch (t: Throwable) {
            1920 to 1080
        } finally {
            runCatching { r.release() }
        }
    }

    private fun probeDurationMs(file: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { r.release() }
        }
    }

    /** Placeholder implementation: reports "not available" so the pipeline stays fast. */
    private class NoopAiUpscaler : AiUpscaler {
        override fun isAvailable(): Boolean = false
        override suspend fun upscale(input: File, output: File, targetShortSide: Int): Boolean = false
    }
}
