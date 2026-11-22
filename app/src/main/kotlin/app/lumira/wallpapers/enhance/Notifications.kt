package app.lumira.wallpapers.enhance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.lumira.wallpapers.MainActivity
import app.lumira.wallpapers.R

/** Notification channels and builders for foreground enhancement jobs. */
object Notifications {

    const val CHANNEL_ENHANCE = "enhance"
    const val CHANNEL_DOWNLOAD = "downloads"
    const val DOWNLOAD_NOTIFICATION_ID = 4202

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ENHANCE,
                "Mejora de video",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Progreso del escalado y recodificación" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Descargas de la tienda" },
        )
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun enhanceProgress(context: Context, title: String, progress: Float): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ENHANCE)
            .setSmallIcon(R.drawable.ic_stat_lumira)
            .setContentTitle("Mejorando en segundo plano")
            .setContentText(title)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), progress <= 0f)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))
            .build()
    }

    fun enhanceDone(context: Context, title: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ENHANCE)
            .setSmallIcon(R.drawable.ic_stat_lumira)
            .setContentTitle("Video mejorado")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        }
    }

    fun downloadProgress(context: Context, title: String, progress: Int): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_lumira)
            .setContentTitle("Descargando")
            .setContentText(title)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))
            .build()
    }
}
