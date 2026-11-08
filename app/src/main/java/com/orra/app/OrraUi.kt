package com.orra.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay

@Composable
fun OrraUnlockDialog(title: String, lockKey: String, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("forgot_reset_title")) },
            text = { Text(tr("forgot_reset_body")) },
            confirmButton = {
                TextButton(onClick = {
                    OrraLocks.removeAll(context)
                    SupabaseChatClient.clearSession(context)
                    (context as? android.app.Activity)?.recreate()
                    onDismiss()
                }) { Text(tr("reset_and_sign_out")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("cancel")) } }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(password, { password = it; wrong = false }, singleLine = true, isError = wrong,
                    label = { Text(tr("password")) }, visualTransformation = PasswordVisualTransformation())
                if (wrong) Text(tr("wrong_password"), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { confirmReset = true }) { Text(tr("forgot_password")) }
            }
        },
        confirmButton = { TextButton(onClick = { if (OrraLocks.verify(context, lockKey, password)) onUnlocked() else wrong = true }) { Text(tr("unlock")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } }
    )
}

@Composable
fun OrraSetPasswordDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(password, { password = it }, singleLine = true, label = { Text(tr("new_password")) },
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(confirm, { confirm = it }, singleLine = true, isError = mismatch, label = { Text(tr("confirm_password")) },
                    visualTransformation = PasswordVisualTransformation())
                if (mismatch) Text(tr("passwords_differ"), color = MaterialTheme.colorScheme.error)
                else Text(tr("password_hint"), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = password.length >= 4 && password == confirm, onClick = { onSave(password) }) { Text(tr("save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } }
    )
}

/** Wraps a media message: shows it, or a "tap to download" placeholder when auto-download is off for that type. */
@Composable
fun OrraMediaGate(kind: String, key: String, isGroup: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var manual by remember(key) { mutableStateOf(false) }
    if (kind.isBlank() || manual || OrraDownloads.allowed(context, kind, isGroup)) {
        content()
    } else {
        Column {
            Text(tr("media_$kind"), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { manual = true }) { Text(tr("tap_to_download")) }
        }
    }
}

/** Returns the chat background for the wallpaper chosen in Chat settings, or null for the app default. */
@Composable
fun rememberOrraWallpaperModifier(): Modifier? {
    val context = LocalContext.current
    val version = OrraWallpaper.version.intValue
    val name = remember(version) { OrraWallpaper.current(context) }
    val bitmap = remember(version, name) {
        if (name == "Custom") runCatching { BitmapFactory.decodeFile(OrraWallpaper.file(context).path)?.asImageBitmap() }.getOrNull() else null
    }
    return when (name) {
        "Midnight" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF04060B), Color(0xFF111627))))
        "Ocean" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF06141F), Color(0xFF0E3A4F))))
        "Violet" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF120B22), Color(0xFF2E1B57))))
        "Custom" -> if (bitmap == null) null
            else Modifier.paint(BitmapPainter(bitmap), contentScale = ContentScale.Crop).background(Color.Black.copy(alpha = 0.30f))
        else -> null
    }
}

/** Warm screen tint for comfortable reading. Touches pass through it. */
@Composable
fun OrraEyeOverlay() {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val unused = tick
    if (OrraEye.active()) {
        Box(Modifier.fillMaxSize().background(Color(0xFFFF8A1F).copy(alpha = 0.05f + OrraEye.level.value * 0.30f)))
    }
}
