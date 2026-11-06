package com.orra.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.neonGlow(color: Color, corner: Dp, intensity: Float, spread: Dp = 12.dp): Modifier =
    drawBehind {
        if (intensity <= 0.01f) return@drawBehind
        val steps = 7
        val sp = spread.toPx()
        for (i in steps downTo 1) {
            val grow = sp * i / steps
            drawRoundRect(
                color = color.copy(alpha = 0.07f * intensity),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(corner.toPx() + grow)
            )
        }
    }

@Composable
fun Modifier.neonPress(
    color: Color,
    corner: Dp,
    baseIntensity: Float = 0f,
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val target = if (pressed) 1.4f else baseIntensity
    val a by animateFloatAsState(target, tween(180), label = "glow")
    return this
        .neonGlow(color, corner, a)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}
