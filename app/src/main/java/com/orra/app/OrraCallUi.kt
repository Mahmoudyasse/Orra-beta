package com.orra.app

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val CallRed = Color(0xFFFF3B30)
private val CallGreenColor = Color(0xFF34C759)

/** Large round action button with a label (iPhone style Decline / Accept / End). */
@Composable
fun CallRoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = color, onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Text(label, fontSize = 13.sp)
    }
}

/** Small toggle used for mute, keypad, speaker, hold and so on. */
@Composable
fun CallToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.size(64.dp), shape = CircleShape, onClick = onClick,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp),
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

/** Slide right to answer, left to decline. */
@Composable
fun OrraSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(80.dp)) {
        val trackPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { 68.dp.toPx() }
        val limit = ((trackPx - thumbPx) / 2f).coerceAtLeast(1f)
        Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(40.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("<  " + tr("call_decline"), color = CallRed, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(tr("call_accept") + "  >", color = CallGreenColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
        val tint = if (offsetX > 0f) CallGreenColor else if (offsetX < 0f) CallRed else MaterialTheme.colorScheme.primary
        Surface(
            modifier = Modifier.align(Alignment.Center).offset { IntOffset(offsetX.roundToInt(), 0) }.size(68.dp)
                .pointerInput(limit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > limit * 0.7f) onAnswer() else if (offsetX < -limit * 0.7f) onDecline()
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-limit, limit)
                    }
                },
            shape = CircleShape, color = tint
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, contentDescription = null, tint = Color.White) }
        }
    }
}
