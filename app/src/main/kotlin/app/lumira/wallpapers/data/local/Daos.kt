package app.lumira.wallpapers.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A wallpaper stored on device (imported from the picker or downloaded from the shop).
 *
 * [id] is a stable slug: `local_<uuid>` for imports, `catalog_<catalogId>` for downloads.
 */
@Entity(
    tableName = "wallpapers",
    indices = [
        Index("kind"),
        Index("source"),
        Index("categoryId"),
        Index("createdAt"),
    ],
)
data class WallpaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val source: String,
    val filePath: String,
    val thumbPath: String? = null,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val categoryId: String? = null,
    val remoteThumb: String? = null,
    val remotePreview: String? = null,
    val remoteFull: String? = null,
    val license: String? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
    val isFavorite: Boolean = false,
    val applyCount: Int = 0,
    /** Enhancement pipeline state: NONE, QUEUED, RUNNING, DONE, FAILED, CANCELED */
    val enhanceState: String = "NONE",
    /** 0..1 progress for the enhancement job, useful to restore UI after process death. */
    val enhanceProgress: Float = 0f,
    /** True when the file was produced by the enhancer (so we can report savings). */
    val enhanced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/** Shop item the user pulled down, tracked so downloads can resume and be cleaned. */
@Entity(tableName = "downloads", indices = [Index(value = ["catalogId"], unique = true)])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val catalogId: String,
    val title: String,
    val kind: String,
    val thumbnailUrl: String?,
    val downloadUrl: String,
    val bytesTotal: Long = 0,
    val bytesDownloaded: Long = 0,
    val status: String = "PENDING",
    val etag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Favorites and view counters for catalog items that are NOT downloaded yet. */
@Entity(tableName = "shop_signals")
data class ShopSignalEntity(
    @PrimaryKey val catalogId: String,
    val favorite: Boolean = false,
    val views: Int = 0,
    val lastSeenAt: Long = 0,
)

/** Recently searched queries, used for suggestion chips. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedAt: Long = System.currentTimeMillis(),
)

/**
 * Full-text search over catalog metadata (title, tags, category).
 * Kept separate from the network cache so search is instant, offline and cheap.
 */
@Fts4
@Entity(tableName = "catalog_fts")
data class CatalogFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val catalogId: String,
    val title: String,
    val tags: String,
    val category: String,
)

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE kind = :kind ORDER BY createdAt DESC")
    fun observeByKind(kind: String): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE source = :source ORDER BY createdAt DESC")
    fun observeBySource(source: String): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<WallpaperEntity?>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM wallpapers")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM wallpapers")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(item: WallpaperEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<WallpaperEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: WallpaperEntity): Long

    @Query("UPDATE wallpapers SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE wallpapers SET applyCount = applyCount + 1 WHERE id = :id")
    suspend fun bumpApply(id: String)

    @Query("UPDATE wallpapers SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE wallpapers SET enhanceState = :state, enhanceProgress = :progress, " +
            "enhanced = :enhanced WHERE id = :id",
    )
    suspend fun setEnhanceState(id: String, state: String, progress: Float, enhanced: Boolean)

    @Query("UPDATE wallpapers SET filePath = :path, sizeBytes = :bytes, width = :w, height = :h, enhanced = 1 WHERE id = :id")
    suspend fun replaceFile(id: String, path: String, bytes: Long, w: Int, h: Int)

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT filePath FROM wallpapers")
    suspend fun allFilePaths(): List<String>

    @Query("SELECT thumbPath FROM wallpapers")
    suspend fun allThumbPaths(): List<String>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status != 'DONE'")
    suspend fun pending(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}

@Dao
interface ShopSignalDao {
    @Query("SELECT * FROM shop_signals")
    fun observeAll(): Flow<List<ShopSignalEntity>>

    @Upsert
    suspend fun upsert(item: ShopSignalEntity)

    @Query("SELECT * FROM shop_signals WHERE catalogId = :id LIMIT 1")
    suspend fun get(id: String): ShopSignalEntity?

    @Query("UPDATE shop_signals SET favorite = NOT favorite WHERE catalogId = :id")
    suspend fun toggleFavorite(id: String)

    @Query("DELETE FROM shop_signals WHERE catalogId = :id")
    suspend fun delete(id: String)
}

@Dao
interface CatalogFtsDao {
    @Query("SELECT catalogId FROM catalog_fts WHERE catalog_fts MATCH :query LIMIT 200")
    suspend fun search(query: String): List<String>

    @Query("SELECT COUNT(*) FROM catalog_fts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogFtsEntity>)

    @Query("DELETE FROM catalog_fts")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY lastUsedAt DESC LIMIT 12")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun remove(query: String)
}
