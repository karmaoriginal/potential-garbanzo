package app.lumira.wallpapers.di

import android.content.Context
import app.lumira.wallpapers.LumiraApp
import app.lumira.wallpapers.data.local.LumiraDatabase
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.data.repo.WallpaperRepository
import app.lumira.wallpapers.data.shop.CatalogRepository
import app.lumira.wallpapers.data.shop.Downloader
import app.lumira.wallpapers.enhance.EnhanceScheduler

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would add hundreds of methods and a kapt/ksp step for an app of this
 * size, so everything is constructed lazily here and shared app-wide.
 */
object ServiceLocator {

    @Volatile private var database: LumiraDatabase? = null
    @Volatile private var settings: SettingsRepository? = null
    @Volatile private var wallpapers: WallpaperRepository? = null
    @Volatile private var catalog: CatalogRepository? = null
    @Volatile private var downloader: Downloader? = null
    @Volatile private var scheduler: EnhanceScheduler? = null

    fun database(context: Context): LumiraDatabase =
        database ?: synchronized(this) {
            database ?: LumiraDatabase.build(context.applicationContext).also { database = it }
        }

    fun settings(context: Context): SettingsRepository =
        settings ?: synchronized(this) {
            settings ?: SettingsRepository(context.applicationContext).also { settings = it }
        }

    fun wallpapers(context: Context): WallpaperRepository =
        wallpapers ?: synchronized(this) {
            wallpapers ?: WallpaperRepository(
                context.applicationContext,
                database(context),
            ).also { wallpapers = it }
        }

    fun catalog(context: Context): CatalogRepository =
        catalog ?: synchronized(this) {
            catalog ?: CatalogRepository(
                context.applicationContext,
                database(context),
                settings(context),
            ).also { catalog = it }
        }

    fun downloader(context: Context): Downloader =
        downloader ?: synchronized(this) {
            downloader ?: Downloader(
                context.applicationContext,
                database(context),
                wallpapers(context),
            ).also { downloader = it }
        }

    fun scheduler(context: Context): EnhanceScheduler =
        scheduler ?: synchronized(this) {
            scheduler ?: EnhanceScheduler(context.applicationContext).also { scheduler = it }
        }

    fun from(context: Context): ServiceLocator {
        // Ensures the Application is initialised in unusual entry points
        // (wallpaper service process, workers).
        @Suppress("UNUSED_EXPRESSION")
        LumiraApp.instance
        return this
    }
}
