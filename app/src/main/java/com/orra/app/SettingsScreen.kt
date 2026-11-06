package com.orra.app

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE) }
    val backupUserId = prefs.getString("user_id", "").orEmpty()
    val backupToken = prefs.getString("token", "").orEmpty()
    val backupClient = remember(backupUserId, backupToken) {
        if (backupUserId.isNotBlank() && backupToken.isNotBlank()) SupabaseChatClient(context, SupabaseChatClient.DEFAULT_PROJECT_URL, SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY, backupToken, backupUserId) else null
    }
    var backupStatus by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf<List<OrraBlockedUser>>(emptyList()) }
    var blockedStatus by remember { mutableStateOf("") }
    var isDefaultDialer by remember { mutableStateOf(isOrraDefaultDialer(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultDialer = isOrraDefaultDialer(context)
    }
    val mode = OrraAppearance.mode.value
    val accent = OrraAppearance.accent.value

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Palette, contentDescription = null)
                    Text("  Appearance", fontWeight = FontWeight.SemiBold)
                }
                Text("Display mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                        if (mode == value) Button(onClick = { OrraAppearance.setMode(context, value) }) { Text(label) }
                        else OutlinedButton(onClick = { OrraAppearance.setMode(context, value) }) { Text(label) }
                    }
                }
                Text("Liquid accent", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("liquid" to Mint, "blue" to Color(0xFF54A8FF), "violet" to Color(0xFF9B8CFF),
                        "green" to Color(0xFF55E6A5), "rose" to Color(0xFFFF76AE)).forEach { (value, color) ->
                        val selected = accent == value
                        Surface(
                            modifier = Modifier.weight(1f).background(Color.Transparent, RoundedCornerShape(18.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(18.dp)),
                            onClick = { OrraAppearance.setAccent(context, value) },
                            shape = RoundedCornerShape(18.dp),
                            color = color.copy(alpha = if (selected) 0.28f else 0.12f)
                        ) { Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("●", color = color)
                        } }
                    }
                }
                Text("iPhone Island Liquid styling uses subtle translucent surfaces and restrained neon accents.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Account & chat backup", fontWeight = FontWeight.SemiBold)
                Text("Your ORRA account and chats are stored in the connected server. A private chat snapshot is created daily when you open Chats. Media files remain in the private storage bucket.",
                    style = MaterialTheme.typography.bodySmall)
                if (backupStatus.isNotBlank()) Text(backupStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Button(enabled = backupClient != null && !backupBusy, onClick = {
                    scope.launch {
                        backupBusy = true
                        backupStatus = try {
                            val id = withContext(Dispatchers.IO) { backupClient!!.createCloudBackupNow() }
                            "Backup saved: " + id.take(8)
                        } catch (e: Exception) { e.message ?: "Backup failed." }
                        backupBusy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (backupBusy) "Working…" else "Back up chats now") }
                OutlinedButton(enabled = backupClient != null && !backupBusy, onClick = {
                    scope.launch {
                        backupBusy = true
                        backupStatus = try {
                            val count = withContext(Dispatchers.IO) { backupClient!!.restoreLatestCloudBackup() }
                            "Restore complete. $count missing messages restored."
                        } catch (e: Exception) { e.message ?: "Restore failed." }
                        backupBusy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Restore latest backup") }
                if (backupClient == null) Text("Sign in to manage cloud backups.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null)
                    Text("  Privacy & permissions", fontWeight = FontWeight.SemiBold)
                }
                Text("Contacts, camera and microphone permissions are requested only when their features are used. Cellular call controls inside ORRA require setting it as the default phone app.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                Text(if (isDefaultDialer) "ORRA is your default phone app." else "ORRA is not the default phone app yet.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val manager = context.getSystemService(RoleManager::class.java)
                        if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true)
                            roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    } else (context as? Activity)?.let { requestOrraDialerRole(it) }
                }) { Text("Set ORRA as default phone app") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }) {
                    androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null)
                    Text("  App permissions")
                }
                OutlinedButton(enabled = backupClient != null, onClick = {
                    scope.launch {
                        blockedStatus = "Loading blocked contacts…"
                        runCatching {
                            blockedUsers = withContext(Dispatchers.IO) { backupClient!!.loadBlockedUsers() }
                            blockedStatus = ""
                            showBlocked = true
                        }.onFailure { blockedStatus = it.message ?: "Could not load blocked contacts." }
                    }
                }) { Text("Blocked contacts") }
                if (blockedStatus.isNotBlank()) Text(blockedStatus, style = MaterialTheme.typography.bodySmall)

            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Share ORRA", fontWeight = FontWeight.SemiBold)
                Text("Invite someone to install the ORRA beta app.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Try ORRA Beta: https://github.com/Mahmoudyasse/Orra-beta")
                    context.startActivity(Intent.createChooser(intent, "Share ORRA"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share app") }
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Info, contentDescription = null)
                    Text("  About ORRA", fontWeight = FontWeight.SemiBold)
                }
                Text("ORRA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("DEVELOPER  /  HAMOUDY YASSER", fontWeight = FontWeight.SemiBold, color = OrraAppearance.accentColor(), letterSpacing = 1.1.sp)
                Text("Version 0.4.0-beta")
                Text("Built with Kotlin, Jetpack Compose, Android Telecom and Supabase.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("ORRA is an independent beta app and is not affiliated with WhatsApp or Apple.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            title = { Text("Blocked contacts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (blockedUsers.isEmpty()) Text("You have not blocked anyone.")
                    blockedUsers.forEach { blocked ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${blocked.username}", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { backupClient!!.unblockUser(blocked.id) }
                                        blockedUsers = blockedUsers.filterNot { it.id == blocked.id }
                                    }.onFailure { blockedStatus = it.message ?: "Could not unblock user." }
                                }
                            }) { Text("Unblock") }
                        }
                    }
                    if (blockedStatus.isNotBlank()) Text(blockedStatus, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text("Close") } }
        )
    }
}
