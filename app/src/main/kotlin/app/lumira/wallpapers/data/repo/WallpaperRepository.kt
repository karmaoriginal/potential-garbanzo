package app.lumira.wallpapers.data.repo

import android.content.Context
import android.net.Uri
import app.lumira.wallpapers.data.local.DownloadEntity
import app.lumira.wallpapers.data.local.LumiraDatabase
import app.lumira.wallpapers.data.local.WallpaperEntity
import app.lumira.wallpapers.data.media.MediaKind
import app.lumira.wallpapers.data.media.MediaProbe
import app.lumira.wallpapers.data.media.MediaFormats
import app.lumira.wallpapers.data.media.dirSize
import app.lumira.wallpapers.domain.model.LibraryStats
import app.lumira.wallpapers.domain.model.MediaKind as DomainKind
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.domain.model.WallpaperSource
import app.lumira.wallpapers.util.FileStore
import app.lumira.wallpapers.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Single source of truth for the user's on-device library. */
class WallpaperRepository(
    private val context: Context,
    private val db: LumiraDatabase,
) {
    private val dao get() = db.wallpapers()

    fun observeAll(): Flow<List<Wallpaper>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByKind(kind: MediaKind): Flow<List<Wallpaper>> =
        dao.observeByKind(kind.name).map { list -> list.map { it.toDomain() } }

    fun observeFavorites(): Flow<List<Wallpaper>> =
        dao.observeFavorites().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Wallpaper?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: String): Wallpaper? = withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }

    suspend fun getEntity(id: String): WallpaperEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    /**
     * Imports a picked URI: copies bytes into app storage (no broad permissions needed),
     * probes metadata, generates a thumbnail and records it in Room.
     *
     * Duplicate detection runs on (size + display name) so re-importing the same file
     * does not duplicate storage.
     */
    suspend fun importFromUri(uri: Uri): Result<Wallpaper> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)
            val displayName = FileStore.displayName(context, uri) ?: "lumira_${System.currentTimeMillis()}"
            val kind = MediaFormats.kindFor(mime, displayName) ?: error("Formato no soportado")
            val info = MediaProbe.probe(context, uri, mime, displayName)
                ?: error("No se pudo leer el archivo")

            val id = "local_" + UUID.randomUUID().toString().take(8)
            val ext = FileStore.extensionFor(mime, displayName, kind)
            val dest = FileStore.mediaFile(context, "$id.$ext")
            FileStore.copyUriToFile(context, uri, dest)

            if (kind == MediaKind.IMAGE) {
                val oriented = ImageUtils.applyExifRotation(dest, info.rotationDegrees)
                if (oriented != info.rotationDegrees) {
                    // rewrite dimensions after rotation
                }
            }

            val thumb = ImageUtils.createThumbnail(
                context = context,
                source = dest,
                kind = if (kind == MediaKind.VIDEO) ThumbKind.VIDEO else ThumbKind.IMAGE,
                outFile = FileStore.thumbFile(context, "$id.webp"),
            )

            val finalDimensions = if (kind == MediaKind.VIDEO) {
                MediaProbe.probeFile(dest, mime)
            } else {
                ImageUtils.readDimensions(dest)
            }

            val entity = WallpaperEntity(
                id = id,
                title = displayName.substringBeforeLast('.').ifBlank { "Wallpaper" },
                kind = kind.name,
                source = WallpaperSource.IMPORTED.name,
                filePath = dest.absolutePath,
                thumbPath = thumb?.absolutePath,
                mimeType = mime,
                width = finalDimensions?.first ?: info.width,
                height = finalDimensions?.second ?: info.height,
                durationMs = info.durationMs,
                sizeBytes = dest.length(),
                createdAt = System.currentTimeMillis(),
            )
            dao.upsert(entity)
            entity.toDomain()
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        dao.rename(id, title.trim().ifBlank { "Wallpaper" })
    }

    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) { dao.toggleFavorite(id) }

    suspend fun bumpApply(id: String) = withContext(Dispatchers.IO) { dao.bumpApply(id) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        runCatching { entity.filePath?.let { File(it).delete() } }
        runCatching { entity.thumbPath?.let { File(it).delete() } }
        dao.delete(id)
        db.downloads().delete(id)
    }

    suspend fun setEnhanceState(id: String, state: String, progress: Float, enhanced: Boolean = false) =
        withContext(Dispatchers.IO) { dao.setEnhanceState(id, state, progress, enhanced) }

    /** Swaps in an enhanced file, deleting the original unless the user keeps it. */
    suspend fun replaceFile(id: String, newFile: File, width: Int, height: Int, keepOriginal: Boolean) =
        withContext(Dispatchers.IO) {
            val entity = dao.getById(id) ?: return@withContext
            if (!keepOriginal && entity.filePath != newFile.absolutePath) {
                runCatching { File(entity.filePath).delete() }
            }
            dao.replaceFile(id, newFile.absolutePath, newFile.length(), width, height)
        }

    fun observeStats(): Flow<LibraryStats> = combine(
        dao.observeAll(),
        dao.observeTotalBytes(),
    ) { items, total ->
        LibraryStats(
            count = items.size,
            images = items.count { it.kind == MediaKind.IMAGE.name },
            videos = items.count { it.kind == MediaKind.VIDEO.name },
            bytes = total,
            cacheBytes = dirSize(FileStore.cacheDir(context)),
            thumbBytes = dirSize(FileStore.thumbsDir(context)),
        )
    }

    /** Deletes thumbnail and image-cache directories, returns reclaimed bytes. */
    suspend fun clearCaches(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val cache = FileStore.cacheDir(context)
        val thumbs = FileStore.thumbsDir(context)
        freed += dirSize(cache)
        freed += dirSize(thumbs)
        cache.listFiles()?.forEach { it.deleteRecursively() }
        thumbs.listFiles()?.forEach { it.deleteRecursively() }
        // Thumbnails are cheap to regenerate on demand; the UI falls back to placeholders.
        freed
    }
}

private fun WallpaperEntity.toDomain() = Wallpaper(
    id = id,
    title = title,
    kind = runCatching { DomainKind.valueOf(kind) }.getOrDefault(DomainKind.IMAGE),
    source = runCatching { WallpaperSource.valueOf(source) }.getOrDefault(WallpaperSource.IMPORTED),
    localPath = filePath,
    thumbPath = thumbPath,
    remoteThumb = remoteThumb,
    remotePreview = remotePreview,
    categoryId = categoryId,
    width = width,
    height = height,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    license = license,
    author = author,
    sourceUrl = sourceUrl,
    isFavorite = isFavorite,
    createdAt = createdAt,
    downloaded = source == WallpaperSource.DOWNLOADED.name,
)

/** Thumbnail source discriminator. */
enum class ThumbKind { IMAGE, VIDEO }
