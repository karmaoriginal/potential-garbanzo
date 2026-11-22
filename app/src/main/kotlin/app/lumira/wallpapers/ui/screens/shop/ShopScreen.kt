package app.lumira.wallpapers.ui.screens.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumira.wallpapers.data.shop.CatalogState
import app.lumira.wallpapers.domain.model.MediaFilter
import app.lumira.wallpapers.domain.model.ShopSort
import app.lumira.wallpapers.ui.ShopViewModel
import app.lumira.wallpapers.ui.components.FavoriteBubble
import app.lumira.wallpapers.ui.components.MasonryGrid
import app.lumira.wallpapers.ui.components.ShimmerBlock
import app.lumira.wallpapers.ui.components.shopPreviewItem
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale

/**
 * The integrated shop: search, category filters, media filters, sorting, favorites and
 * on-demand downloads with visible progress. All thumbnails are tiny WebP files served
 * from GitHub Pages, so browsing costs very little data.
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: ShopViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize()) {
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            onCommit = viewModel::commitSearch,
            onBack = onBack,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.favoritesOnly,
                onClick = { viewModel.toggleFavoritesOnly() },
                label = { Text("Favoritos") },
                leadingIcon = {
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp))
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(),
            )
            if (state.media != MediaFilter.ALL) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setMedia(MediaFilter.ALL) },
                    label = { Text(state.media.label) },
                    trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                    shape = CircleShape,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.category == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text("Todo") },
                    shape = CircleShape,
                )
            }
            items(state.categories, key = { it }) { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = {
                        viewModel.setCategory(if (state.category == category) null else category)
                    },
                    label = { Text(category.replaceFirstChar { it.uppercase() }) },
                    shape = CircleShape,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.loading) "Cargando catálogo…" else "${state.items.size} resultados",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ShopSort.entries.forEach { sort ->
                Text(
                    text = sort.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.sort == sort) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(
                            if (state.sort == sort) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        when {
            state.loading && state.items.isEmpty() -> ShimmerGrid()
            state.catalog is CatalogState.Error && state.items.isEmpty() -> ErrorState(
                message = (state.catalog as CatalogState.Error).message,
                onRetry = { viewModel.load(force = true) },
            )
            state.items.isEmpty() -> EmptyShop()
            else -> MasonryGrid(
                items = state.items.map { item ->
                    shopPreviewItem(
                        id = item.id,
                        title = item.title,
                        kind = item.kind,
                        thumbUrl = item.thumbUrl(
                            (state.catalog as? CatalogState.Ready)?.catalog?.baseUrl.orEmpty(),
                        ),
                        width = item.width,
                        height = item.height,
                        favorite = state.signals[item.id]?.favorite == true,
                    )
                },
                columns = 2,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
            ) { preview ->
                val original = state.items.firstOrNull { it.id == preview.id } ?: return@MasonryGrid
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    SubcomposeAsyncImage(
                        model = original.thumbUrl(
                            (state.catalog as? CatalogState.Ready)?.catalog?.baseUrl.orEmpty(),
                        ),
                        contentDescription = original.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ShimmerBlock(Modifier.fillMaxSize(), RoundedCornerShape(22.dp)) },
                    )
                    // Whole card opens the preview.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(0.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.addView(original)
                                onOpenItem(original.id)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = original.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                        val progress = state.downloads[original.id]
                        if (progress != null) {
                            Box(Modifier.padding(start = 6.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    FavoriteBubble(
                        favorite = state.signals[original.id]?.favorite == true,
                        onClick = { viewModel.toggleFavorite(original) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 46.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Buscar anime, agua, amoled…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange(""); onCommit("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            onClick = onBack,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
        }
    }
}

@Composable
private fun ShimmerGrid() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { column ->
                    ShimmerBlock(
                        Modifier
                            .weight(1f)
                            .height(if ((row + column) % 2 == 0) 240.dp else 190.dp),
                        RoundedCornerShape(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No se pudo cargar la tienda", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            onClick = onRetry,
        ) {
            Text(
                "Reintentar",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyShop() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Download, null, Modifier.size(40.dp))
        Text(
            "Sin resultados para ese filtro",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
