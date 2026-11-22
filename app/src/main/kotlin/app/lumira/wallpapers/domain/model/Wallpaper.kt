package app.lumira.wallpapers.domain.model

/** Kind of media a wallpaper uses. */
enum class MediaKind { IMAGE, VIDEO }

/** Where a wallpaper came from. */
enum class WallpaperSource { IMPORTED, DOWNLOADED, SAMPLE }

/**
 * Where the user intends to apply a wallpaper.
 * [BOTH] applies the same image to home and lock screen.
 */
enum class ApplyTarget { HOME, LOCK, BOTH }

/** Destination for enhanced/transcoded video output. */
enum class QualityPreset(val label: String, val shortSide: Int) {
    NONE("Original", 0),
    HD_1080("1080p", 1080),
    QHD_1440("1440p", 1440),
    ;

    companion object {
        fun fromShortSide(v: Int): QualityPreset = when {
            v <= 0 -> NONE
            v <= HD_1080.shortSide -> HD_1080
            else -> QHD_1440
        }
    }
}

/**
 * A wallpaper as the UI understands it, independent of Room/catalog shape.
 *
 * @param localPath absolute path of the stored media file (null for catalog-only items)
 * @param thumbPath absolute path of the local thumbnail (null -> use [remoteThumb])
 */
data class Wallpaper(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val source: WallpaperSource,
    val localPath: String? = null,
    val thumbPath: String? = null,
    val remoteThumb: String? = null,
    val remotePreview: String? = null,
    val categoryId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val mimeType: String? = null,
    val license: String? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0,
    val downloaded: Boolean = false,
    val mutable: Boolean = true,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0.6f
}
