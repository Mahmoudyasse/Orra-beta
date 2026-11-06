package com.orra.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun OrraWordmark(modifier: Modifier = Modifier, color: Color, dot: Color) {
    Canvas(modifier.aspectRatio(297f / 106f)) {
        val s = size.width / 297f
        fun p(x: Float, y: Float) = Offset((x - 17f) * s, (y - 57f) * s)
        val w = 11f * s
        val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawArc(
            color = color, startAngle = -1.5f, sweepAngle = 275f, useCenter = false,
            topLeft = p(43f, 83f), size = Size(54f * s, 54f * s), style = st
        )
        for (sx in listOf(125f, 180f)) {
            drawLine(color, p(sx, 83f), p(sx, 137f), strokeWidth = w, cap = StrokeCap.Round)
            val a = p(sx, 112f); val b = p(sx, 94f); val c = p(sx + 10f, 83f); val d = p(sx + 27f, 83f)
            val path = Path().apply {
                moveTo(a.x, a.y)
                cubicTo(b.x, b.y, c.x, c.y, d.x, d.y)
            }
            drawPath(path, color, style = st)
        }
        drawCircle(color, radius = 27f * s, center = p(262f, 110f), style = st)
        drawLine(color, p(289f, 83f), p(289f, 137f), strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(dot, radius = 6.5f * s, center = p(89f, 91f))
    }
}
