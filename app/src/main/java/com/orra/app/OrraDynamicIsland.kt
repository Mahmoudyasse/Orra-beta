package com.orra.app

import androidx.compose.animation.core.snap
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-app Dynamic Island style surface.
 * This is an interactive app component, not an Android system overlay.
 */
@Composable
fun OrraDynamicIsland() {
    var expanded by remember { mutableStateOf(false) }
    val animate = OrraPrefs.animationsEnabled(androidx.compose.ui.platform.LocalContext.current)
    val width by animateDpAsState(
        targetValue = if (expanded) 286.dp else 126.dp,
        animationSpec = if (animate) tween(260) else snap(),
        label = "island-width"
    )
    val height by animateDpAsState(
        targetValue = if (expanded) 112.dp else 38.dp,
        animationSpec = if (animate) tween(260) else snap(),
        label = "island-height"
    )
    val shape = RoundedCornerShape(if (expanded) 28.dp else 24.dp)

    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF080A10), Color(0xFF15182A), Color(0xFF090B12))), shape)
            .border(1.dp, OrraAppearance.accentColor().copy(alpha = 0.22f), shape)
            .clickable { expanded = !expanded }
            .animateContentSize(tween(220))
            .padding(horizontal = 15.dp, vertical = if (expanded) 12.dp else 7.dp),
        verticalArrangement = if (expanded) Arrangement.spacedBy(10.dp) else Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(OrraAppearance.accentColor())
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "ORRA BETA" else "ORRA",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "ACTIVE" else "• • •",
                color = OrraAppearance.accentColor(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (expanded) {
            Text(
                text = "Quick panel",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp
            )
            Text(
                text = "Tap to collapse",
                color = Mint,
                fontSize = 10.sp
            )
        }
    }
}
