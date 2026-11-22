package app.lumira.wallpapers.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import app.lumira.wallpapers.domain.model.ApplyTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Result of an apply operation, used by the UI to show a snackbar. */
sealed interface ApplyResult {
    data object Success : ApplyResult
    data object LiveWallpaperOpened : ApplyResult
    data class Failure(val error: String) : ApplyResult
}

/**
 * Applies static images through [WallpaperManager].
 *
 * Supports HOME / LOCK / BOTH on API 24+, honouring the requested crop by
 * scaling and centering the bitmap on the target screen size instead of
 * crushing it.
 */
object WallpaperApplier {

    suspend fun applyImage(
        context: Context,
        file: File,
        target: ApplyTarget,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
        blurAmount: Int = 0,
        dimAmount: Int = 0,
    ): ApplyResult = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val raw = decodeForScreen(context, file) ?: error("No se pudo decodificar la imagen")
            val composed = compose(raw, context, focusX, focusY, blurAmount, dimAmount)
            when (target) {
                ApplyTarget.HOME -> wm.setBitmap(composed, null, true, WallpaperManager.FLAG_SYSTEM)
                ApplyTarget.LOCK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.setBitmap(composed, null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        wm.setBitmap(composed)
                    }
                }
                ApplyTarget.BOTH -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.setBitmap(
                            composed,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                        )
                    } else {
                        wm.setBitmap(composed)
                    }
                }
            }
            if (composed !== raw) composed.recycle()
            raw.recycle()
            ApplyResult.Success
        }.getOrElse { t ->
            ApplyResult.Failure(t.message ?: "Error desconocido")
        }
    }

    /** Saves the current video as the live wallpaper source and opens the chooser. */
    fun applyVideo(context: Context, uri: Uri, title: String?): ApplyResult {
        return runCatching {
            WallpaperPrefs(context).setCurrentVideo(uri.toString(), title)
            VideoWallpaperService.openChooser(context)
            ApplyResult.LiveWallpaperOpened
        }.getOrElse { ApplyResult.Failure(it.message ?: "Error desconocido") }
    }

    /** Decode at screen resolution (never a full 8K bitmap). */
    private fun decodeForScreen(context: Context, file: File): Bitmap? {
        val wm = WallpaperManager.getInstance(context)
        val targetW = if (wm.desiredMinimumWidth > 0) wm.desiredMinimumWidth
        else context.resources.displayMetrics.widthPixels
        val targetH = if (wm.desiredMinimumHeight > 0) wm.desiredMinimumHeight
        else context.resources.displayMetrics.heightPixels

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW &&
            bounds.outHeight / (sample * 2) >= targetH
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    /**
     * Scale-crop the bitmap onto the exact wallpaper canvas, using [focusX]/[focusY]
     * (0..1) to decide which part survives the crop.
     */
    private fun compose(
        src: Bitmap,
        context: Context,
        focusX: Float,
        focusY: Float,
        blurAmount: Int,
        dimAmount: Int,
    ): Bitmap {
        val wm = WallpaperManager.getInstance(context)
        val outW = if (wm.desiredMinimumWidth > 0) wm.desiredMinimumWidth
        else context.resources.displayMetrics.widthPixels
        val outH = if (wm.desiredMinimumHeight > 0) wm.desiredMinimumHeight
        else context.resources.displayMetrics.heightPixels

        if (src.width == outW && src.height == outH && blurAmount == 0 && dimAmount == 0) return src

        val scale = maxOf(outW.toFloat() / src.width, outH.toFloat() / src.height)
        val dx = (outW - src.width * scale) * focusX.coerceIn(0f, 1f)
        val dy = (outH - src.height * scale) * focusY.coerceIn(0f, 1f)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

        var blurred = out
        if (blurAmount > 0) blurred = blur(out, blurAmount.toFloat())
        if (dimAmount > 0) {
            val c = Canvas(blurred)
            c.drawRect(
                Rect(0, 0, blurred.width, blurred.height),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(dimAmount.coerceIn(0, 255), 0, 0, 0)
                },
            )
        }
        return blurred
    }

    private fun blur(src: Bitmap, radius: Float): Bitmap {
        // RenderScript is deprecated; a downscale/upscale round-trip gives a cheap
        // aesthetic blur that works on every API level and costs no extra dependency.
        val w = (src.width / 8f).toInt().coerceAtLeast(1)
        val h = (src.height / 8f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            small,
            null,
            Rect(0, 0, src.width, src.height),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        small.recycle()
        return out
    }

    /** Clearing the wallpaper (useful while testing). */
    @Throws(IOException::class)
    suspend fun clear(context: Context, target: ApplyTarget) = withContext(Dispatchers.IO) {
        val wm = WallpaperManager.getInstance(context)
        when (target) {
            ApplyTarget.HOME -> wm.clear(WallpaperManager.FLAG_SYSTEM)
            ApplyTarget.LOCK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.clear(WallpaperManager.FLAG_LOCK)
            } else {
                wm.clear()
            }
            ApplyTarget.BOTH -> wm.clear()
        }
    }
}
