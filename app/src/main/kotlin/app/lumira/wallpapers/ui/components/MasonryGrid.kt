package app.lumira.wallpapers.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumira.wallpapers.domain.model.Wallpaper
import app.lumira.wallpapers.domain.model.MediaKind

/**
 * Masonry grid of wallpapers.
 *
 * Wraps [LazyVerticalStaggeredGrid]: items are keyed by id (stable keys => no
 * re-creation on list updates), each item declares its own aspect ratio so heights
 * differ naturally, and the whole thing virtualises at 120 Hz.
 *
 * @param onNearEnd called once when the user is ~6 items from the end (paging hook)
 */
@Composable
fun MasonryGrid(
    items: List<Wallpaper>,
    columns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    spacing: Dp = 10.dp,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onNearEnd: (() -> Unit)? = null,
    itemContent: @Composable (Wallpaper) -> Unit,
) {
    val state = rememberLazyStaggeredGridState()
    val config = LocalConfiguration.current
    val columnWidth = remember(config.screenWidthDp, columns, spacing) {
        ((config.screenWidthDp.dp - 24.dp - spacing * (columns - 1)) / columns)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    if (onNearEnd != null) {
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) onNearEnd()
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        // Shared element transitions need the item to be addressable by its key.
        modifier = modifier.fillMaxSize().sharedBoundsCaller(),
        state = state,
        contentPadding = contentPadding,
        verticalItemSpacing = spacing,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (header != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "grid_header") {
                Box(Modifier.fillMaxWidth()) { header() }
            }
        }
        items(items = items, key = { it.id }, contentType = { it.kind.name }) { item ->
            val ratio = remember(item.width, item.height) {
                if (item.width > 0 && item.height > 0) {
                    (item.height.toFloat() / item.width).coerceIn(0.7f, 2.0f)
                } else {
                    1.5f
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(columnWidth * ratio),
            ) {
                itemContent(item)
            }
        }
        if (footer != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "grid_footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { footer() }
            }
        }
    }
}

/** Convenience: the "catalog item -> Wallpaper" mapping used by the shop screens. */
fun shopPreviewItem(
    id: String,
    title: String,
    kind: String,
    thumbUrl: String,
    width: Int,
    height: Int,
    favorite: Boolean,
): Wallpaper = Wallpaper(
    id = id,
    title = title,
    kind = if (kind.equals("video", true)) {
        app.lumira.wallpapers.domain.model.MediaKind.VIDEO
    } else {
        app.lumira.wallpapers.domain.model.MediaKind.IMAGE
    },
    source = app.lumira.wallpapers.domain.model.WallpaperSource.DOWNLOADED,
    remoteThumb = thumbUrl,
    width = width,
    height = height,
    isFavorite = favorite,
)


/**
 * Opts the grid into shared-element participation when the caller wants it.
 * Kept as a no-op extension so screens can enable it per destination without
 * paying the cost of a transition scope where it is not needed.
 */
@Composable
private fun Modifier.sharedBoundsCaller(): Modifier = this
