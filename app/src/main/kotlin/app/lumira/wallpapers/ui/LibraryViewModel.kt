package app.lumira.wallpapers.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.domain.model.LibraryStats
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.wallpaper.ApplyResult
import app.lumira.wallpapers.wallpaper.WallpaperApplier
import app.lumira.wallpapers.domain.model.ApplyTarget
import app.lumira.wallpapers.domain.model.QualityPreset
import app.lumira.wallpapers.enhance.VideoEnhancer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import app.lumira.wallpapers.domain.model.MediaKind

/** Library tab selection. */
enum class LibraryFilter(val label: String) {
    ALL("Todo"),
    IMAGES("Imágenes"),
    VIDEOS("Videos"),
    FAVORITES("Favoritos"),
}

/** One-shot UI messages (snackbars) and navigation effects. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object OpenLiveWallpaperChooser : UiEvent
}

data class LibraryUiState(
    val items: List<Wallpaper> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val stats: LibraryStats = LibraryStats(),
    val loading: Boolean = true,
)

/**
 * Owns the local library: imports, apply actions, favorites, deletion and
 * enhancement jobs. Exposes a single [state] flow composed from Room.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.wallpapers(app)
    private val scheduler = ServiceLocator.scheduler(app)

    private val filter = MutableStateFlow(LibraryFilter.ALL)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val state: StateFlow<LibraryUiState> = combine(
        repo.observeAll(),
        filter,
        repo.observeStats(),
    ) { all, currentFilter, stats ->
        val filtered = when (currentFilter) {
            LibraryFilter.ALL -> all
            LibraryFilter.IMAGES -> all.filter { it.kind == MediaKind.IMAGE }
            LibraryFilter.VIDEOS -> all.filter { it.kind == MediaKind.VIDEO }
            LibraryFilter.FAVORITES -> all.filter { it.isFavorite }
        }
        LibraryUiState(filtered, currentFilter, stats, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun import(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = repo.importFromUri(uri)
            result.onSuccess { wp ->
                _events.emit(UiEvent.Message("Añadido: ${wp.title}"))
                val settings = ServiceLocator.settings(getApplication()).settings
                // Auto-enhance only when the user asked for it and the source is weak.
                if (wp.kind == MediaKind.VIDEO) {
                    val s = settings.first()
                    val lowQuality = wp.width < 1080 || wp.height < 1080
                    if (s.autoEnhance && (lowQuality || s.qualityPreset != QualityPreset.NONE)) {
                        scheduleEnhance(wp, s.qualityPreset)
                    }
                }
            }.onFailure { error ->
                _events.emit(UiEvent.Message(error.message ?: "No se pudo importar"))
            }
        }
    }

    fun toggleFavorite(item: Wallpaper) = viewModelScope.launch {
        repo.toggleFavorite(item.id)
    }

    fun rename(item: Wallpaper, newTitle: String) = viewModelScope.launch {
        repo.rename(item.id, newTitle)
        _events.emit(UiEvent.Message("Renombrado"))
    }

    fun delete(item: Wallpaper) = viewModelScope.launch {
        repo.delete(item.id)
        scheduler.cancel(item.id)
        _events.emit(UiEvent.Message("Eliminado"))
    }

    fun applyImage(item: Wallpaper, target: ApplyTarget, focusX: Float = 0.5f, focusY: Float = 0.5f) {
        val path = item.localPath ?: return
        viewModelScope.launch {
            when (
                val result = WallpaperApplier.applyImage(
                    getApplication(),
                    File(path),
                    target,
                    focusX,
                    focusY,
                )
            ) {
                ApplyResult.Success -> {
                    repo.bumpApply(item.id)
                    _events.emit(UiEvent.Message("Wallpaper aplicado"))
                }
                is ApplyResult.Failure ->
                    _events.emit(UiEvent.Message("Error: ${result.error}"))
                ApplyResult.LiveWallpaperOpened -> Unit
            }
        }
    }

    fun applyVideo(item: Wallpaper) {
        val path = item.localPath ?: return
        when (
            val result = WallpaperApplier.applyVideo(
                context = getApplication(),
                uri = android.net.Uri.fromFile(File(path)),
                title = item.title,
            )
        ) {
            ApplyResult.LiveWallpaperOpened -> {
                viewModelScope.launch {
                    repo.bumpApply(item.id)
                    _events.emit(UiEvent.OpenLiveWallpaperChooser)
                }
            }
            is ApplyResult.Failure -> viewModelScope.launch {
                _events.emit(UiEvent.Message("Error: ${result.error}"))
            }
            ApplyResult.Success -> Unit
        }
    }

    fun scheduleEnhance(item: Wallpaper, preset: QualityPreset) = viewModelScope.launch {
        val context: Context = getApplication()
        if (!VideoEnhancer.available(context)) {
            _events.emit(
                UiEvent.Message("La mejora de video no está disponible en este dispositivo"),
            )
            return@launch
        }
        val settings = ServiceLocator.settings(context).settings.first()
        scheduler.enqueue(item.id, item.title, preset, wifiOnly = false, chargingOnly = true)
        _events.emit(
            UiEvent.Message(
                if (settings.keepOriginal) {
                    "Mejora programada (se conserva el original)"
                } else {
                    "Mejora programada: se ejecutará al cargar"
                },
            ),
        )
    }

    fun cancelEnhance(item: Wallpaper) = viewModelScope.launch {
        scheduler.cancel(item.id)
        _events.emit(UiEvent.Message("Mejora cancelada"))
    }

    fun clearCaches() = viewModelScope.launch {
        val freed = repo.clearCaches()
        _events.emit(UiEvent.Message("Liberados ${app.lumira.wallpapers.ui.components.humanBytes(freed)}"))
    }
}

