package app.lumira.wallpapers.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import app.lumira.wallpapers.data.media.MediaFormats
import java.io.File
import app.lumira.wallpapers.domain.model.MediaKind

/**
 * App-private storage layout. Everything lives under
 * `Android/data/<pkg>/files` so no storage permission is ever required.
 *
 *   files/media/      wallpapers (imported + downloaded)
 *   files/thumbs/     generated WebP thumbnails (safe to delete)
 *   files/exports/    enhanced/transcoded outputs (temp)
 *   cache/            Coil image cache and scratch files
 */
object FileStore {

    fun root(context: Context) = context.filesDir

    fun mediaDir(context: Context) = File(context.filesDir, "media").apply { mkdirs() }

    fun thumbsDir(context: Context) = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun exportsDir(context: Context) = File(context.filesDir, "exports").apply { mkdirs() }

    fun cacheDir(context: Context) = File(context.cacheDir, "images").apply { mkdirs() }

    fun mediaFile(context: Context, name: String) = File(mediaDir(context), name)

    fun thumbFile(context: Context, name: String) = File(thumbsDir(context), name)

    fun exportFile(context: Context, name: String) = File(exportsDir(context), name)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return File(uri.path!!).name
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    fun extensionFor(mime: String?, displayName: String?, kind: MediaKind): String {
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrEmpty() && fromName.length in 2..5) {
            val inImage = fromName in MediaFormats.imageExtensions
            val inVideo = fromName in MediaFormats.videoExtensions
            if ((kind == MediaKind.IMAGE && inImage) || (kind == MediaKind.VIDEO && inVideo)) {
                return if (fromName == "jpeg") "jpg" else fromName
            }
        }
        return when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            "image/avif" -> "avif"
            "image/gif" -> "gif"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "video/mp4" -> "mp4"
            "video/x-matroska" -> "mkv"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/3gpp" -> "3gp"
            else -> if (kind == MediaKind.IMAGE) "jpg" else "mp4"
        }
    }

    /** Streams a content URI into [dest] without loading it into memory. */
    fun copyUriToFile(context: Context, uri: Uri, dest: File): File {
        dest.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el archivo")
        input.use { inStream ->
            dest.outputStream().use { out ->
                inStream.copyTo(out, DEFAULT_BUFFER_SIZE)
            }
        }
        return dest
    }

    fun freeSpaceBytes(): Long =
        runCatching { File(Environment.getExternalStorageDirectory(), "..").usableSpace }
            .getOrDefault(0L)

    fun sanitizeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(64).ifBlank { "wallpaper" }
}
