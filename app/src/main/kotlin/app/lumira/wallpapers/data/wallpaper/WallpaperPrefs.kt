package app.lumira.wallpapers.data.wallpaper

import android.content.Context
import androidx.core.content.edit

/**
 * Tiny SharedPreferences store shared between the app process and the
 * wallpaper service process (they may run in different processes).
 */
class WallpaperPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setCurrentVideo(path: String?, title: String?) = prefs.edit {
        putString(KEY_VIDEO, path)
        putString(KEY_TITLE, title)
    }

    fun currentVideoPath(): String? = prefs.getString(KEY_VIDEO, null)

    fun currentVideoTitle(): String? = prefs.getString(KEY_TITLE, null)

    fun setFallbackColor(color: Int) = prefs.edit { putInt(KEY_FALLBACK, color) }

    fun fallbackColor(): Int = prefs.getInt(KEY_FALLBACK, 0xFF000000.toInt())

    fun clear() = prefs.edit { clear() }

    companion object {
        private const val NAME = "lumira_wallpaper"
        private const val KEY_VIDEO = "current_video"
        private const val KEY_TITLE = "current_title"
        private const val KEY_FALLBACK = "fallback_color"
    }
}
