package app.lumira.wallpapers.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import app.lumira.wallpapers.data.media.MediaKind
import app.lumira.wallpapers.domain.model.Wallpaper

/**
 * The card used in every masonry grid.
 *
 * Uses a shared-element key ([sharedKey]) so the detail screen can morph the image
 * into place. Press feedback is a spring-driven scale on the graphics layer, which
 * stays off the composition phase and keeps 120 Hz scrolling smooth.
 */
@Composable
fun WallpaperCard(
    item: Wallpaper,
    sharedKey: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    showTitle: Boolean = true,
) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "cardPress",
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(sharedKey) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = onLongClick?.let { { it() } },
                )
            },
    ) {
        SubcomposeAsyncImage(
            model = item.thumbPath ?: item.remoteThumb,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { ShimmerBlock(Modifier.fillMaxSize(), shape) },
            error = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✦",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        // Bottom scrim so text stays legible on bright wallpapers.
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                )
                .padding(10.dp),
        )

        if (item.kind == MediaKind.VIDEO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("▶", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showTitle) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.sizeBytes > 0) {
                    Text(
                        text = humanBytes(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        if (onFavoriteClick != null) {
            FavoriteBubble(
                favorite = item.isFavorite,
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
fun FavoriteBubble(
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (favorite) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "favScale",
    )
    Surface(
        modifier = modifier
            .size(30.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.38f),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (favorite) Color(0xFFFF6B8A) else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Compact size formatter (es-MX friendly): 1.4 MB, 820 KB … */
fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

/** Row of tiny stats/pills used in detail sheets. */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun InfoRow(vararg pills: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pills.filter { it.isNotBlank() }.forEach { InfoPill(it) }
    }
}
