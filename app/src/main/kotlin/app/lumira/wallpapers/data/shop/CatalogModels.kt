package app.lumira.wallpapers.data.shop

import kotlinx.serialization.Serializable

/**
 * Root of `catalog.json`, published to GitHub Pages.
 *
 * Versioning contract: bump [version] whenever items change so clients can show a
 * "new content" hint and invalidate their FTS index cheaply.
 */
@Serializable
data class Catalog(
    val version: Int = 1,
    val updatedAt: String = "",
    val baseUrl: String = "",
    val categories: List<CatalogCategory> = emptyList(),
    val items: List<CatalogItem> = emptyList(),
)

@Serializable
data class CatalogCategory(
    val id: String,
    val name: String,
    val icon: String = "sparkle",
    val color: String = "#8B7BFF",
)

/**
 * One wallpaper in the catalog.
 *
 * Only metadata + tiny thumbnails live in the repo; heavy files are hosted on
 * GitHub Releases (or any static host). [full] may be relative to [Catalog.baseUrl].
 */
@Serializable
data class CatalogItem(
    val id: String,
    val title: String,
    val category: String,
    val kind: String = "image",          // image | video
    val thumb: String = "",              // ~30 KB WebP, served from Pages
    val preview: String = "",            // short muted MP4/WebM clip (videos only)
    val full: String = "",               // heavy asset (Release asset URL)
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val bytes: Long = 0,
    val tags: List<String> = emptyList(),
    val license: String = "CC0",
    val author: String = "",
    val sourceUrl: String = "",
    val popularity: Int = 0,
    val addedAt: Long = 0,
    val blurHash: String = "",
) {
    val isVideo: Boolean get() = kind.equals("video", ignoreCase = true)

    fun resolve(url: String, base: String): String = when {
        url.isBlank() -> ""
        url.startsWith("http") -> url
        base.isBlank() -> url
        else -> base.trimEnd('/') + "/" + url.trimStart('/')
    }

    fun thumbUrl(base: String) = resolve(thumb, base)
    fun previewUrl(base: String) = resolve(preview, base)
    fun fullUrl(base: String) = resolve(full, base)
}
