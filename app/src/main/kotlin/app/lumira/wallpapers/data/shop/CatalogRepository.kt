package app.lumira.wallpapers.data.shop

import android.content.Context
import app.lumira.wallpapers.data.local.CatalogFtsEntity
import app.lumira.wallpapers.data.local.LumiraDatabase
import app.lumira.wallpapers.data.local.SearchHistoryEntity
import app.lumira.wallpapers.data.local.ShopSignalEntity
import app.lumira.wallpapers.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** UI state of the catalog loader. */
sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Ready(val catalog: Catalog, val fromCache: Boolean) : CatalogState
    data class Error(val message: String) : CatalogState
}

/**
 * Loads and caches `catalog.json`.
 *
 * Strategy: serve the cached copy instantly (offline first), then refresh in the
 * background when the ETag changes. The catalog is also flattened into an FTS4
 * table so search works with zero network and zero latency.
 */
class CatalogRepository(
    private val context: Context,
    private val db: LumiraDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val cacheFile get() = File(context.filesDir, "catalog_cache.json")
    private val etagFile get() = File(context.filesDir, "catalog_cache.etag")

    suspend fun load(forceRefresh: Boolean = false): CatalogState = withContext(Dispatchers.IO) {
        val cached = readCache() ?: readBundled()
        if (cached != null && !forceRefresh) {
            _state.value = CatalogState.Ready(cached, fromCache = true)
            // Background refresh; failure keeps the cached copy visible.
            refresh(cached)
            return@withContext _state.value
        }
        _state.value = CatalogState.Loading
        val refreshed = refresh(cached)
        if (refreshed == null) indexForSearch(cached)
        _state.value = refreshed ?: cached?.let { CatalogState.Ready(it, fromCache = true) }
            ?: CatalogState.Error("No se pudo cargar el catálogo. Revisa tu conexión.")
        _state.value
    }

    private suspend fun refresh(current: Catalog?): CatalogState? = runCatching {
        val url = settingsRepository.settings.first().catalogUrl
        val builder = Request.Builder().url(url).get()
        val etag = etagFile.takeIf { it.exists() }?.readText()
        if (etag != null && current != null) builder.header("If-None-Match", etag)
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 304 && current != null) return@runCatching null
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Respuesta vacía")
            val catalog = json.decodeFromString<Catalog>(body)
            if (catalog.items.none { it.thumb.startsWith("data:") }) {
                cacheFile.writeText(body)
            }
            response.header("ETag")?.let { etagFile.writeText(it) }
            indexForSearch(catalog)
            CatalogState.Ready(catalog, fromCache = false)
        }
    }.getOrNull()

    private fun readCache(): Catalog? = runCatching {
        if (!cacheFile.exists()) return null
        json.decodeFromString<Catalog>(cacheFile.readText())
    }.getOrNull()

    /**
     * Offline seed shipped inside the APK (assets/catalog/bundled.json).
     *
     * It carries the same metadata as the published catalog but embeds tiny base64
     * thumbnails, so the shop is fully browsable on first launch without network or
     * without GitHub Pages being reachable. `full` URLs still point at GitHub Releases.
     */
    private fun readBundled(): Catalog? = runCatching {
        context.assets.open("catalog/bundled.json").use { stream ->
            json.decodeFromString<Catalog>(stream.readBytes().decodeToString())
        }
    }.getOrNull()

    private suspend fun indexForSearch(catalog: Catalog?) {
        if (catalog == null) return
        val dao = db.catalogFts()
        if (dao.count() == catalog.items.size) return
        dao.clear()
        dao.insertAll(
            catalog.items.mapIndexed { index, item ->
                CatalogFtsEntity(
                    rowId = index.toLong() + 1,
                    catalogId = item.id,
                    title = item.title,
                    tags = item.tags.joinToString(" "),
                    category = item.category,
                )
            },
        )
    }

    suspend fun searchIds(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val sanitized = query.trim().split(Regex("\\s+"))
            .joinToString(" ") { "$it*" }
            .replace(Regex("[^\\p{L}\\p{N}* ]"), "")
        runCatching { db.catalogFts().search(sanitized) }.getOrDefault(emptyList())
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) db.searchHistory().upsert(SearchHistoryEntity(query.trim()))
    }

    fun recentSearches(): Flow<List<String>> =
        db.searchHistory().observeRecent().map { list -> list.map { it.query } }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) { db.searchHistory().clear() }

    fun signals(): Flow<Map<String, ShopSignalEntity>> =
        db.shopSignals().observeAll().map { list -> list.associateBy { it.catalogId } }

    suspend fun toggleFavorite(catalogId: String) = withContext(Dispatchers.IO) {
        val existing = db.shopSignals().get(catalogId)
        if (existing == null) {
            db.shopSignals().upsert(ShopSignalEntity(catalogId, favorite = true))
        } else {
            db.shopSignals().toggleFavorite(catalogId)
        }
    }

    suspend fun addView(catalogId: String) = withContext(Dispatchers.IO) {
        val existing = db.shopSignals().get(catalogId)
        db.shopSignals().upsert(
            existing?.copy(views = existing.views + 1, lastSeenAt = System.currentTimeMillis())
                ?: ShopSignalEntity(catalogId, views = 1, lastSeenAt = System.currentTimeMillis()),
        )
    }
}

/** Convenience: combine catalog + signals for the UI. */
fun CatalogState.items(): List<CatalogItem> = (this as? CatalogState.Ready)?.catalog?.items ?: emptyList()

fun combineWithSignals(
    catalog: Flow<CatalogState>,
    signals: Flow<Map<String, ShopSignalEntity>>,
): Flow<Pair<List<CatalogItem>, Map<String, ShopSignalEntity>>> =
    combine(catalog, signals) { state, sig -> state.items() to sig }
