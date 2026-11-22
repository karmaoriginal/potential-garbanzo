package app.lumira.wallpapers.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.lumira.wallpapers.ui.theme.LocalLumiraExtras

/**
 * A shimmering placeholder used while images or catalog pages load.
 *
 * Implemented as a single infinite gradient translation on the draw layer: no
 * recomposition per frame (it runs in the composition-free graphics phase), so it
 * costs no jank on 120 Hz displays.
 */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(20.dp)): Modifier = composed {
    val extras = LocalLumiraExtras.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = extras.shimmerBase
    val highlight = extras.shimmerHighlight
    this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(progress * 900f - 450f, 0f),
                end = Offset(progress * 900f + 150f, 300f),
            ),
        )
}

/** Fills its parent with a shimmer surface, used as a skeleton for cards. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(modifier = modifier.shimmer(shape))
}
