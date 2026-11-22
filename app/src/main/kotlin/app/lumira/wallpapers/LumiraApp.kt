package app.lumira.wallpapers

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import app.lumira.wallpapers.data.bootstrapBackgroundWork
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.enhance.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Application entry point.
 *
 * Responsibilities kept deliberately small:
 *  - one process-wide [ImageLoader] (bounded memory + disk cache)
 *  - background work bootstrap (cache housekeeping only)
 *  - apply the persisted notification channels
 */
class LumiraApp : Application(), ImageLoaderFactory, Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        bootstrapBackgroundWork(this)
        // Warm DataStore off the main thread so the first screen reads settings instantly.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ServiceLocator.settings(this@LumiraApp).settings.first() }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.22)
                .build()
        }
        .diskCache {
            // The default budget; CacheWorker trims it to the user's configured limit
            // daily, and Settings exposes the limit for manual cleanup.
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(DEFAULT_DISK_CACHE_BYTES)
                .build()
        }
        .components {
            add(ImageDecoderDecoder.Factory()) // animated GIF / WEBP / AVIF (API 28+)
            add(GifDecoder.Factory())          // animated GIF fallback
            add(VideoFrameDecoder.Factory())   // video thumbnails
        }
        .respectCacheHeaders(false)
        .crossfade(160)
        .build()

    /** WorkManager configuration: single-process, no custom initializer needed. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .setMaxSchedulerLimit(4)
            .build()

    companion object {
        private const val DEFAULT_DISK_CACHE_BYTES = 256L * 1024 * 1024

        lateinit var instance: LumiraApp
            private set

        /** Cache policy used everywhere: read from memory+disk, write to disk when online. */
        val cachePolicy: CachePolicy = CachePolicy.ENABLED

        val retention: Pair<Long, TimeUnit> = 30L to TimeUnit.DAYS
    }
}
