package app.lumira.wallpapers.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WallpaperEntity::class,
        DownloadEntity::class,
        ShopSignalEntity::class,
        CatalogFtsEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LumiraDatabase : RoomDatabase() {
    abstract fun wallpapers(): WallpaperDao
    abstract fun downloads(): DownloadDao
    abstract fun shopSignals(): ShopSignalDao
    abstract fun catalogFts(): CatalogFtsDao
    abstract fun searchHistory(): SearchHistoryDao

    companion object {
        private const val NAME = "lumira.db"

        /** Placeholder for the first schema bump; keeps migration tooling honest. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        fun build(context: Context): LumiraDatabase =
            Room.databaseBuilder(context, LumiraDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
