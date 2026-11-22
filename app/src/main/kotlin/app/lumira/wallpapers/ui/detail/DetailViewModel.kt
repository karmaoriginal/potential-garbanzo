package app.lumira.wallpapers.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumira.wallpapers.domain.model.MediaKind
import app.lumira.wallpapers.data.shop.CatalogItem
import app.lumira.wallpapers.data.shop.CatalogState
import app.lumira.wallpapers.data.shop.DownloadProgress
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.domain.model.ApplyTarget
import app.lumira.wallpapers.domain.model.QualityPreset
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.domain.model.WallpaperSource
import app.lumira.wallpapers.wallpaper.ApplyResult
import app.lumira.wallpapers.wallpaper.WallpaperApplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DetailUiState(
    val item: Wallpaper? = null,
    val catalogItem: CatalogItem? = null,
    val loading: Boolean = true,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val progress: Float = 0f,
    val message: String? = null,
)

/**
 * Detail screen logic: resolves either a local wallpaper or a catalog entry,
 * handles download → apply → enhance.
 */
class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.wallpapers(app)
    private val catalog = ServiceLocator.catalog(app)
    private val downloader = ServiceLocator.downloader(app)
    private val scheduler = ServiceLocator.scheduler(app)

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var baseUrl: String = ""

    fun load(wallpaperId: String?, catalogId: String?) = viewModelScope.launch {
        if (wallpaperId != null) {
            val local = repo.get(wallpaperId)
            _state.update { it.copy(item = local, loading = false) }
            if (local != null) {
                val entity = repo.getEntity(local.id)
                _state.update { it.copy(progress = entity?.enhanceProgress ?: 0f) }
            }
            return@launch
        }
        if (catalogId != null) {
            val ready = catalog.state.value as? CatalogState.Ready
            val stateValue = ready ?: (catalog.load() as? CatalogState.Ready)
            val catalogData = (stateValue as? CatalogState.Ready)?.catalog
            baseUrl = catalogData?.baseUrl.orEmpty()
            val item = catalogData?.items?.firstOrNull { it.id == catalogId }
            val local = repo.get("catalog_$catalogId")
            _state.update {
                it.copy(
                    item = local ?: item?.toDomain(baseUrl),
                    catalogItem = item,
                    downloaded = local != null,
                    loading = false,
                )
            }
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val item = _state.value.item ?: return@launch
        if (item.downloaded) {
            repo.toggleFavorite(item.id)
        } else {
            catalog.toggleFavorite(item.id.removePrefix("catalog_"))
        }
        _state.update {
            it.copy(item = it.item?.copy(isFavorite = !it.item.isFavorite))
        }
    }

    fun applyImage(target: ApplyTarget, focusX: Float, focusY: Float) = viewModelScope.launch {
        val item = _state.value.item ?: return@launch
        val path = item.localPath ?: return@launch
        when (val result = WallpaperApplier.applyImage(getApplication(), File(path), target, focusX, focusY)) {
            ApplyResult.Success -> {
                repo.bumpApply(item.id)
                _state.update { it.copy(message = "Wallpaper aplicado ✓") }
            }
            is ApplyResult.Failure -> _state.update { it.copy(message = "Error: ${result.error}") }
            ApplyResult.LiveWallpaperOpened -> Unit
        }
    }

    fun applyVideo() = viewModelScope.launch {
        val item = _state.value.item ?: return@launch
        val path = item.localPath ?: return@launch
        when (val result = WallpaperApplier.applyVideo(getApplication(), Uri.fromFile(File(path)), item.title)) {
            ApplyResult.LiveWallpaperOpened -> {
                repo.bumpApply(item.id)
                _state.update { it.copy(message = "Elige Lumira en el selector del sistema") }
            }
            is ApplyResult.Failure -> _state.update { it.copy(message = "Error: ${result.error}") }
            ApplyResult.Success -> Unit
        }
    }

    fun download() = viewModelScope.launch {
        val item = _state.value.catalogItem ?: return@launch
        _state.update { it.copy(downloading = true, downloadProgress = 0f) }
        val result = downloader.download(item, baseUrl) { progress ->
            when (progress) {
                is DownloadProgress.Running ->
                    _state.update { it.copy(downloadProgress = progress.fraction) }
                is DownloadProgress.Done -> Unit
                is DownloadProgress.Failed ->
                    _state.update { it.copy(message = progress.message) }
            }
        }
        result.onSuccess { file ->
            val local = repo.get("catalog_${item.id}")
            _state.update {
                it.copy(
                    downloading = false,
                    downloadProgress = 1f,
                    downloaded = true,
                    item = local ?: it.item?.copy(localPath = file.absolutePath),
                    message = "Descargado ✓",
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(downloading = false, message = error.message) }
        }
    }

    fun enhance() = viewModelScope.launch {
        val item = _state.value.item ?: return@launch
        if (item.kind != MediaKind.VIDEO) return@launch
        scheduler.enqueue(item.id, item.title, QualityPreset.HD_1080)
        _state.update { it.copy(message = "Mejora programada (al cargar)") }
    }

    fun cancelEnhance() = viewModelScope.launch {
        val item = _state.value.item ?: return@launch
        scheduler.cancel(item.id)
        _state.update { it.copy(progress = 0f, message = "Mejora cancelada") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun CatalogItem.toDomain(base: String) = Wallpaper(
        id = "catalog_$id",
        title = title,
        kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
        source = WallpaperSource.DOWNLOADED,
        remoteThumb = thumbUrl(base),
        remotePreview = previewUrl(base),
        width = width,
        height = height,
        durationMs = durationMs,
        sizeBytes = bytes,
        license = license,
        author = author,
        sourceUrl = sourceUrl,
    )
}
