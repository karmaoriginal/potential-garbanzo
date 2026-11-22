package app.lumira.wallpapers.enhance

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lumira.wallpapers.LumiraApp
import app.lumira.wallpapers.data.local.LumiraDatabase
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.data.repo.WallpaperRepository
import app.lumira.wallpapers.domain.model.QualityPreset
import app.lumira.wallpapers.util.FileStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background enhancement job.
 *
 * Constraints are set by the scheduler (charging + unmetered network optional), so the
 * job never fights the user for CPU while on battery. Progress is written to WorkInfo
 * (observed by the UI) and mirrored into Room so it survives process death.
 */
class EnhanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.enhanceProgress(
            context = applicationContext,
            title = inputData.getString(KEY_TITLE) ?: "Mejorando video",
            progress = 0f,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Wallpaper"
        val presetName = inputData.getString(KEY_PRESET) ?: QualityPreset.HD_1080.name
        val preset = runCatching { QualityPreset.valueOf(presetName) }
            .getOrDefault(QualityPreset.HD_1080)

        val db = LumiraDatabase.build(applicationContext)
        val repo = WallpaperRepository(applicationContext, db)
        val settings = SettingsRepository(applicationContext).settings.first()
        val entity = repo.getEntity(id) ?: return Result.failure()

        try {
            setForeground(getForegroundInfo())
        } catch (t: Throwable) {
            // Foreground service may be restricted; the job still runs.
        }

        val input = File(entity.filePath)
        if (!input.exists()) {
            repo.setEnhanceState(id, "FAILED", 0f)
            return Result.failure()
        }

        val outExt = if (input.extension.lowercase() in setOf("mp4", "m4v")) "mp4" else "mp4"
        val output = FileStore.exportFile(applicationContext, "enhanced_$id.$outExt")
        val canceled = AtomicBoolean(false)

        repo.setEnhanceState(id, "RUNNING", 0f)
        val result: kotlin.Result<EnhanceResult> = try {
            VideoEnhancer(applicationContext).enhance(
                input = input,
                output = output,
                preset = preset,
                cancelFlag = canceled,
                listener = object : VideoEnhancer.Listener {
                    override fun onProgress(fraction: Float) {
                        setProgressAsync(workDataOf(KEY_PROGRESS to fraction))
                    }

                    override fun onLog(line: String) = Unit
                },
                useAi = settings.aiUpscale,
            )
        } catch (t: Throwable) {
            kotlin.Result.failure(t)
        }

        return when {
            isStopped || canceled.get() -> {
                output.delete()
                repo.setEnhanceState(id, "CANCELED", 0f)
                Result.failure()
            }

            result.isSuccess -> {
                val r = result.getOrThrow()
                // Move the enhanced file into permanent storage before swapping it in.
                val permanent = FileStore.mediaFile(applicationContext, "enhanced_$id.mp4")
                r.output.copyTo(permanent, overwrite = true)
                r.output.delete()
                repo.replaceFile(
                    id = id,
                    newFile = permanent,
                    width = r.width,
                    height = r.height,
                    keepOriginal = settings.keepOriginal,
                )
                repo.setEnhanceState(id, "DONE", 1f, enhanced = true)
                Notifications.enhanceDone(applicationContext, title)
                Result.success(
                    workDataOf(
                        KEY_PROGRESS to 1f,
                        KEY_SIZE_AFTER to permanent.length(),
                        KEY_WIDTH to r.width,
                        KEY_HEIGHT to r.height,
                    ),
                )
            }

            else -> {
                output.delete()
                repo.setEnhanceState(id, "FAILED", 0f)
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR, result.exceptionOrNull()?.message ?: "Error")
                        .build(),
                )
            }
        }
    }

    companion object {
        const val KEY_ID = "wallpaper_id"
        const val KEY_TITLE = "title"
        const val KEY_PRESET = "preset"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_SIZE_AFTER = "size_after"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val NOTIFICATION_ID = 4201
        const val UNIQUE_PREFIX = "enhance_"
    }
}
