package app.lumira.wallpapers.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lumira.wallpapers.LumiraApp
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.data.repo.WallpaperRepository
import app.lumira.wallpapers.di.ServiceLocator
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Housekeeping that keeps the app's footprint honest:
 *
 *  - trims the Coil disk cache to the user's configured limit
 *  - deletes thumbnails that no longer belong to a wallpaper
 *  - removes orphaned `.part` files from aborted downloads
 *  - prunes search history and stale shop signals
 *
 * Runs at most once a day, only when the device is idle-ish (not low battery), so it
 * never competes with the user. Zero background work otherwise.
 */
class CacheWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsRepository(context).settings.first()
        if (!settings.autoCleanCache) return Result.success()

        val repo = ServiceLocator.wallpapers(context)
        val db = ServiceLocator.database(context)

        // 1. Trim the image cache down to the configured budget (oldest files first).
        val cacheDir = File(context.cacheDir, "images")
        trimDirectory(cacheDir, settings.cacheLimitMb.toLong() * 1024 * 1024)

        // 2. Remove thumbnails whose wallpaper is gone.
        val thumbsDir = File(context.filesDir, "thumbs")
        val known = db.wallpapers().allThumbPaths().filterNotNull().map { File(it).name }.toSet()
        thumbsDir.listFiles()?.forEach { file ->
            if (file.name !in known) runCatching { file.delete() }
        }

        // 3. Drop abandoned partial downloads (older than 7 days).
        val staleCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        File(context.filesDir, "downloads").listFiles()?.forEach { file ->
            if (file.lastModified() < staleCutoff) runCatching { file.delete() }
        }

        // 4. Keep the cache directory bounded overall.
        repo.clearCaches()

        return Result.success()
    }

    private fun trimDirectory(dir: File, maxBytes: Long) {
        if (!dir.exists()) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            runCatching { file.delete() }
        }
    }

    companion object {
        private const val UNIQUE = "lumira_cache_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CacheWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .addTag("lumira")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** Called once from [LumiraApp]; keeps the wiring in one obvious place. */
fun bootstrapBackgroundWork(context: Context) {
    CacheWorker.schedule(context)
    @Suppress("UNUSED_EXPRESSION")
    LumiraApp.instance
}
