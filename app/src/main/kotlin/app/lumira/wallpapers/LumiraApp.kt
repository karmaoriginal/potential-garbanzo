package app.lumira.wallpapers

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Application entry point.
 *
 * Owns a single process-wide [ImageLoader] so every screen shares one memory and
 * disk cache (bounded, so the process never grows without limit).
 */
class LumiraApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            // 25% of the app's available heap, capped at 96 MB.
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .components {
            add(ImageDecoderDecoder.Factory()) // animated GIF/WEBP/AVIF on API 28+
            add(GifDecoder.Factory())          // animated GIF fallback
            add(VideoFrameDecoder.Factory())   // video thumbnails
        }
        .respectCacheHeaders(false)
        .crossfade(180)
        .build()

    companion object {
        lateinit var instance: LumiraApp
            private set
    }
}
