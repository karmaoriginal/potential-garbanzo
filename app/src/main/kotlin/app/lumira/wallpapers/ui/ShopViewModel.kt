package app.lumira.wallpapers.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumira.wallpapers.data.shop.CatalogItem
import app.lumira.wallpapers.data.shop.CatalogRepository
import app.lumira.wallpapers.data.shop.CatalogState
import app.lumira.wallpapers.data.shop.DownloadProgress
import app.lumira.wallpapers.data.shop.Downloader
import app.lumira.wallpapers.data.local.ShopSignalEntity
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.domain.model.MediaFilter
import app.lumira.wallpapers.domain.model.ShopSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShopUiState(
    val catalog: CatalogState = CatalogState.Idle,
    val items: List<CatalogItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val sort: ShopSort = ShopSort.NEWEST,
    val media: MediaFilter = MediaFilter.ALL,
    val favoritesOnly: Boolean = false,
    val signals: Map<String, ShopSignalEntity> = emptyMap(),
    val loading: Boolean = false,
    val downloads: Map<String, Float> = emptyMap(),
)

/**
 * Shop state: catalog loading, search, filtering, sorting and downloads.
 *
 * All filtering happens in memory over the (small) catalog, except text search which
 * is backed by the FTS table so it stays instant with thousands of items.
 */
class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val catalogRepo: CatalogRepository = ServiceLocator.catalog(app)
    private val downloader: Downloader = ServiceLocator.downloader(app)

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(ShopSort.NEWEST)
    private val media = MutableStateFlow(MediaFilter.ALL)
    private val favoritesOnly = MutableStateFlow(false)
    private val searchIds = MutableStateFlow<Set<String>?>(null)
    private val downloads = MutableStateFlow<Map<String, Float>>(emptyMap())

    private var searchJob: Job? = null

    private val filterKey = combine(query, category, sort, media) { q, c, s, m ->
        FilterKey(q, c, s, m)
    }
    private val searchAndDownloads = combine(searchIds, downloads) { ids, d -> ids to d }

    val state: StateFlow<ShopUiState> = combine(
        catalogRepo.state,
        catalogRepo.signals(),
        filterKey,
        favoritesOnly,
        searchAndDownloads,
    ) { catalogState, signals, key, favOnly, idsAndDownloads ->
        val ids = idsAndDownloads.first
        val dl = idsAndDownloads.second
        val all = (catalogState as? CatalogState.Ready)?.catalog?.items ?: emptyList()
        val filtered = all.asSequence()
            .filter { item ->
                key.category == null || item.category.equals(key.category, ignoreCase = true)
            }
            .filter { item ->
                when (key.media) {
                    MediaFilter.ALL -> true
                    MediaFilter.STATIC -> !item.isVideo
                    MediaFilter.ANIMATED -> item.isVideo
                }
            }
            .filter { item -> ids == null || item.id in ids }
            .filter { item -> !favOnly || signals[item.id]?.favorite == true }
            .sortedWith(
                when (key.sort) {
                    ShopSort.NEWEST -> compareByDescending { it.addedAt }
                    ShopSort.POPULAR -> compareByDescending { it.popularity }
                    ShopSort.TRENDING -> compareByDescending {
                        it.popularity + (signals[it.id]?.views ?: 0) * 3
                    }
                    ShopSort.NAME -> compareBy { it.title.lowercase() }
                },
            )
            .toList()

        ShopUiState(
            catalog = catalogState,
            items = filtered,
            categories = all.map { it.category }.distinct(),
            query = key.query,
            category = key.category,
            sort = key.sort,
            media = key.media,
            favoritesOnly = favOnly,
            signals = signals,
            loading = catalogState is CatalogState.Loading || catalogState is CatalogState.Idle,
            downloads = dl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    private data class FilterKey(
        val query: String,
        val category: String?,
        val sort: ShopSort,
        val media: MediaFilter,
    )

    fun load(force: Boolean = false) = viewModelScope.launch {
        catalogRepo.load(force)
    }

    fun setQuery(value: String) {
        query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            searchIds.value = null
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(180) // debounce typing
            val ids = catalogRepo.searchIds(value)
            searchIds.value = ids.toSet()
        }
    }

    fun commitSearch(value: String) = viewModelScope.launch { catalogRepo.recordSearch(value) }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun setSort(value: ShopSort) {
        sort.value = value
    }

    fun setMedia(value: MediaFilter) {
        media.value = value
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun toggleFavorite(item: CatalogItem) = viewModelScope.launch {
        catalogRepo.toggleFavorite(item.id)
    }

    fun addView(item: CatalogItem) = viewModelScope.launch { catalogRepo.addView(item.id) }

    fun download(item: CatalogItem) = viewModelScope.launch {
        val base = (catalogRepo.state.value as? CatalogState.Ready)?.catalog?.baseUrl ?: ""
        downloads.value = downloads.value + (item.id to 0f)
        val result = downloader.download(item, base, onProgress = { progress ->
            when (progress) {
                is DownloadProgress.Running ->
                    downloads.value = downloads.value + (item.id to progress.fraction)
                is DownloadProgress.Done ->
                    downloads.value = downloads.value - item.id
                is DownloadProgress.Failed ->
                    downloads.value = downloads.value - item.id
            }
        })
        if (result.isFailure) downloads.value = downloads.value - item.id
    }
}
