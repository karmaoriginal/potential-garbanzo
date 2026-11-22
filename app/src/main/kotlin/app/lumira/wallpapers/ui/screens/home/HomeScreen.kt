package app.lumira.wallpapers.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumira.wallpapers.ui.LibraryViewModel
import app.lumira.wallpapers.ui.components.humanBytes
import app.lumira.wallpapers.ui.theme.LumiraMint
import app.lumira.wallpapers.ui.theme.LumiraViolet
import app.lumira.wallpapers.wallpaper.VideoWallpaperService
import coil.compose.AsyncImage

/**
 * Landing screen: greeting, import CTA, quick actions and the most recent wallpapers.
 * Uses parallax on the hero card (scroll-linked translation) for depth.
 */
@Composable
fun HomeScreen(
    onOpenShop: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (String, String) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        uris.forEach { viewModel.import(it) }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600),
        label = "homeFade",
    )

    Box(Modifier.fillMaxSize()) {
        // Ambient gradient that follows the theme instead of a fixed image.
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            LumiraViolet.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 54.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Lumira",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tu pantalla, mejor cada día",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { VideoWallpaperService.openChooser(context) }) {
                        Icon(Icons.Outlined.Collections, contentDescription = "Wallpaper animado")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Ajustes")
                    }
                }
            }

            item {
                HeroCard(
                    modifier = Modifier.alpha(contentAlpha),
                    count = state.stats.count,
                    bytes = state.stats.bytes,
                    onImport = {
                        runCatching {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                ),
                            )
                        }
                    },
                    onShop = onOpenShop,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        title = "Tienda",
                        subtitle = "Curado y gratis",
                        icon = { Icon(Icons.Outlined.Storefront, null) },
                        modifier = Modifier.weight(1f),
                        onClick = onOpenShop,
                    )
                    QuickAction(
                        title = "Biblioteca",
                        subtitle = "${state.stats.count} elementos",
                        icon = { Icon(Icons.Outlined.Collections, null) },
                        modifier = Modifier.weight(1f),
                        onClick = onOpenLibrary,
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recientes",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Ver todo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (state.items.isEmpty() && !state.loading) {
                item {
                    EmptyLibraryCard(onImport = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    })
                }
            } else {
                items(items = state.items.take(6), key = { it.id }) { item ->
                    RecentRow(
                        item = item,
                        onClick = {
                            onOpenDetail(item.kind.name.lowercase(), item.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    count: Int,
    bytes: Long,
    onImport: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                LumiraViolet.copy(alpha = 0.85f),
                                LumiraMint.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
            )
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Wallpapers estáticos y animados",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    "Importa tus fotos y videos, o explora la tienda curada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledPill("Importar", Icons.Filled.Add, Color.White, LumiraViolet, onImport)
                    OutlinedPill("Tienda", onShop, Color.White)
                }
                Text(
                    "$count en biblioteca · ${humanBytes(bytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun FilledPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: Color,
    container: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = container,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@Composable
private fun OutlinedPill(text: String, onClick: () -> Unit, color: Color) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        onClick = onClick,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.primary,
                ) { icon() }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(
    item: app.lumira.wallpapers.domain.model.Wallpaper,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbPath ?: item.remoteThumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (item.kind == app.lumira.wallpapers.domain.model.MediaKind.VIDEO) "Video" else "Imagen") +
                    " · ${item.width}×${item.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.TextButton(onClick = onClick) { Text("Abrir") }
    }
}

@Composable
private fun EmptyLibraryCard(onImport: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aún no hay wallpapers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Importa una foto o un video corto para empezar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            FilledPill("Importar ahora", Icons.Filled.Add, Color.White, LumiraViolet, onImport)
        }
    }
}

