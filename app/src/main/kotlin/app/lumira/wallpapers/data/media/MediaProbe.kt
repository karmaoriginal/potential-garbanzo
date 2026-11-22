package app.lumira.wallpapers.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import app.lumira.wallpapers.domain.model.MediaKind
import java.io.File

/** Basic media facts used by the importer, enhancer and UI. */
data class MediaInfo(
    val kind: MediaKind,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val bitrate: Int = 0,
    val rotationDegrees: Int = 0,
) {
    val shortSide: Int get() = minOf(width, height)
    val isLowQuality: Boolean
        get() = when (kind) {
            MediaKind.IMAGE -> shortSide < 1080
            MediaKind.VIDEO -> shortSide < 720 || (bitrate in 1..1_400_000 && durationMs > 0)
        }
}

/** File format support matrix (verified against the platform decoders on API 26+). */
object MediaFormats {
    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp")
    val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "3gp", "m4v", "ts")

    val imageMime = setOf(
        "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif",
        "image/avif", "image/gif", "image/bmp", "image/x-ms-bmp",
    )
    val videoMime = setOf(
        "video/mp4", "video/x-matroska", "video/webm", "video/quicktime",
        "video/3gpp", "video/x-msvideo", "video/mp2t",
    )

    /** MIME types offered to the Photo Picker / SAF picker. */
    val pickerMimeTypes: Array<String> = buildList {
        addAll(imageMime)
        addAll(videoMime)
    }.sorted().toTypedArray()

    fun kindFor(mime: String?, name: String?): MediaKind? {
        val m = mime?.lowercase()
        if (m != null && m in imageMime) return MediaKind.IMAGE
        if (m != null && m in videoMime) return MediaKind.VIDEO
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        if (ext != null && ext.isNotEmpty()) {
            if (ext in imageExtensions) return MediaKind.IMAGE
            if (ext in videoExtensions) return MediaKind.VIDEO
        }
        return null
    }
}

/**
 * Reads dimensions/duration without decoding full bitmaps where avoidable.
 * All calls are safe off the main thread.
 */
object MediaProbe {

    fun probe(context: Context, uri: Uri, mime: String?, displayName: String?): MediaInfo? {
        val kind = MediaFormats.kindFor(mime, displayName) ?: return null
        val size = querySize(context, uri)
        return when (kind) {
            MediaKind.IMAGE -> probeImage(context, uri, mime, size)
            MediaKind.VIDEO -> probeVideo(context, uri, mime, size)
        }
    }

    fun probeFile(file: File, mime: String?): MediaInfo? {
        val kind = MediaFormats.kindFor(mime, file.name) ?: return null
        return when (kind) {
            MediaKind.IMAGE -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val rotation = runCatching {
                    ExifInterface(file.absolutePath)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).toDegrees()
                MediaInfo(
                    kind = MediaKind.IMAGE,
                    mimeType = mime,
                    width = if (rotation == 90 || rotation == 270) opts.outHeight else opts.outWidth,
                    height = if (rotation == 90 || rotation == 270) opts.outWidth else opts.outHeight,
                    durationMs = 0,
                    sizeBytes = file.length(),
                    rotationDegrees = rotation,
                )
            }
            MediaKind.VIDEO -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    videoInfo(r, mime, file.length())
                } catch (t: Throwable) {
                    null
                } finally {
                    runCatching { r.release() }
                }
            }
        }
    }

    private fun probeImage(context: Context, uri: Uri, mime: String?, size: Long): MediaInfo? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        var width = opts.outWidth
        var height = opts.outHeight
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).toDegrees()
        if (rotation == 90 || rotation == 270) {
            val w = width; width = height; height = w
        }
        if (width <= 0) return null
        return MediaInfo(MediaKind.IMAGE, mime, width, height, 0, size, rotationDegrees = rotation)
    }

    private fun probeVideo(context: Context, uri: Uri, mime: String?, size: Long): MediaInfo? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            videoInfo(r, mime, size)
        } catch (t: Throwable) {
            MediaInfo(MediaKind.VIDEO, mime, 0, 0, 0, size)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun videoInfo(r: MediaMetadataRetriever, mime: String?, size: Long): MediaInfo {
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0
        val bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull() ?: 0
        val swap = rotation == 90 || rotation == 270
        return MediaInfo(
            kind = MediaKind.VIDEO,
            mimeType = mime,
            width = if (swap) h else w,
            height = if (swap) w else h,
            durationMs = duration,
            sizeBytes = size,
            bitrate = bitrate,
            rotationDegrees = rotation,
        )
    }

    private fun querySize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }.getOrDefault(0L) ?: 0L
}

internal fun Int.toDegrees(): Int = when (this) {
    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}

/** Bitmap decoding helpers that respect screen size (never decode a 4K bitmap for a thumb). */
object BitmapTools {

    fun decodeSampled(file: File, target: Size): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target.width &&
            bounds.outHeight / (sample * 2) >= target.height
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    /** Scale to fit inside [maxWidth]x[maxHeight] preserving aspect ratio. */
    fun scaleToFit(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (src.width <= maxWidth && src.height <= maxHeight) return src
        val ratio = minOf(maxWidth.toFloat() / src.width, maxHeight.toFloat() / src.height)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }
}

/** Free-space helpers for storage computations. */
fun File.dirSize(): Long =
    if (!exists()) 0L
    else walkTopDown().filter { it.isFile }.sumOf { it.length() }
