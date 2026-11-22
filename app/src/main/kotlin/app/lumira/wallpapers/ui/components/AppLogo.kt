package app.lumira.wallpapers.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.lumira.wallpapers.ui.theme.LumiraMint
import app.lumira.wallpapers.ui.theme.LumiraViolet

/**
 * Lumira's mark: a glowing "aperture" of two arcs around a bright core.
 * Drawn with Canvas so it scales crisply at any size and costs no bitmap.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Int = 56,
) {
    Canvas(modifier = modifier.size(size.dp)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LumiraViolet.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(LumiraViolet, LumiraMint, LumiraViolet)),
            startAngle = 200f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
            size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
            style = Stroke(width = radius * 0.16f, cap = StrokeCap.Round),
        )
        drawArc(
            color = LumiraMint,
            startAngle = 20f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.52f, center.y - radius * 0.52f),
            size = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 1.04f),
            style = Stroke(width = radius * 0.11f, cap = StrokeCap.Round),
        )
        drawCircle(color = Color.White, radius = radius * 0.17f, center = center)
    }
}
