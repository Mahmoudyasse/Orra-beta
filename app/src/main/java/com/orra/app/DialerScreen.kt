package com.orra.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object OrraPendingChat {
    var userId by mutableStateOf<String?>(null)
    var username by mutableStateOf("")
    fun clear() { userId = null; username = "" }
}

private val keys = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)


@Composable
fun DialerScreen(onOpenChat: () -> Unit = {}) {
    val ctx = LocalContext.current
    var number by remember { mutableStateOf("") }
    var orraAccount by remember { mutableStateOf<OrraChatUser?>(null) }
    var lookupStatus by remember { mutableStateOf("") }
    val prefs = remember { ctx.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(number) {
        orraAccount = null
        lookupStatus = ""
        val token = prefs.getString("token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        if (token.isNotBlank() && userId.isNotBlank() && number.filter { it.isDigit() }.length >= 10) {
            delay(450)
            runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseChatClient(ctx, SupabaseChatClient.DEFAULT_PROJECT_URL,
                        SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY, token, userId).searchUsersByPhone(number)
                }
            }.onSuccess { found ->
                orraAccount = found
                lookupStatus = if (found == null) "No ORRA account found for this number." else "ORRA account: @${found.username}"
            }.onFailure { lookupStatus = "Could not check ORRA directory." }
        }
    }
    val startCall = rememberOrraCallAction()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = number,
            fontSize = 38.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
        if (lookupStatus.isNotBlank()) {
            Text(lookupStatus, style = MaterialTheme.typography.bodySmall,
                color = if (orraAccount != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (orraAccount != null) {
            OutlinedButton(onClick = {
                OrraPendingChat.userId = orraAccount!!.id
                OrraPendingChat.username = orraAccount!!.username
                onOpenChat()
            }, modifier = Modifier.fillMaxWidth()) { Text("Open ORRA chat") }
        }
        if (number.isNotBlank()) {
            TextButton(onClick = {
                val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                runCatching { ctx.startActivity(sms) }
            }) { Text("Send SMS") }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (d, letters) ->
                        KeyButton(d, letters) { number += d }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(78.dp))
                Box(
                    Modifier.size(78.dp)
                        .neonPress(CallGreen, 39.dp, 0.6f) {
                            if (number.isNotBlank()) {
                                startCall(number, number)
                            }
                        }
                        .clip(CircleShape).background(CallGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    Modifier.size(78.dp)
                        .neonPress(EndRed, 39.dp) { number = number.dropLast(1) }
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (number.isNotEmpty()) Text("\u232B", fontSize = 28.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyButton(digit: String, letters: String, onClick: () -> Unit) {
    Box(
        Modifier.size(78.dp)
            .neonPress(GlowKeypad, 39.dp, 0f, onClick)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, fontSize = 30.sp, fontWeight = FontWeight.Light)
            if (letters.isNotEmpty()) {
                Text(letters, fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
