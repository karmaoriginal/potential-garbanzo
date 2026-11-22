package app.lumira.wallpapers.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumira.wallpapers.data.prefs.Settings
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.domain.model.QualityPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bridges the settings DataStore to a StateFlow and exposes typed setters. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: SettingsRepository = ServiceLocator.settings(app)

    val settings: StateFlow<Settings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setDynamicColor(value: Boolean) = update { repo.setDynamicColor(value) }
    fun setAmoled(value: Boolean) = update { repo.setAmoled(value) }
    fun setDarkMode(value: Settings.DarkMode) = update { repo.setDarkMode(value) }
    fun setGridColumns(value: Int) = update { repo.setGridColumns(value) }
    fun setLiveFps(value: Int) = update { repo.setLiveFps(value) }
    fun setLiveMuted(value: Boolean) = update { repo.setLiveMuted(value) }
    fun setPauseOffScreen(value: Boolean) = update { repo.setPauseOffScreen(value) }
    fun setPauseBattery(value: Boolean) = update { repo.setPauseBattery(value) }
    fun setQuality(value: QualityPreset) = update { repo.setQuality(value) }
    fun setAutoEnhance(value: Boolean) = update { repo.setAutoEnhance(value) }
    fun setKeepOriginal(value: Boolean) = update { repo.setKeepOriginal(value) }
    fun setAiUpscale(value: Boolean) = update { repo.setAiUpscale(value) }
    fun setWifiOnly(value: Boolean) = update { repo.setWifiOnly(value) }
    fun setAutoClean(value: Boolean) = update { repo.setAutoClean(value) }
    fun setCacheLimit(value: Int) = update { repo.setCacheLimit(value) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
