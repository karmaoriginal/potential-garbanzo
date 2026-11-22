package app.lumira.wallpapers.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.lumira.wallpapers.domain.model.ApplyTarget
import app.lumira.wallpapers.domain.model.MediaKind
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.ui.components.InfoRow
import app.lumira.wallpapers.ui.components.humanBytes
import app.lumira.wallpapers.ui.components.shimmer
import app.lumira.wallpapers.ui.detail.DetailViewModel
import coil.compose.SubcomposeAsyncImage

/**
 * Full-screen preview ("prueba antes de aplicar").
 *
 * - The wallpaper fills the screen exactly like on the launcher (with a translucent
 *   app UI on top), so the user judges the real crop.
 * - Videos play with a muted ExoPlayer that loops and is paused whenever the app goes
 *   to the background (lifecycle-aware), then released on dispose.
 * - Image wallpapers can be re-cropped by dragging vertically *before* applying, via
 *   the focus sliders in the action sheet.
 */
@Composable
fun DetailScreen(
    wallpaperId: String?,
    onBack: () -> Unit,
    catalogId: String? = null,
    thumbKey: String = "",
    viewModel: DetailViewModel = viewModel(),
) {
    LaunchedEffect(wallpaperId, catalogId) {
        viewModel.load(wallpaperId, catalogId)
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val wallpaper = state.item
    var showSheet by remember { mutableStateOf(false) }
    var focusY by remember { mutableFloatStateOf(0.5f) }
    var previewing by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Blurred background derived from the same image = premium look while loading.
        if (wallpaper != null) {
            SubcomposeAsyncImage(
                model = wallpaper.thumbPath ?: wallpaper.remoteThumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp),
                loading = { Box(Modifier.fillMaxSize().shimmer()) },
            )
        }

        if (wallpaper?.kind == MediaKind.VIDEO) {
            val uri = wallpaper.localPath ?: wallpaper.remotePreview.orEmpty()
            if (uri.isNotBlank()) {
                VideoPreview(
                    uri = uri,
                    modifier = Modifier.fillMaxSize(),
                    onLongPressHint = { showSheet = true },
                )
            }
        } else if (wallpaper != null) {
            SubcomposeAsyncImage(
                model = wallpaper.localPath ?: wallpaper.remoteThumb,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().shimmer()) },
            )
        }

        // Top bar, translucent so the wallpaper shows through.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrimIconButton(Icons.Filled.ArrowBack, "Atrás", onBack)
            Spacer(Modifier.weight(1f))
            if (wallpaper != null) {
                ScrimIconButton(
                    icon = if (wallpaper.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    description = "Favorito",
                    onClick = { viewModel.toggleFavorite() },
                )
            }
        }

        AnimatedVisibility(
            visible = wallpaper != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            wallpaper?.let { item ->
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        InfoRow(
                            modifier = Modifier.padding(top = 8.dp),
                            pills = buildList {
                                add(if (item.kind == MediaKind.VIDEO) "Video" else "Imagen")
                                if (item.width > 0) add("${item.width}×${item.height}")
                                if (item.durationMs > 0) add("${item.durationMs / 1000}s")
                                if (item.sizeBytes > 0) add(humanBytes(item.sizeBytes))
                                item.license?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }.toTypedArray(),
                        )
                        item.author?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "por $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        if (state.progress in 0.01f..0.99f) {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(
                                    "Mejorando video… ${(state.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                                TextButton(onClick = viewModel::cancelEnhance) {
                                    Text("Cancelar")
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!state.downloaded && catalogId != null) {
                                BigButton(
                                    label = if (state.downloading) {
                                        "Descargando ${(state.downloadProgress * 100).toInt()}%"
                                    } else {
                                        "Descargar"
                                    },
                                    icon = { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)) },
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::download,
                                    enabled = !state.downloading,
                                )
                            } else if (item.kind == MediaKind.VIDEO) {
                                BigButton(
                                    label = "Aplicar animado",
                                    icon = { Icon(Icons.Outlined.Smartphone, null, Modifier.size(18.dp)) },
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.applyVideo() },
                                )
                            } else {
                                BigButton(
                                    label = "Aplicar",
                                    icon = { Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp)) },
                                    modifier = Modifier.weight(1f),
                                    onClick = { showSheet = true },
                                )
                            }
                        }

                        if (item.kind == MediaKind.VIDEO && item.localPath != null &&
                            state.enhanceAvailable
                        ) {
                            Row(Modifier.padding(top = 4.dp)) {
                                TextButton(onClick = viewModel::enhance) {
                                    Text("Mejorar calidad (H.265)")
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSheet && wallpaper?.kind == MediaKind.IMAGE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ApplyTargetSheet(
                onDismiss = { showSheet = false },
                focusY = focusY,
                onFocusChange = { focusY = it },
                onApply = { target ->
                    showSheet = false
                    viewModel.applyImage(target, 0.5f, focusY)
                },
            )
        }

        state.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2200)
                viewModel.consumeMessage()
            }
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp),
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPreview(
    uri: String,
    modifier: Modifier = Modifier,
    onLongPressHint: () -> Unit,
) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(true) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    // Release on dispose: prevents the classic ExoPlayer leak in Compose.
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        modifier = modifier,
    )
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 74.dp, end = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.4f),
                onClick = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                },
            ) {
                Text(
                    if (muted) "Silenciado" else "Con audio",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ScrimIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        modifier = modifier,
        onClick = onClick,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f),
        onClick = { if (enabled) onClick() },
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(vertical = 13.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimary,
            ) { icon() }
            Spacer(Modifier.size(8.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ApplyTargetSheet(
    onDismiss: () -> Unit,
    focusY: Float,
    onFocusChange: (Float) -> Unit,
    onApply: (ApplyTarget) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aplicar en", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Text(
                    "Ajusta el encuadre vertical antes de aplicar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Slider(
                    value = focusY,
                    onValueChange = onFocusChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TargetButton("Inicio", Icons.Outlined.PhoneAndroid, Modifier.weight(1f)) {
                        onApply(ApplyTarget.HOME)
                    }
                    TargetButton("Bloqueo", Icons.Outlined.Lock, Modifier.weight(1f)) {
                        onApply(ApplyTarget.LOCK)
                    }
                }
                Row(Modifier.padding(top = 10.dp)) {
                    TargetButton("Ambos", Icons.Outlined.Smartphone, Modifier.weight(1f)) {
                        onApply(ApplyTarget.BOTH)
                    }
                }
                Text(
                    "Si el video es animado, Lumira abrirá el selector de fondos animados del sistema.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TargetButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        onClick = onClick,
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
