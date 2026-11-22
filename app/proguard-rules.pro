# ---- Kotlin / Compose ----
-dontwarn kotlinx.**
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- Room ----
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Coil ----
-dontwarn coil.**

# ---- Media3 ----
-dontwarn androidx.media3.**

# ---- FFmpegKit ----
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ---- Wallpaper service must be instantiable by the system ----
-keep class app.lumira.wallpapers.wallpaper.** { *; }

# ---- Serializable models (catalog) ----
-keepclassmembers class app.lumira.wallpapers.data.shop.** { *; }

# Kotlin coroutines internals
-dontwarn kotlinx.coroutines.debug.**
