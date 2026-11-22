package app.lumira.wallpapers.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import app.lumira.wallpapers.data.repo.ThumbKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Thumbnail + orientation helpers.
 *
 * Thumbnails are encoded as small WebP (quality 78) to keep the thumb directory tiny.
 * AVIF / HEIC / animated GIF are decoded through the platform decoders, which is why
 * BitmapFactory is used instead of a heavy image library.
 */
object ImageUtils {

    const val THUMB_WIDTH = 480

    suspend fun createThumbnail(
        context: Context,
        source: File,
        kind: ThumbKind,
        outFile: File,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = when (kind) {
                ThumbKind.IMAGE -> decodeImageThumb(source)
                ThumbKind.VIDEO -> decodeVideoFrame(source)
            } ?: return@withContext null
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val ok = bitmap.compress(Bitmap.CompressFormat.WEBP, 78, out)
                if (!ok) error("No se pudo codificar la miniatura")
            }
            bitmap.recycle()
            outFile
        }.getOrNull()
    }

    private fun decodeImageThumb(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return null
        val rotation = readRotation(source)
        return if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }

    private fun decodeVideoFrame(source: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { scaleDown(it, THUMB_WIDTH) }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            maxWidth,
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun readDimensions(file: File): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        val rot = readRotation(file)
        return if (rot == 90 || rot == 270) opts.outHeight to opts.outWidth
        else opts.outWidth to opts.outHeight
    }

    fun readRotation(file: File): Int = runCatching {
        when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    /**
     * Bakes EXIF rotation into the JPEG bytes so WallpaperManager and Coil both see
     * the image upright. Returns the (possibly new) rotation read from disk after write.
     */
    suspend fun applyExifRotation(file: File, rotation: Int): Int = withContext(Dispatchers.IO) {
        if (rotation == 0) return@withContext 0
        runCatching {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext rotation
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            file.outputStream().use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            if (rotated !== bitmap) bitmap.recycle()
            bitmap.recycle()
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
            0
        }.getOrDefault(rotation)
    }

    /** Average color of an image, used as a shimmer/placeholder tint. */
    suspend fun dominantColor(file: File): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val size = Size(24, 24)
            var sample = 1
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            while (bounds.outWidth / (sample * 2) >= size.width) sample *= 2
            val bmp = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching null
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            for (p in pixels) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
            bmp.recycle()
            if (n == 0L) null else android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }.getOrNull()
    }

    @Throws(IOException::class)
    fun copyForExport(source: File, dest: File): File {
        source.copyTo(dest, overwrite = true)
        return dest
    }
}
