package app.lumira.wallpapers.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lumira.wallpapers.domain.model.QualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Immutable snapshot of all user preferences. */
data class Settings(
    // Appearance
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val gridColumns: Int = 2,

    // Live wallpaper behaviour
    val liveWallpaperFps: Int = 60,
    val liveWallpaperMuted: Boolean = true,
    val liveWallpaperPauseOffScreen: Boolean = true,
    val liveWallpaperBatterySaverPause: Boolean = true,
    val liveWallpaperLoopSmooth: Boolean = true,

    // Quality / enhancement
    val qualityPreset: QualityPreset = QualityPreset.HD_1080,
    val autoEnhance: Boolean = false,
    val aiUpscale: Boolean = false,
    val keepOriginal: Boolean = false,

    // Network / storage
    val wifiOnly: Boolean = true,
    val cacheLimitMb: Int = 256,
    val autoCleanCache: Boolean = true,
    val downloadsSort: String = "NEWEST",

    // Shop
    val catalogUrl: String = DEFAULT_CATALOG_URL,
    val onboarded: Boolean = false,
) {
    enum class DarkMode { SYSTEM, LIGHT, DARK }

    companion object {
        /**
         * Canonical, version-agnostic URL of the catalog.
         *
         * `raw.githubusercontent.com` is used as the default because it works as soon as
         * the repository is public and never breaks when Pages is (re)deployed. If Pages
         * is enabled, `https://karmaoriginal.github.io/potential-garbanzo/catalog/catalog.json`
         * is an equivalent CDN-backed alternative (see README → Tienda).
         */
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/karmaoriginal/potential-garbanzo/main/docs/catalog/catalog.json"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumira_settings")

/** Typed, flow-based settings repository backed by DataStore Preferences. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            amoled = p[Keys.AMOLED] ?: false,
            darkMode = runCatching { Settings.DarkMode.valueOf(p[Keys.DARK_MODE] ?: "SYSTEM") }
                .getOrDefault(Settings.DarkMode.SYSTEM),
            gridColumns = p[Keys.GRID_COLUMNS] ?: 2,
            liveWallpaperFps = p[Keys.LW_FPS] ?: 60,
            liveWallpaperMuted = p[Keys.LW_MUTED] ?: true,
            liveWallpaperPauseOffScreen = p[Keys.LW_PAUSE_OFFSCREEN] ?: true,
            liveWallpaperBatterySaverPause = p[Keys.LW_PAUSE_BATTERY] ?: true,
            liveWallpaperLoopSmooth = p[Keys.LW_SMOOTH_LOOP] ?: true,
            qualityPreset = runCatching {
                QualityPreset.valueOf(p[Keys.QUALITY_PRESET] ?: QualityPreset.HD_1080.name)
            }.getOrDefault(QualityPreset.HD_1080),
            autoEnhance = p[Keys.AUTO_ENHANCE] ?: false,
            aiUpscale = p[Keys.AI_UPSCALE] ?: false,
            keepOriginal = p[Keys.KEEP_ORIGINAL] ?: false,
            wifiOnly = p[Keys.WIFI_ONLY] ?: true,
            cacheLimitMb = p[Keys.CACHE_LIMIT_MB] ?: 256,
            autoCleanCache = p[Keys.AUTO_CLEAN] ?: true,
            downloadsSort = p[Keys.DOWNLOADS_SORT] ?: "NEWEST",
            catalogUrl = p[Keys.CATALOG_URL] ?: Settings.DEFAULT_CATALOG_URL,
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setDynamicColor(value: Boolean) = put { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setAmoled(value: Boolean) = put { it[Keys.AMOLED] = value }
    suspend fun setDarkMode(value: Settings.DarkMode) = put { it[Keys.DARK_MODE] = value.name }
    suspend fun setGridColumns(value: Int) = put { it[Keys.GRID_COLUMNS] = value.coerceIn(2, 4) }
    suspend fun setLiveFps(value: Int) = put { it[Keys.LW_FPS] = if (value >= 60) 60 else 30 }
    suspend fun setLiveMuted(value: Boolean) = put { it[Keys.LW_MUTED] = value }
    suspend fun setPauseOffScreen(value: Boolean) = put { it[Keys.LW_PAUSE_OFFSCREEN] = value }
    suspend fun setPauseBattery(value: Boolean) = put { it[Keys.LW_PAUSE_BATTERY] = value }
    suspend fun setSmoothLoop(value: Boolean) = put { it[Keys.LW_SMOOTH_LOOP] = value }
    suspend fun setQuality(value: QualityPreset) = put { it[Keys.QUALITY_PRESET] = value.name }
    suspend fun setAutoEnhance(value: Boolean) = put { it[Keys.AUTO_ENHANCE] = value }
    suspend fun setAiUpscale(value: Boolean) = put { it[Keys.AI_UPSCALE] = value }
    suspend fun setKeepOriginal(value: Boolean) = put { it[Keys.KEEP_ORIGINAL] = value }
    suspend fun setWifiOnly(value: Boolean) = put { it[Keys.WIFI_ONLY] = value }
    suspend fun setCacheLimit(value: Int) = put { it[Keys.CACHE_LIMIT_MB] = value }
    suspend fun setAutoClean(value: Boolean) = put { it[Keys.AUTO_CLEAN] = value }
    suspend fun setDownloadsSort(value: String) = put { it[Keys.DOWNLOADS_SORT] = value }
    suspend fun setCatalogUrl(value: String) = put { it[Keys.CATALOG_URL] = value }
    suspend fun setOnboarded(value: Boolean) = put { it[Keys.ONBOARDED] = value }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val LW_FPS = intPreferencesKey("lw_fps")
        val LW_MUTED = booleanPreferencesKey("lw_muted")
        val LW_PAUSE_OFFSCREEN = booleanPreferencesKey("lw_pause_offscreen")
        val LW_PAUSE_BATTERY = booleanPreferencesKey("lw_pause_battery")
        val LW_SMOOTH_LOOP = booleanPreferencesKey("lw_smooth_loop")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val AUTO_ENHANCE = booleanPreferencesKey("auto_enhance")
        val AI_UPSCALE = booleanPreferencesKey("ai_upscale")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val AUTO_CLEAN = booleanPreferencesKey("auto_clean")
        val DOWNLOADS_SORT = stringPreferencesKey("downloads_sort")
        val CATALOG_URL = stringPreferencesKey("catalog_url")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val LAST_CLEAN = longPreferencesKey("last_clean")
    }
}
