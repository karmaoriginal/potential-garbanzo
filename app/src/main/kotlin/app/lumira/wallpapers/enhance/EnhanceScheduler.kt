package app.lumira.wallpapers.enhance

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.lumira.wallpapers.domain.model.QualityPreset
import java.util.UUID

/**
 * Schedules one-time enhancement jobs.
 *
 *  - unique per wallpaper (re-enqueuing replaces the previous job)
 *  - requires the device to be charging by default, so a heavy transcode never eats
 *    the user's battery; Wi-Fi is only added when the user opts into cloud models
 *  - exposed as a suspend API mirroring the WorkManager flow
 */
class EnhanceScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    fun enqueue(
        wallpaperId: String,
        title: String,
        preset: QualityPreset,
        wifiOnly: Boolean = false,
        chargingOnly: Boolean = true,
    ): UUID {
        val constraints = Constraints.Builder()
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .apply { if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) }
            .build()

        val request = OneTimeWorkRequestBuilder<EnhanceWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    EnhanceWorker.KEY_ID to wallpaperId,
                    EnhanceWorker.KEY_TITLE to title,
                    EnhanceWorker.KEY_PRESET to preset.name,
                ),
            )
            .addTag(wallpaperId)
            .build()

        workManager.enqueueUniqueWork(
            EnhanceWorker.UNIQUE_PREFIX + wallpaperId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id
    }

    fun cancel(wallpaperId: String) {
        workManager.cancelUniqueWork(EnhanceWorker.UNIQUE_PREFIX + wallpaperId)
    }

    fun cancelAll() = workManager.cancelAllWorkByTag("lumira")

    fun observe(wallpaperId: String) =
        workManager.getWorkInfosForUniqueWorkFlow(EnhanceWorker.UNIQUE_PREFIX + wallpaperId)

    suspend fun progressOf(wallpaperId: String): Float {
        val infos = workManager
            .getWorkInfosForUniqueWork(EnhanceWorker.UNIQUE_PREFIX + wallpaperId)
            .get()
        val running = infos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        } ?: return 0f
        return running.progress.getFloat(EnhanceWorker.KEY_PROGRESS, 0f)
    }
}
