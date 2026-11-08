package com.orra.app

import androidx.compose.foundation.isSystemInDarkTheme
import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// ORRA settings: Telegram-style hub with one page per section.
// ============================================================================

@Composable
fun SettingsScreen() {
    var route by remember { mutableStateOf(OrraNav.settingsRoute.value ?: "home") }
    LaunchedEffect(OrraNav.settingsRoute.value) { OrraNav.settingsRoute.value?.let { route = it; OrraNav.settingsRoute.value = null } }
    BackHandler(enabled = route != "home") { route = "home" }
    val back = { route = "home" }
    when (route) {
        "account" -> SettingsPage(tr("account"), back) { AccountPage() }
        "chat" -> SettingsPage(tr("chat_settings"), back) { ChatSettingsPage() }
        "privacy" -> SettingsPage(tr("privacy_security"), back) { PrivacyPage() }
        "notifications" -> SettingsPage(tr("notifications"), back) { NotificationsPage() }
        "data" -> SettingsPage(tr("data_storage"), back) { DataPage() }
        "folders" -> SettingsPage(tr("folders"), back) { FoldersPage() }
        "devices" -> SettingsPage(tr("devices"), back) { DevicesPage() }
        "power" -> SettingsPage(tr("power_saving"), back) { PowerPage() }
        "language" -> SettingsPage(tr("language"), back) { LanguagePage() }
        "sounds" -> SettingsPage(tr("sounds"), back) { SoundsPage() }
        "security" -> SettingsPage(tr("security"), back) { SecurityPage() }
        "about" -> SettingsPage(tr("about"), back) { AboutPage() }
        else -> SettingsHome { route = it }
    }
}

@Composable
private fun SettingsHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { OrraPrefs.get(context) }
    val name = prefs.getString("profile_display_name", "").orEmpty()
    val username = prefs.getString("profile_username", "").orEmpty().ifBlank { prefs.getString("username", "").orEmpty() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 58.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text(name.ifBlank { "O" }.first().uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(name.ifBlank { "ORRA" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(if (username.isNotBlank()) "@$username" else tr("set_username"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        OrraCard {
            val dark = OrraAppearance.mode.value == "dark" || (OrraAppearance.mode.value == "system" && isSystemInDarkTheme())
            SwitchRow(tr("dark_mode"), null, dark) { OrraAppearance.setMode(context, if (it) "dark" else "light") }
        }
        OrraCard {
            SettingsRow(Icons.Filled.AccountCircle, Color(0xFF2E9BFF), tr("account"), tr("account_sub")) { open("account") }
            SettingsRow(Icons.Filled.ChatBubble, Color(0xFFF59E0B), tr("chat_settings"), tr("chat_settings_sub")) { open("chat") }
            SettingsRow(Icons.Filled.VpnKey, Color(0xFF34C759), tr("privacy_security"), tr("privacy_sub")) { open("privacy") }
            SettingsRow(Icons.Filled.Notifications, Color(0xFFEF4455), tr("notifications"), tr("notifications_sub")) { open("notifications") }
            SettingsRow(Icons.Filled.PieChart, Color(0xFF5B6CFF), tr("data_storage"), tr("data_sub")) { open("data") }
            SettingsRow(Icons.Filled.Folder, Color(0xFF2E9BFF), tr("folders"), tr("folders_sub")) { open("folders") }
            SettingsRow(Icons.Filled.Laptop, Color(0xFF14B8A6), tr("devices"), tr("devices_sub")) { open("devices") }
            SettingsRow(Icons.Filled.BatteryChargingFull, Color(0xFFF97316), tr("power_saving"), tr("power_sub")) { open("power") }
            SettingsRow(Icons.Filled.Notifications, Color(0xFFEC4899), tr("sounds"), tr("sounds_sub")) { open("sounds") }
            SettingsRow(Icons.Filled.VpnKey, Color(0xFF16A34A), tr("security"), tr("security_sub")) { open("security") }
            SettingsRow(Icons.Filled.Language, Color(0xFF9B6BFF), tr("language"), OrraLang.nativeName(OrraLang.code.value, tr("system_language"))) { open("language") }
        }
        OrraCard {
            SettingsRow(Icons.Filled.Share, Color(0xFF2E9BFF), tr("share_app"), tr("share_sub")) { shareOrra(context) }
            SettingsRow(Icons.Filled.Info, Color(0xFF64748B), tr("about"), "Version " + BuildConfigVersion.name(context)) { open("about") }
        }
        OrraDeveloperBadge(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 8.dp))
    }
}

private fun shareOrra(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, OrraLang.t("share_text") + " https://github.com/Mahmoudyasse/Orra-beta")
    context.startActivity(Intent.createChooser(intent, OrraLang.t("share_app")))
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 54.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back")) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content
        )
    }
}

@Composable
private fun OrraCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun SettingsRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
fun OrraDeveloperBadge(modifier: Modifier = Modifier) {
    val accent = OrraAppearance.accentColor()
    val shape = RoundedCornerShape(50)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("developer").uppercase(), fontSize = 10.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Row(
            Modifier.clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF05060A), Color(0xFF14172A), Color(0xFF07080E))), shape)
                .border(1.dp, accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            BasicText(
                "HAMOUDY YASSER",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(accent, Color.White, accent)),
                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
                    fontSize = 11.sp, letterSpacing = 4.sp
                )
            )
        }
    }
}

// ------------------------------------------------------------------ Chat settings

@Composable
private fun ChatSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { OrraPrefs.get(context) }
    val mode = OrraAppearance.mode.value
    val accent = OrraAppearance.accent.value
    var animations by remember { mutableStateOf(prefs.getBoolean("chat_animations", true)) }
    val wallpaper = remember(OrraWallpaper.version.intValue) { OrraWallpaper.current(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { OrraWallpaper.setCustom(context, uri) } }
    }
    SectionTitle(tr("appearance"))
    OrraCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val darkNow = OrraAppearance.mode.value == "dark" || (OrraAppearance.mode.value == "system" && isSystemInDarkTheme())
            SwitchRow(tr("dark_mode"), null, darkNow) { OrraAppearance.setMode(context, if (it) "dark" else "light") }
            Text(tr("night_mode"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to tr("mode_system"), "dark" to tr("mode_dark"), "light" to tr("mode_light")).forEach { (value, label) ->
                    ChoiceChip(label, mode == value) { OrraAppearance.setMode(context, value) }
                }
            }
            Text(tr("liquid_accent"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("liquid" to Mint, "blue" to Color(0xFF54A8FF), "violet" to Color(0xFF9B8CFF),
                    "green" to Color(0xFF55E6A5), "rose" to Color(0xFFFF76AE)).forEach { (value, color) ->
                    val selected = accent == value
                    Surface(
                        modifier = Modifier.weight(1f).border(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                        onClick = { OrraAppearance.setAccent(context, value) },
                        shape = RoundedCornerShape(18.dp), color = color.copy(alpha = if (selected) 0.28f else 0.12f)
                    ) { Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("●", color = color) } }
                }
            }
            Note(tr("liquid_note"))
        }
        SwitchRow(tr("dynamic_island"), tr("dynamic_island_sub"), OrraEye.island.value) { OrraEye.island.value = it; OrraEye.save(context) }
        SwitchRow(tr("animations"), null, animations) { animations = it; prefs.edit().putBoolean("chat_animations", it).apply() }
    }
    SectionTitle(tr("chat_wallpaper"))
    OrraCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("current") + ": " + tr("wp_" + wallpaper.lowercase()))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrraWallpaper.presets.forEach { preset ->
                    ChoiceChip(tr("wp_" + preset.lowercase()), wallpaper == preset) { OrraWallpaper.setPreset(context, preset) }
                }
            }
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("wallpaper_from_phone"))
            }
            Note(tr("wallpaper_note"))
        }
    }
}

// ------------------------------------------------------------------ Privacy & security

@Composable
private fun PrivacyPage() {
    val context = LocalContext.current
    val prefs = remember { OrraPrefs.get(context) }
    var hideSeen by remember { mutableStateOf(prefs.getBoolean("hide_last_seen", false)) }
    var chats by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var setFor by remember { mutableStateOf<OrraChatConversation?>(null) }
    var removeFor by remember { mutableStateOf<OrraChatConversation?>(null) }
    val lockVersion = OrraLocks.version.intValue
    LaunchedEffect(Unit) {
        val client = orraClient(context)
        if (client == null) status = tr("sign_in_first")
        else runCatching { chats = withContext(Dispatchers.IO) { client.loadConversations() } }.onFailure { status = tr("load_failed") }
    }
    OrraCard { SwitchRow(tr("hide_last_seen"), tr("hide_last_seen_sub"), hideSeen) { hideSeen = it; prefs.edit().putBoolean("hide_last_seen", it).apply() } }
    SectionTitle(tr("chat_passwords"))
    Note(tr("chat_passwords_sub"))
    OrraCard {
        if (chats.isEmpty()) Text(status.ifBlank { tr("no_chats") }, Modifier.padding(16.dp))
        chats.forEach { chat ->
            val locked = lockVersion >= 0 && OrraLocks.isChatLocked(context, chat.id)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = null,
                    tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(chat.username, maxLines = 1)
                    Text(if (locked) tr("locked") else tr("not_locked"), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { if (locked) removeFor = chat else setFor = chat }) { Text(if (locked) tr("remove") else tr("set_password")) }
            }
        }
    }
    setFor?.let { chat ->
        OrraSetPasswordDialog(chat.username, { setFor = null }) { pw -> OrraLocks.set(context, OrraLocks.chatKey(chat.id), pw); setFor = null }
    }
    removeFor?.let { chat ->
        OrraUnlockDialog(chat.username, OrraLocks.chatKey(chat.id), { removeFor = null }) {
            OrraLocks.remove(context, OrraLocks.chatKey(chat.id)); removeFor = null
        }
    }
}

// ------------------------------------------------------------------ Notifications

@Composable
private fun NotificationsPage() {
    val context = LocalContext.current
    val prefs = remember { OrraPrefs.get(context) }
    var messages by remember { mutableStateOf(prefs.getBoolean("message_notifications", true)) }
    var calls by remember { mutableStateOf(prefs.getBoolean("call_notifications", true)) }
    var vibration by remember { mutableStateOf(prefs.getBoolean("notification_vibration", true)) }
    var allowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { OrraRinger.stop() } }
    OrraCard {
        SwitchRow(tr("message_notifications"), null, messages) { messages = it; prefs.edit().putBoolean("message_notifications", it).apply() }
        SwitchRow(tr("call_notifications"), null, calls) { calls = it; prefs.edit().putBoolean("call_notifications", it).apply() }
        SwitchRow(tr("vibration"), null, vibration) { vibration = it; prefs.edit().putBoolean("notification_vibration", it).apply() }
    }
    SectionTitle(tr("message_tone"))
    OrraCard { SoundPicker(call = false) }
    SectionTitle(tr("call_ringtone"))
    OrraCard { SoundPicker(call = true) }
    Note(tr("sound_note"))
    OrraCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!allowed && Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text(tr("allow_notifications")) }
            }
            OutlinedButton(onClick = { OrraNotify.show(context, "message", "ORRA", tr("test_body")) }, modifier = Modifier.fillMaxWidth()) { Text(tr("send_test")) }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("system_notification_settings")) }
        }
    }
}

@Composable
private fun SoundPicker(call: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(OrraSounds.selection(context, call)) }
    var customs by remember { mutableStateOf(OrraSounds.customSounds(context)) }
    fun choose(value: String) {
        selection = value
        OrraSounds.setSelection(context, call, value)
        OrraRinger.play(context, value, call, false)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val added = withContext(Dispatchers.IO) { OrraSounds.addCustom(context, uri) }
            if (added != null) { customs = OrraSounds.customSounds(context); choose("custom:" + added.id) }
        }
    }
    val options = buildList<Pair<String, String>> {
        add(OrraSounds.DEFAULT to tr("sound_default"))
        add(OrraSounds.SILENT to tr("sound_silent"))
        (if (call) OrraSounds.ringtones else OrraSounds.notificationTones).forEach { add("builtin:" + it.resName to it.label) }
        customs.forEach { add("custom:" + it.id to it.name) }
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        options.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { choose(value) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selection == value, onClick = { choose(value) })
                Text(label, Modifier.weight(1f), maxLines = 1)
                if (value.startsWith("custom:")) {
                    TextButton(onClick = {
                        OrraSounds.removeCustom(context, value.removePrefix("custom:"))
                        customs = OrraSounds.customSounds(context); selection = OrraSounds.selection(context, call)
                    }) { Text(tr("remove")) }
                }
            }
        }
        OutlinedButton(onClick = { pick.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(tr("add_sound_from_phone"))
        }
    }
}

// ------------------------------------------------------------------ Data & storage

@Composable
private fun DataPage() {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var wifiOnly by remember { mutableStateOf(OrraDownloads.wifiOnly(context)) }
    var cleared by remember { mutableStateOf("") }
    val refresh = rev
    Note(tr("auto_download_note"))
    listOf(false, true).forEach { group ->
        SectionTitle(tr(if (group) "groups" else "private_chats"))
        OrraCard {
            OrraDownloads.kinds.forEach { kind ->
                SwitchRow(tr("media_$kind"), null, refresh >= 0 && OrraDownloads.enabled(context, kind, group)) {
                    OrraDownloads.setEnabled(context, kind, group, it); rev++
                }
            }
        }
    }
    OrraCard {
        SwitchRow(tr("wifi_only"), tr("wifi_only_sub"), wifiOnly) { wifiOnly = it; OrraPrefs.get(context).edit().putBoolean("dl_wifi_only", it).apply() }
    }
    SectionTitle(tr("storage"))
    OrraCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cacheKb = runCatching { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 }.getOrDefault(0L)
            Text(tr("cache_size") + ": " + cacheKb + " KB", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val ok = runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; true }.getOrDefault(false)
                cleared = if (ok) tr("cache_cleared") else tr("cache_failed")
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("clear_cache")) }
            if (cleared.isNotBlank()) Text(cleared, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------------ Folders

@Composable
private fun FoldersPage() {
    val context = LocalContext.current
    var chats by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OrraFolder?>(null) }
    var pendingUnlock by remember { mutableStateOf<Pair<OrraFolder, () -> Unit>?>(null) }
    val folders = OrraFolders.list(context)
    LaunchedEffect(Unit) {
        val client = orraClient(context)
        if (client != null) runCatching { chats = withContext(Dispatchers.IO) { client.loadConversations() } }
    }
    fun guard(folder: OrraFolder, action: () -> Unit) {
        if (OrraFolders.isLocked(context, folder.id)) pendingUnlock = folder to action else action()
    }
    Note(tr("folders_note"))
    Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("new_folder"))
    }
    if (folders.isNotEmpty()) {
        OrraCard {
            folders.forEach { folder ->
                val locked = OrraLocks.has(context, OrraFolders.folderKey(folder.id))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(folder.name, maxLines = 1)
                        Text(folder.chatIds.size.toString() + " " + tr("chats_count"), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { guard(folder) { editing = folder } }) { Text(tr("edit")) }
                    TextButton(onClick = { guard(folder) { OrraFolders.delete(context, folder.id) } }) { Text(tr("delete")) }
                }
            }
        }
    }
    if (creating || editing != null) {
        FolderEditorDialog(editing, chats, onDismiss = { creating = false; editing = null }) { name, ids, password, removePassword ->
            val existing = editing
            if (existing == null) {
                OrraFolders.create(context, name, ids, password)
            } else {
                OrraFolders.save(context, OrraFolders.list(context).map { if (it.id == existing.id) it.copy(name = name.trim(), chatIds = ids) else it })
                if (removePassword) OrraLocks.remove(context, OrraFolders.folderKey(existing.id))
                else if (password.isNotBlank()) OrraLocks.set(context, OrraFolders.folderKey(existing.id), password)
            }
            creating = false; editing = null
        }
    }
    pendingUnlock?.let { (folder, action) ->
        OrraUnlockDialog(folder.name, OrraFolders.folderKey(folder.id), { pendingUnlock = null }) { pendingUnlock = null; action() }
    }
}

@Composable
private fun FolderEditorDialog(
    existing: OrraFolder?, chats: List<OrraChatConversation>, onDismiss: () -> Unit,
    onSave: (String, Set<String>, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var removePassword by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(existing?.chatIds ?: emptySet()) }
    val hasPassword = existing != null && OrraLocks.has(context, OrraFolders.folderKey(existing.id))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (existing == null) "new_folder" else "edit_folder")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text(tr("folder_name")) })
                OutlinedTextField(password, { password = it }, singleLine = true, label = { Text(tr(if (hasPassword) "new_password_keep" else "folder_password_optional")) },
                    visualTransformation = PasswordVisualTransformation())
                if (hasPassword) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(removePassword, { removePassword = it }); Text(tr("remove_password"))
                }
                Text(tr("choose_chats"), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (chats.isEmpty()) Text(tr("no_chats"), style = MaterialTheme.typography.bodySmall)
                    chats.forEach { chat ->
                        Row(Modifier.fillMaxWidth().clickable { selected = if (chat.id in selected) selected - chat.id else selected + chat.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(chat.id in selected, { selected = if (it) selected + chat.id else selected - chat.id })
                            Text(chat.username, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (password.isEmpty() || password.length >= 4),
                onClick = { onSave(name, selected, password, removePassword) }) { Text(tr("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } }
    )
}

// ------------------------------------------------------------------ Devices

@Composable
private fun DevicesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<OrraSession>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val myDevice = remember { OrraDevice.id(context) }
    fun reload() {
        scope.launch {
            busy = true
            val client = orraClient(context)
            if (client == null) status = tr("sign_in_first")
            else {
                status = ""
                runCatching { sessions = withContext(Dispatchers.IO) { client.loadSessions() } }.onFailure { status = tr("devices_setup") }
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    SectionTitle(tr("this_device"))
    OrraCard {
        Column(Modifier.padding(16.dp)) {
            Text(OrraDevice.name(), fontWeight = FontWeight.SemiBold)
            Text("Android " + Build.VERSION.RELEASE + " · ORRA " + BuildConfigVersion.name(context), style = MaterialTheme.typography.bodySmall)
            Text(tr("online_now"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    SectionTitle(tr("active_sessions"))
    if (status.isNotBlank()) Note(status)
    val others = sessions.filter { it.deviceId != myDevice }
    OrraCard {
        if (others.isEmpty() && status.isBlank()) Text(if (busy) tr("loading") else tr("no_other_sessions"), Modifier.padding(16.dp))
        others.forEach { session ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Laptop, contentDescription = null)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(session.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(session.os + " · ORRA " + session.appVersion, style = MaterialTheme.typography.bodySmall)
                    Text(tr("last_active") + ": " + relativeTime(session.lastActive), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { orraClient(context)?.terminateSession(session.id) } }
                        reload()
                    }
                }) { Text(tr("terminate")) }
            }
        }
    }
    if (others.isNotEmpty()) {
        OutlinedButton(onClick = {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { orraClient(context)?.terminateOtherSessions(context) } }
                reload()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr("terminate_all_others")) }
    }
    Note(tr("sessions_note"))
}

private fun relativeTime(iso: String): String = runCatching {
    android.text.format.DateUtils.getRelativeTimeSpanString(java.time.Instant.parse(iso).toEpochMilli(), System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
}.getOrDefault(iso)

// ------------------------------------------------------------------ Power saving

@Composable
private fun PowerPage() {
    val context = LocalContext.current
    val prefs = remember { OrraPrefs.get(context) }
    var power by remember { mutableStateOf(prefs.getBoolean("power_saving", false)) }
    var auto by remember { mutableStateOf(prefs.getBoolean("power_auto_low", true)) }
    var rev by remember { mutableIntStateOf(0) }
    val refresh = rev
    OrraCard {
        SwitchRow(tr("power_saving_on"), tr("power_saving_on_sub"), power) { power = it; prefs.edit().putBoolean("power_saving", it).apply(); rev++ }
        SwitchRow(tr("power_auto_low"), null, auto) { auto = it; prefs.edit().putBoolean("power_auto_low", it).apply(); rev++ }
    }
    Note((if (refresh >= 0 && OrraPower.active(context)) tr("power_active") else tr("power_inactive")) + "\n" + tr("power_effects"))
    SectionTitle(tr("eye_protection"))
    OrraCard {
        SwitchRow(tr("eye_protection_on"), tr("eye_protection_sub"), OrraEye.enabled.value) { OrraEye.enabled.value = it; OrraEye.save(context) }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(tr("eye_intensity"), style = MaterialTheme.typography.labelLarge)
            Slider(value = OrraEye.level.value, onValueChange = { OrraEye.level.value = it }, onValueChangeFinished = { OrraEye.save(context) })
        }
        SwitchRow(tr("eye_auto"), tr("eye_auto_sub"), OrraEye.auto.value) { OrraEye.auto.value = it; OrraEye.save(context) }
    }
}

// ------------------------------------------------------------------ Language

@Composable
private fun LanguagePage() {
    val context = LocalContext.current
    OrraCard {
        OrraLang.languages.forEach { (code, nativeName) ->
            val label = if (code == "system") tr("system_language") else nativeName
            Row(Modifier.fillMaxWidth().clickable { OrraLang.set(context, code) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = OrraLang.code.value == code, onClick = { OrraLang.set(context, code) })
                Text(label)
            }
        }
    }
    Note(tr("language_note"))
}

// ------------------------------------------------------------------ App sounds and security

@Composable
private fun SoundsPage() {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val refresh = rev
    Note(tr("sfx_note"))
    OrraCard {
        SwitchRow(tr("sfx_master"), null, refresh >= 0 && OrraSfx.masterEnabled(context)) { OrraSfx.setMaster(context, it); rev++ }
    }
    OrraCard {
        OrraSfx.events.forEach { event ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(tr("sfx_$event")) }
                TextButton(onClick = {
                    OrraSfx.init(context)
                    val was = OrraSfx.enabled(context, event); val wasMaster = OrraSfx.masterEnabled(context)
                    OrraSfx.setEnabled(context, event, true); OrraSfx.setMaster(context, true)
                    OrraSfx.play(context, event)
                    OrraSfx.setEnabled(context, event, was); OrraSfx.setMaster(context, wasMaster)
                }) { Text(tr("sfx_preview")) }
                Switch(checked = refresh >= 0 && OrraSfx.enabled(context, event), onCheckedChange = { OrraSfx.setEnabled(context, event, it); rev++ })
            }
        }
    }
}

@Composable
private fun SecurityPage() {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val refresh = rev
    var message by remember { mutableStateOf("") }
    val rooted = remember { OrraSecurity.looksRooted() }
    OrraCard {
        SwitchRow(tr("secure_screen"), tr("secure_screen_sub"), OrraSecurity.secureScreen.value) { OrraSecurity.setSecureScreen(context, it) }
        SwitchRow(tr("app_lock"), tr("app_lock_sub"), refresh >= 0 && OrraSecurity.appLockEnabled(context)) { enable ->
            if (enable && !OrraSecurity.deviceSecure(context)) message = tr("set_device_lock")
            else { message = ""; OrraSecurity.setAppLock(context, enable); rev++ }
        }
    }
    if (message.isNotBlank()) Note(message)
    SectionTitle(tr("device_integrity"))
    OrraCard {
        Column(Modifier.padding(16.dp)) {
            Text(if (rooted) tr("integrity_warn") else tr("integrity_ok"),
                color = if (rooted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
    SectionTitle(tr("protection_active"))
    Note(tr("protection_list"))
}

// ------------------------------------------------------------------ About

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    OrraCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Version " + BuildConfigVersion.name(context))
            OrraDeveloperBadge(Modifier.padding(vertical = 8.dp))
            Text(tr("about_text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
    Button(onClick = { shareOrra(context) }, modifier = Modifier.fillMaxWidth()) { Text(tr("share_app")) }
}

// ============================================================================
// Account page (profile, cloud backup, proxy, permissions, blocked contacts)
// ============================================================================


@Composable
fun AccountPage() {
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text((displayName.ifBlank { "O" }.first()).uppercase(), modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Text(displayName.ifBlank { "ORRA account" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (username.isNotBlank()) "@$username" else "Set your username and bio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                OutlinedButton(onClick = { showAccountEditor = true }) { Text(tr("s_2fc1a870")) }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("s_87dc59ee"), fontWeight = FontWeight.SemiBold)
                Text(tr("s_41ff4e90"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("s_c6d33022"), modifier = Modifier.weight(1f))
                    Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                }
                OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(tr("s_ba6d4bc8")) })
                OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("s_eb44ac81")) })
                Text(tr("s_6002ddee"), style = MaterialTheme.typography.labelLarge)
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
                }) { Text(tr("s_ee3e8d60")) }
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
                if (backupClient == null) Text(tr("s_bb5ef909"), style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(tr("s_e9153e53"), fontWeight = FontWeight.SemiBold)
                Text(tr("s_a9cdaa9f"),
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
                }, modifier = Modifier.fillMaxWidth()) { Text(tr("s_bc411b37")) }
                if (backupClient == null) Text(tr("s_3c661f28"), style = MaterialTheme.typography.bodySmall)
            }
        }

        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null)
                    Text("  " + tr("s_f370c6a0"), fontWeight = FontWeight.SemiBold)
                }
                Text(tr("s_f14d5765"),
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
                }) { Text(tr("s_7b10b435")) }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }) {
                    androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null)
                    Text("  " + tr("s_7fd303ec"))
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
                }) { Text(tr("s_f6a9223f")) }

            }
        }



    }

    if (showAccountEditor) {
        AlertDialog(onDismissRequest = { showAccountEditor = false }, title = { Text(tr("s_11039552")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(tr("s_bc9e270b")) }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } }, label = { Text(tr("s_f6039d44")) }, singleLine = true)
                OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text(tr("s_713a01ab")) })
            } },
            confirmButton = { TextButton(onClick = { prefs.edit().putString("profile_display_name", displayName).putString("profile_username", username).putString("profile_bio", bio).apply(); showAccountEditor = false }) { Text(tr("s_c9cc8cce")) } },
            dismissButton = { TextButton(onClick = { showAccountEditor = false }) { Text(tr("s_ea478870")) } })
    }
    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            title = { Text(tr("s_f6a9223f")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (blockedUsers.isEmpty()) Text(tr("s_a3d465f7"))
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
                            }) { Text(tr("s_193cb08a")) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text(tr("s_d3d2e617")) } }
        )
    }
}
