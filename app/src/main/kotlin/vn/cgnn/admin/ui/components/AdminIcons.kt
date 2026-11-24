package vn.cgnn.admin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SearchVectorIcon(
    color: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val r = this.size.minDimension * 0.30f
        val cx = this.size.width * 0.44f
        val cy = this.size.height * 0.44f
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(cx + r * 0.72f, cy + r * 0.72f),
            end = Offset(this.size.width * 0.86f, this.size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CloseVectorIcon(
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.12f
        val pad = this.size.minDimension * 0.24f
        drawLine(
            color = color,
            start = Offset(pad, pad),
            end = Offset(this.size.width - pad, this.size.height - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width - pad, pad),
            end = Offset(pad, this.size.height - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun NfcWaveVectorIcon(
    color: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        val p1 = Path().apply {
            moveTo(w * 0.33f, h * 0.29f)
            quadraticTo(w * 0.54f, h * 0.50f, w * 0.33f, h * 0.71f)
        }
        val p2 = Path().apply {
            moveTo(w * 0.50f, h * 0.17f)
            quadraticTo(w * 0.84f, h * 0.50f, w * 0.50f, h * 0.83f)
        }
        val p3 = Path().apply {
            moveTo(w * 0.67f, h * 0.33f)
            quadraticTo(w * 0.80f, h * 0.50f, w * 0.67f, h * 0.67f)
        }
        drawPath(p1, color = color, style = stroke)
        drawPath(p2, color = color, style = stroke)
        drawPath(p3, color = color, style = stroke)
    }
}

@Composable
fun CopyVectorIcon(
    color: Color,
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.35f, h * 0.35f),
            size = Size(w * 0.52f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
            style = stroke,
        )
        val back = Path().apply {
            moveTo(w * 0.25f, h * 0.65f)
            lineTo(w * 0.18f, h * 0.65f)
            lineTo(w * 0.13f, h * 0.60f)
            lineTo(w * 0.13f, h * 0.18f)
            lineTo(w * 0.18f, h * 0.13f)
            lineTo(w * 0.60f, h * 0.13f)
            lineTo(w * 0.65f, h * 0.18f)
            lineTo(w * 0.65f, h * 0.25f)
        }
        drawPath(back, color = color, style = stroke)
    }
}

@Composable
fun LinkVectorIcon(
    color: Color,
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.32f),
            size = Size(w * 0.38f, h * 0.36f),
            cornerRadius = CornerRadius(h * 0.18f, h * 0.18f),
            style = stroke,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.52f, h * 0.32f),
            size = Size(w * 0.38f, h * 0.36f),
            cornerRadius = CornerRadius(h * 0.18f, h * 0.18f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.36f, h * 0.50f),
            end = Offset(w * 0.64f, h * 0.50f),
            strokeWidth = w * 0.10f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun MessageVectorIcon(
    color: Color,
    size: Dp = 15.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bubble = Path().apply {
            moveTo(w * 0.16f, h * 0.20f)
            lineTo(w * 0.84f, h * 0.20f)
            lineTo(w * 0.84f, h * 0.68f)
            lineTo(w * 0.38f, h * 0.68f)
            lineTo(w * 0.16f, h * 0.86f)
            close()
        }
        drawPath(bubble, color = color, style = stroke)
        drawLine(color, Offset(w * 0.30f, h * 0.40f), Offset(w * 0.70f, h * 0.40f), w * 0.09f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.30f, h * 0.54f), Offset(w * 0.56f, h * 0.54f), w * 0.09f, StrokeCap.Round)
    }
}

@Composable
fun WarningVectorIcon(
    color: Color,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tri = Path().apply {
            moveTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.84f)
            lineTo(w * 0.12f, h * 0.84f)
            close()
        }
        drawPath(tri, color = color, style = stroke)
        drawLine(color, Offset(w * 0.50f, h * 0.40f), Offset(w * 0.50f, h * 0.60f), w * 0.09f, StrokeCap.Round)
        drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.72f))
    }
}
