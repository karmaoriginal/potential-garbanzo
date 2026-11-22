package app.lumira.wallpapers.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import app.lumira.wallpapers.data.prefs.Settings
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.data.wallpaper.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ExoPlayer-backed live wallpaper.
 *
 * Battery rules:
 *  - pauses when the screen turns off (ACTION_SCREEN_OFF)
 *  - pauses when the launcher is not visible (onVisibilityChanged)
 *  - pauses when the system enters battery saver, if the user enabled it
 *  - optional frame cap (30/60 fps): when capping, the player keeps decoding but the
 *    engine detaches/reattaches at most [Settings.liveWallpaperFps] times per second
 *  - the player is released on surface destruction/engine destroy (no leaks)
 *
 * Smooth looping is achieved with REPEAT_MODE_ONE: ExoPlayer loops the same media
 * item in place, avoiding a seek-to-zero black frame.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val handler = Handler(Looper.getMainLooper())
        private val bgPaint = Paint().apply { color = Color.BLACK }

        private var player: ExoPlayer? = null
        private var surfaceHolder: SurfaceHolder? = null
        private var videoSize = VideoSize.UNKNOWN
        private var visible = false
        private var screenOn = true
        private var powerSave = false
        private var ready = false

        private var settings: Settings = Settings()
        private var frameCapMs = 0L
        private var lastAttachAt = 0L

        private val capTick = object : Runnable {
            override fun run() {
                val p = player ?: return
                val now = System.nanoTime() / 1_000_000L
                if (now - lastAttachAt >= frameCapMs) {
                    lastAttachAt = now
                    val surface = surfaceHolder?.surface
                    runCatching {
                        p.setVideoSurface(null)
                        p.setVideoSurface(surface)
                    }
                }
                if (visible && screenOn && frameCapMs > 0L) handler.postDelayed(this, frameCapMs)
            }
        }

        private val systemReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        applyPlaybackState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        applyPlaybackState()
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        powerSave = isPowerSaveMode()
                        applyPlaybackState()
                    }
                }
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(false)
            setTouchEventsEnabled(false)
            surfaceHolder = holder
            screenOn = isScreenOn()
            powerSave = isPowerSaveMode()

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            registerReceiver(systemReceiver, filter)

            scope.launch {
                settings = SettingsRepository(applicationContext).settings.first()
                frameCapMs = if (settings.liveWallpaperFps in 1..59) 1000L / settings.liveWallpaperFps else 0L
                startPlayback(holder)
            }
        }

        private fun startPlayback(holder: SurfaceHolder) {
            val source = WallpaperPrefs(applicationContext).currentVideoPath()
            if (source.isNullOrBlank()) {
                drawFallback(holder)
                return
            }
            releasePlayer()
            videoSize = VideoSize.UNKNOWN
            ready = false

            val exo = ExoPlayer.Builder(applicationContext).build()
            player = exo
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(source)))
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.volume = if (settings.liveWallpaperMuted) 0f else 1f
            exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            exo.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            exo.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }

                override fun onRenderedFirstFrame() {
                    ready = true
                    applyPlaybackState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Live wallpaper playback error", error)
                    drawFallback(holder)
                }
            })
            exo.setVideoSurface(holder.surface)
            exo.prepare()
            exo.playWhenReady = false
            applyPlaybackState()
        }

        private fun applyPlaybackState() {
            val p = player ?: return
            val shouldPlay = visible && screenOn &&
                !(powerSave && settings.liveWallpaperBatterySaverPause)
            if (shouldPlay) {
                p.playWhenReady = true
                if (frameCapMs > 0L) {
                    handler.removeCallbacks(capTick)
                    handler.postDelayed(capTick, frameCapMs)
                } else {
                    handler.removeCallbacks(capTick)
                }
            } else {
                p.playWhenReady = false
                handler.removeCallbacks(capTick)
            }
        }

        /** Latest frame remains on screen while paused, which is what we want. */
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) screenOn = isScreenOn()
            applyPlaybackState()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceHolder = holder
            player?.setVideoSurface(holder.surface)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            applyPlaybackState()
            releasePlayer()
            surfaceHolder = null
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            runCatching { unregisterReceiver(systemReceiver) }
            releasePlayer()
            scope.cancel()
        }

        private fun drawFallback(holder: SurfaceHolder) {
            val canvas: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                bgPaint.color = WallpaperPrefs(applicationContext).fallbackColor()
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bgPaint)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun releasePlayer() {
            player?.run {
                runCatching { setVideoSurface(null) }
                runCatching { stop() }
                runCatching { release() }
            }
            player = null
            ready = false
            handler.removeCallbacks(capTick)
        }

        private fun isScreenOn(): Boolean {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isInteractive
        }

        private fun isPowerSaveMode(): Boolean {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isPowerSaveMode
        }
    }

    companion object {
        private const val TAG = "LumiraLive"

        val VIDEO_SIZE_UNKNOWN = VideoSize.UNKNOWN

        fun component(context: Context): ComponentName =
            ComponentName(context, VideoWallpaperService::class.java)

        /** Opens the system live-wallpaper chooser pre-selected on this service. */
        fun openChooser(context: Context) {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (!ok) {
                runCatching {
                    context.startActivity(
                        Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}
