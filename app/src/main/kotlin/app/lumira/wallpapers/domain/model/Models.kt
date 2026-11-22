package app.lumira.wallpapers.domain.model

import app.lumira.wallpapers.data.prefs.Settings

/** App settings surfaced to the UI (thin alias so the domain stays stable). */
typealias LumiraSettings = Settings

/** Storage statistics for the library screen. */
data class LibraryStats(
    val count: Int = 0,
    val images: Int = 0,
    val videos: Int = 0,
    val bytes: Long = 0,
    val cacheBytes: Long = 0,
    val thumbBytes: Long = 0,
) {
    val totalBytes: Long get() = bytes + cacheBytes + thumbBytes
}

/** Progress of a long running enhancement job. */
data class JobProgress(
    val wallpaperId: String,
    val fraction: Float,
    val running: Boolean,
    val message: String? = null,
)
