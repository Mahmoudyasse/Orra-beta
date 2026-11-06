package com.orra.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B14)
val Mist = Color(0xFFF5F5FA)
val Indigo = Color(0xFF5B4BFF)
val Mint = Color(0xFF5CF2C8)
val CallGreen = Color(0xFF30D158)

@Composable
fun OrraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Indigo, background = Ink, surface = Ink,
            onBackground = Color.White, onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C26)
        )
    } else {
        lightColorScheme(
            primary = Indigo, background = Mist, surface = Color.White,
            onBackground = Ink, onSurface = Ink,
            surfaceVariant = Color(0xFFE8E8F0)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val EndRed = Color(0xFFFF453A)
val GlowChats = Color(0xFF5CF2C8)
val GlowCalls = Color(0xFF30D158)
val GlowKeypad = Color(0xFF8B7DFF)
val GlowContacts = Color(0xFFFFB020)
val GlowSettings = Color(0xFF4CC9FF)
