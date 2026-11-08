package com.orra.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B14)
val Mist = Color(0xFFF5F5FA)
val Indigo = Color(0xFF5B4BFF)
val Mint = Color(0xFF5CF2C8)
val CallGreen = Color(0xFF30D158)
val EndRed = Color(0xFFFF453A)
val GlowChats = Color(0xFF5CF2C8)
val GlowCalls = Color(0xFF30D158)
val GlowKeypad = Color(0xFF8B7DFF)
val GlowContacts = Color(0xFFFFB020)
val GlowSettings = Color(0xFF4CC9FF)
val GlowSim = Color(0xFFFF6FB5)

object OrraAppearance {
    val mode = mutableStateOf("system")
    val accent = mutableStateOf("liquid")
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("orra_appearance", Context.MODE_PRIVATE)
        mode.value = prefs.getString("mode", "system") ?: "system"
        accent.value = prefs.getString("accent", "liquid") ?: "liquid"
    }
    fun setMode(context: Context, value: String) {
        mode.value = value
        context.getSharedPreferences("orra_appearance", Context.MODE_PRIVATE).edit().putString("mode", value).apply()
    }
    fun setAccent(context: Context, value: String) {
        accent.value = value
        context.getSharedPreferences("orra_appearance", Context.MODE_PRIVATE).edit().putString("accent", value).apply()
    }
    fun accentColor(): Color = when (accent.value) {
        "blue" -> Color(0xFF54A8FF)
        "violet" -> Color(0xFF9B8CFF)
        "green" -> Color(0xFF55E6A5)
        "rose" -> Color(0xFFFF76AE)
        else -> Mint
    }
}

@Composable
fun OrraTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (OrraAppearance.mode.value) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val accent = OrraAppearance.accentColor()
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, secondary = Indigo, tertiary = Mint,
            background = Color.Black, surface = Color(0xFF0A0A0A),
            onBackground = Color.White, onSurface = Color.White,
            surfaceVariant = Color(0xFF181818)
        )
    } else {
        lightColorScheme(
            primary = Indigo, secondary = accent, tertiary = Color(0xFF167B66),
            background = Mist, surface = Color.White,
            onBackground = Ink, onSurface = Ink,
            surfaceVariant = Color(0xFFE8E8F0)
        )
    }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
