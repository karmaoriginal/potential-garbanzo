package app.lumira.wallpapers.data.shop

import android.content.Context
import android.util.Log
import app.lumira.wallpapers.LumiraApp
import app.lumira.wallpapers.data.local.DownloadEntity
import app.lumira.wallpapers.data.media.MediaProbe
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.data.repo.WallpaperRepository
import app.lumira.wallpapers.data.local.LumiraDatabase
import app.lumira.wallpapers.data.local.WallpaperEntity
import app.lumira.wallpapers.domain.model.WallpaperSource
import app.lumira.wallpapers.util.FileStore
import app.lumira.wallpapers.util.ImageUtils
import app.lumira.wallpapers.data.repo.ThumbKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import app.lumira.wallpapers.domain.model.MediaKind

/** Progress event for a single download. */
sealed interface DownloadProgress {
    data class Running(val id: String, val fraction: Float, val bytes: Long) : DownloadProgress
    data class Done(val id: String) : DownloadProgress
    data class Failed(val id: String, val message: String) : DownloadProgress
}

/**
 * Resumable downloader.
 *
 * Files land in `downloads/<id>.part`; on retry the partial size is sent as a
 * `Range` header and the server's `206 Partial Content` is appended to the file,
 * so a flaky connection never restarts the whole asset. If the server ignores the
 * range (200), the file is truncated and the download restarts cleanly.
 */
class Downloader(
    private val context: Context,
    private val db: LumiraDatabase,
    private val wallpapers: WallpaperRepository,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun partDir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    @JvmOverloads
    suspend fun download(
        item: CatalogItem,
        baseUrl: String,
        onProgress: ((DownloadProgress) -> Unit)? = null,
        isWifiOnly: Boolean = true,
    ): kotlin.Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = SettingsRepository(context).settings.first()
            if (isWifiOnly && settings.wifiOnly && !NetworkMonitor.isUnmetered(context)) {
                error("Esperando Wi-Fi")
            }

            val url = item.fullUrl(baseUrl)
            require(url.isNotBlank()) { "El elemento no tiene archivo" }

            val entityId = "catalog_${item.id}"
            val existing = wallpapers.get(entityId)
            if (existing?.localPath != null && File(existing.localPath).exists()) {
                return@runCatching File(existing.localPath)
            }

            val ext = FileStore.extensionFor(null, File(url).name, if (item.isVideo) {
                app.lumira.wallpapers.domain.model.MediaKind.VIDEO
            } else {
                app.lumira.wallpapers.domain.model.MediaKind.IMAGE
            })
            val part = File(partDir(), "$entityId.$ext.part")
            val finalFile = FileStore.mediaFile(context, "$entityId.$ext")

            var downloaded = if (part.exists()) part.length() else 0L
            val requestBuilder = Request.Builder().url(url).get()
            if (downloaded > 0) requestBuilder.header("Range", "bytes=$downloaded-")

            val response = http.newCall(requestBuilder.build()).execute()
            response.use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val total = when (res.code) {
                    HttpURLConnection.HTTP_PARTIAL -> downloaded + (res.body?.contentLength() ?: -1)
                    else -> {
                        downloaded = 0L
                        res.body?.contentLength() ?: -1
                    }
                }
                val body = res.body ?: error("Sin contenido")

                db.downloads().upsert(
                    DownloadEntity(
                        id = entityId,
                        catalogId = item.id,
                        title = item.title,
                        kind = item.kind,
                        thumbnailUrl = item.thumbUrl(baseUrl),
                        downloadUrl = url,
                        bytesTotal = if (total > 0) total else item.bytes,
                        bytesDownloaded = downloaded,
                        status = "RUNNING",
                        etag = res.header("ETag"),
                    ),
                )

                RandomAccessFile(part, "rw").use { raf ->
                    if (downloaded > 0) raf.seek(downloaded) else raf.setLength(0)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            val now = System.nanoTime()
                            if (now - lastReport > 250_000_000L) {
                                lastReport = now
                                val fraction = if (total > 0) {
                                    downloaded.toFloat() / total.toFloat()
                                } else {
                                    0f
                                }
                                onProgress?.invoke(DownloadProgress.Running(entityId, fraction, downloaded))
                            }
                        }
                    }
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!part.renameTo(finalFile)) {
                part.copyTo(finalFile, overwrite = true)
                part.delete()
            }

            // Probe + thumbnail, then register in the library so it shows up instantly.
            val info = MediaProbe.probeFile(finalFile, null)
            val thumb = ImageUtils.createThumbnail(
                context = context,
                source = finalFile,
                kind = if (item.isVideo) ThumbKind.VIDEO else ThumbKind.IMAGE,
                outFile = FileStore.thumbFile(context, "$entityId.webp"),
            )
            val entity = WallpaperEntity(
                id = entityId,
                title = item.title,
                kind = if (item.isVideo) "VIDEO" else "IMAGE",
                source = WallpaperSource.DOWNLOADED.name,
                filePath = finalFile.absolutePath,
                thumbPath = thumb?.absolutePath,
                mimeType = info?.mimeType,
                width = info?.width ?: item.width,
                height = info?.height ?: item.height,
                durationMs = info?.durationMs ?: item.durationMs,
                sizeBytes = finalFile.length(),
                categoryId = item.category,
                remoteThumb = item.thumbUrl(baseUrl),
                remotePreview = item.previewUrl(baseUrl),
                license = item.license,
                author = item.author,
                sourceUrl = item.sourceUrl,
                createdAt = System.currentTimeMillis(),
            )
            db.wallpapers().upsert(entity)
            db.downloads().upsert(
                DownloadEntity(
                    id = entityId,
                    catalogId = item.id,
                    title = item.title,
                    kind = item.kind,
                    thumbnailUrl = item.thumbUrl(baseUrl),
                    downloadUrl = url,
                    bytesTotal = finalFile.length(),
                    bytesDownloaded = finalFile.length(),
                    status = "DONE",
                ),
            )
            onProgress?.invoke(DownloadProgress.Done(entityId))
            finalFile
        }.onFailure { t ->
            Log.w("LumiraDownload", "download failed for ${item.id}", t)
            onProgress?.invoke(DownloadProgress.Failed("catalog_${item.id}", t.message ?: "Error"))
        }
    }

    /** Bytes still on disk for partial downloads, surfaced in the storage screen. */
    fun partialBytes(): Long =
        partDir().listFiles()?.sumOf { it.length() } ?: 0L
}
