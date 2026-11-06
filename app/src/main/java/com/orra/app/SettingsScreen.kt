package com.orra.app

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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
    var proxyHost by remember { mutableStateOf(prefs.getString("proxy_host", "").orEmpty()) }
    var proxyPort by remember { mutableStateOf(prefs.getInt("proxy_port", 0).takeIf { it > 0 }?.toString().orEmpty()) }
    var proxyType by remember { mutableStateOf(prefs.getString("proxy_type", "http").orEmpty()) }
    var proxyEnabled by remember { mutableStateOf(prefs.getBoolean("proxy_enabled", false)) }
    var proxyStatus by remember { mutableStateOf("") }
    var proxyBusy by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    var showBlocked by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf<List<OrraBlockedUser>>(emptyList()) }
    var blockedStatus by remember { mutableStateOf("") }
    var isDefaultDialer by remember { mutableStateOf(isOrraDefaultDialer(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultDialer = isOrraDefaultDialer(context)
    }
    val mode = OrraAppearance.mode.value
    val accent = OrraAppearance.accent.value
    var showAccountEditor by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(prefs.getString("profile_display_name", "").orEmpty()) }
    var username by remember { mutableStateOf(prefs.getString("profile_username", "").orEmpty()) }
    var bio by remember { mutableStateOf(prefs.getString("profile_bio", "").orEmpty()) }
    var chatWallpaper by remember { mutableStateOf(prefs.getString("chat_wallpaper", "Default").orEmpty()) }
    var chatAnimations by remember { mutableStateOf(prefs.getBoolean("chat_animations", true)) }
    var messageNotifications by remember { mutableStateOf(prefs.getBoolean("message_notifications", true)) }
    var callNotifications by remember { mutableStateOf(prefs.getBoolean("call_notifications", true)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("notification_vibration", true)) }
    var powerSaving by remember { mutableStateOf(prefs.getBoolean("power_saving", false)) }
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "System").orEmpty()) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var storageStatus by remember { mutableStateOf("") }

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
                val developerAccent = OrraAppearance.accentColor()
                Surface(
                    modifier = Modifier.border(
                        1.dp,
                        developerAccent.copy(alpha = if (mode == "dark") 0.58f else 0.34f),
                        RoundedCornerShape(50)
                    ),
                    shape = RoundedCornerShape(50),
                    color = developerAccent.copy(alpha = if (mode == "dark") 0.11f else 0.07f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("●", color = developerAccent, style = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = developerAccent.copy(alpha = if (mode == "dark") 0.72f else 0.30f),
                                offset = Offset.Zero,
                                blurRadius = 10f
                            )
                        ))
                        Text(
                            "DEVELOPER  /  HAMOUDY YASSER",
                            fontWeight = FontWeight.SemiBold,
                            color = developerAccent,
                            letterSpacing = 1.1.sp,
                            style = MaterialTheme.typography.labelLarge.copy(
                                shadow = Shadow(
                                    color = developerAccent.copy(alpha = if (mode == "dark") 0.48f else 0.18f),
                                    offset = Offset.Zero,
                                    blurRadius = 12f
                                )
                            )
                        )
                    }
                }
                Text("Version 0.5.0-beta")
                Text("Built with Kotlin, Jetpack Compose, Android Telecom and Supabase.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("ORRA is an independent beta app and is not affiliated with WhatsApp or Apple.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }



        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connection proxy", fontWeight = FontWeight.SemiBold)
                Text("Routes ORRA's Supabase API requests through this proxy. It does not route Android phone calls or every third-party connection.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use proxy", modifier = Modifier.weight(1f))
                    Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                }
                OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Proxy host / IP") })
                OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Port (1–65535)") })
                Text("Proxy type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (proxyType == "http") Button(onClick = { proxyType = "http" }) { Text("HTTP") }
                    else OutlinedButton(onClick = { proxyType = "http" }) { Text("HTTP") }
                    if (proxyType == "socks") Button(onClick = { proxyType = "socks" }) { Text("SOCKS") }
                    else OutlinedButton(onClick = { proxyType = "socks" }) { Text("SOCKS") }
                }
                Button(enabled = !proxyBusy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val port = proxyPort.toIntOrNull()
                    if (proxyEnabled && (proxyHost.isBlank() || port == null || port !in 1..65535)) {
                        proxyStatus = "Enter a valid proxy host and port."
                    } else {
                        SupabaseChatClient.saveProxy(context, proxyHost, port ?: 0, proxyType, proxyEnabled)
                        proxyStatus = "Proxy settings saved."
                    }
                }) { Text("Save proxy settings") }
                OutlinedButton(enabled = !proxyBusy && backupClient != null, modifier = Modifier.fillMaxWidth(), onClick = {
                    val port = proxyPort.toIntOrNull()
                    if (proxyEnabled && (proxyHost.isBlank() || port == null || port !in 1..65535)) {
                        proxyStatus = "Enter a valid proxy host and port first."
                    } else {
                        SupabaseChatClient.saveProxy(context, proxyHost, port ?: 0, proxyType, proxyEnabled)
                        scope.launch {
                            proxyBusy = true
                            proxyStatus = try {
                                withContext(Dispatchers.IO) { backupClient!!.testConnection() }
                                "Connected: Supabase API is reachable using the current connection settings."
                            } catch (e: Exception) { android.util.Log.w("ORRA", "Proxy test failed", e); "" }
                            proxyBusy = false
                        }
                    }
                }) { Text(if (proxyBusy) "Testing…" else "Test connection") }
                if (backupClient == null) Text("Sign in to test the Supabase connection.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Account & chat backup", fontWeight = FontWeight.SemiBold)
                Text("Your ORRA account and chats are stored in the connected server. A private chat snapshot is created daily when you open Chats. Media files remain in the private storage bucket.",
                    style = MaterialTheme.typography.bodySmall)
                Button(enabled = backupClient != null && !backupBusy, onClick = {
                    scope.launch {
                        backupBusy = true
                        backupStatus = try {
                            val id = withContext(Dispatchers.IO) { backupClient!!.createCloudBackupNow() }
                            "Backup saved: " + id.take(8)
                        } catch (e: Exception) { android.util.Log.w("ORRA", "Backup failed", e); "" }
                        backupBusy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (backupBusy) "Working…" else "Back up chats now") }
                OutlinedButton(enabled = backupClient != null && !backupBusy, onClick = {
                    scope.launch {
                        backupBusy = true
                        backupStatus = try {
                            val count = withContext(Dispatchers.IO) { backupClient!!.restoreLatestCloudBackup() }
                            "Restore complete. $count missing messages restored."
                        } catch (e: Exception) { android.util.Log.w("ORRA", "Restore failed", e); "" }
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
                Text(if (notificationsAllowed) "Notifications permission is enabled." else "Notifications permission is not enabled.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(if (notificationsAllowed) "Notification settings" else "Enable notifications") }
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
                        }.onFailure { android.util.Log.w("ORRA", "Blocked contacts load failed", it); blockedStatus = "" }
                    }
                }) { Text("Blocked contacts") }

            }
        }



        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account", fontWeight = FontWeight.SemiBold)
                Text(if (displayName.isBlank()) "Set your profile name, username and bio" else displayName, style = MaterialTheme.typography.titleMedium)
                Text(listOf(username.takeIf { it.isNotBlank() }?.let { "@$it" }, bio.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ").ifBlank { "Profile details are saved on this device" }, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showAccountEditor = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit profile details") }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chat Settings", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Chat wallpaper"); Text(chatWallpaper, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = { showWallpaperPicker = true }) { Text("Change") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chat animations", modifier = Modifier.weight(1f))
                    Switch(checked = chatAnimations, onCheckedChange = { chatAnimations = it; prefs.edit().putBoolean("chat_animations", it).apply() })
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notifications", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Message notifications", modifier = Modifier.weight(1f)); Switch(checked = messageNotifications, onCheckedChange = { messageNotifications = it; prefs.edit().putBoolean("message_notifications", it).apply() }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Call notifications", modifier = Modifier.weight(1f)); Switch(checked = callNotifications, onCheckedChange = { callNotifications = it; prefs.edit().putBoolean("call_notifications", it).apply() }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Vibration", modifier = Modifier.weight(1f)); Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it; prefs.edit().putBoolean("notification_vibration", it).apply() }) }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("System notification sound and channels") }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Data and Storage", fontWeight = FontWeight.SemiBold)
                val cacheKb = runCatching { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 }.getOrDefault(0L)
                Text("Temporary cache: " + cacheKb + " KB", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val cleared = runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; true }.getOrDefault(false)
                    storageStatus = if (cleared) "Temporary cache cleared." else "Could not clear all cache files."
                }, modifier = Modifier.fillMaxWidth()) { Text("Clear temporary cache") }
                if (storageStatus.isNotBlank()) Text(storageStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Devices", fontWeight = FontWeight.SemiBold)
                Text("This device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, style = MaterialTheme.typography.bodySmall)
                Text("Remote session management requires server-side session support; this screen does not claim to show other devices.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Open Android security settings") }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Power Saving", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable power saving", modifier = Modifier.weight(1f)); Switch(checked = powerSaving, onCheckedChange = { powerSaving = it; prefs.edit().putBoolean("power_saving", it).apply() }) }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Language", fontWeight = FontWeight.SemiBold)
                Text(appLanguage)
                OutlinedButton(onClick = { showLanguagePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose language") }
                Text("Language selection is saved locally; full translation requires translated app resources.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ORRA Premium", fontWeight = FontWeight.SemiBold)
                Text("Premium plans, gifts and payments are not connected yet.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth()) { Text("Help, FAQ and privacy") }
            }
        }
    }

    if (showAccountEditor) {
        AlertDialog(onDismissRequest = { showAccountEditor = false }, title = { Text("Edit profile details") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text("Bio") })
            } },
            confirmButton = { TextButton(onClick = { prefs.edit().putString("profile_display_name", displayName).putString("profile_username", username).putString("profile_bio", bio).apply(); showAccountEditor = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAccountEditor = false }) { Text("Cancel") } })
    }
    if (showWallpaperPicker) {
        AlertDialog(onDismissRequest = { showWallpaperPicker = false }, title = { Text("Chat wallpaper") },
            text = { Column { listOf("Default", "Midnight", "Ocean", "Violet").forEach { option -> TextButton(onClick = { chatWallpaper = option; prefs.edit().putString("chat_wallpaper", option).apply(); showWallpaperPicker = false }) { Text(option) } } } },
            confirmButton = { TextButton(onClick = { showWallpaperPicker = false }) { Text("Close") } })
    }
    if (showLanguagePicker) {
        AlertDialog(onDismissRequest = { showLanguagePicker = false }, title = { Text("Language") },
            text = { Column { listOf("System", "English", "العربية").forEach { option -> TextButton(onClick = { appLanguage = option; prefs.edit().putString("app_language", option).apply(); showLanguagePicker = false }) { Text(option) } } } },
            confirmButton = { TextButton(onClick = { showLanguagePicker = false }) { Text("Close") } })
    }
    if (showHelp) {
        AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("ORRA Help") },
            text = { Text("ORRA is in beta. Premium, gifts and remote device sessions are not implemented yet.") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } })
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
                                    }.onFailure { android.util.Log.w("ORRA", "Unblock failed", it); blockedStatus = "" }
                                }
                            }) { Text("Unblock") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text("Close") } }
        )
    }
}
