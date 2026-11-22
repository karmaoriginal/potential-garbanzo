package app.lumira.wallpapers.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumira.wallpapers.domain.model.MediaKind
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.enhance.VideoEnhancer
import app.lumira.wallpapers.ui.LibraryFilter
import app.lumira.wallpapers.ui.LibraryViewModel
import app.lumira.wallpapers.ui.components.MasonryGrid
import app.lumira.wallpapers.ui.components.WallpaperCard
import app.lumira.wallpapers.ui.components.humanBytes

/**
 * Local library: imports, favorites, storage summary and per-item actions
 * (rename, delete, apply). Grid density respects the user's setting.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenDetail: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val enhanceAvailable = remember(context) {
        VideoEnhancer.available(context)
    }
    var actionTarget by remember { mutableStateOf<Wallpaper?>(null) }
    var renameTarget by remember { mutableStateOf<Wallpaper?>(null) }
    var deleteTarget by remember { mutableStateOf<Wallpaper?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> uris.forEach { viewModel.import(it) } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 14.dp, top = 44.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
            }
            Text(
                "Biblioteca",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Ajustes")
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                IconButton(onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Importar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        StorageSummary(
            count = state.stats.count,
            images = state.stats.images,
            videos = state.stats.videos,
            bytes = state.stats.totalBytes,
            onCleanCache = viewModel::clearCaches,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries.toList(), key = { it.name }) { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
                    shape = CircleShape,
                )
            }
        }

        if (state.items.isEmpty() && !state.loading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nada por aquí todavía", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usa el botón + para importar fotos o videos cortos. " +
                        "No pedimos permisos de almacenamiento: usamos el selector del sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            MasonryGrid(
                items = state.items,
                columns = 2,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
            ) { item ->
                WallpaperCard(
                    item = item,
                    sharedKey = item.id,
                    onClick = { onOpenDetail(item.kind.name.lowercase(), item.id) },
                    onLongClick = { actionTarget = item },
                    onFavoriteClick = { viewModel.toggleFavorite(item) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    actionTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { actionTarget = null }, sheetState = sheetState) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    target.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SheetAction(Icons.Filled.Edit, "Renombrar") {
                    actionTarget = null
                    renameTarget = target
                }
                if (target.kind == MediaKind.VIDEO && enhanceAvailable) {
                    SheetAction(Icons.Filled.Edit, "Mejorar calidad (H.265)") {
                        actionTarget = null
                        viewModel.scheduleEnhance(
                            target,
                            app.lumira.wallpapers.domain.model.QualityPreset.HD_1080,
                        )
                    }
                }
                SheetAction(Icons.Filled.Delete, "Eliminar", destructive = true) {
                    actionTarget = null
                    deleteTarget = target
                }
            }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(target, text)
                    renameTarget = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar «${target.title}»?") },
            text = { Text("Se borrará el archivo del almacenamiento de la app.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StorageSummary(
    count: Int,
    images: Int,
    videos: Int,
    bytes: Long,
    onCleanCache: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$count elementos · ${humanBytes(bytes)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "$images imágenes · $videos videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCleanCache) { Text("Limpiar caché") }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    label,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
