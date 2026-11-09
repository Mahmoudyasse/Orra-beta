package com.orra.app

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** File safety, "view once / twice" markers and saving received media to the phone. */
object OrraMedia {
    private val blocked = setOf("apk", "apks", "xapk", "exe", "msi", "bat", "cmd", "com", "scr", "sh", "jar", "dex",
        "vbs", "js", "ps1", "dll", "so", "bin", "iso", "lnk", "reg", "hta", "wsf", "pif", "cpl")
    fun isDangerous(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in blocked
    fun viewLimit(body: String): Int = Regex("^orra:view(\\d+):").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    fun viewPrefix(limit: Int): String = if (limit > 0) "orra:view$limit:" else ""
    fun cleanBody(body: String): String = body.replace(Regex("^orra:view\\d+:"), "")
    /** Saves to Downloads/ORRA with Android's download manager. Returns false for blocked or failed downloads. */
    fun save(context: Context, url: String, rawName: String): Boolean {
        val name = rawName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "orra_file" }
        if (isDangerous(name)) return false
        return runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ORRA/$name")
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            true
        }.getOrDefault(false)
    }
}

private val EMOJIS = listOf("😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔", "😢", "😭", "😡", "👍", "👎", "👏", "🙏",
    "💪", "🎉", "❤️", "🔥", "✨", "🌹", "💔", "😴", "🤝", "👌", "✌️", "🙌", "😅", "😇", "🥰", "😋",
    "😜", "🤗", "😏", "😬", "🙄", "😱", "🤩", "🥳", "😤", "🤯", "😷", "🤒", "👀", "💯", "✅", "❌",
    "⭐", "🌙", "☀️", "🌈", "🍕", "☕", "🎵", "📷", "🚗", "✈️", "🏠", "📱", "💻", "🎮", "⚽", "🎁")

@Composable
fun OrraAvatar(name: String, url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).then(Modifier), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.size(size).clip(CircleShape))
        else Surface(modifier = Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.trim().take(1).uppercase().ifBlank { "O" }, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
            }
        }
    }
}

data class OrraMenuItem(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/** Back button, contact pill (avatar + name + status) and call / menu pill, laid out like Telegram. */
@Composable
fun OrraChatTopBar(
    title: String, status: String, statusColor: Color, avatarUrl: String?,
    onBack: () -> Unit, onOpenProfile: () -> Unit, onCall: (() -> Unit)?, menu: List<OrraMenuItem>
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pill = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = pill, onClick = onBack) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, contentDescription = tr("back")) }
        }
        Surface(modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(30.dp), color = pill, onClick = onOpenProfile) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OrraAvatar(title, avatarUrl, 38.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, fontSize = 11.sp, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Surface(modifier = Modifier.height(46.dp), shape = RoundedCornerShape(30.dp), color = pill) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCall != null) IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = tr("voice_call")) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = tr("more_options")) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label, color = if (item.danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
                                leadingIcon = { Icon(item.icon, contentDescription = null, tint = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                                onClick = { menuOpen = false; item.onClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rounded message bar (emoji, text, attach) with a round mic / send button. */
@Composable
fun OrraComposerBar(
    text: String, onText: (String) -> Unit, hint: String, canAttach: Boolean, recording: Boolean, busy: Boolean,
    onEmoji: () -> Unit, onAttach: () -> Unit, onMic: () -> Unit, onSend: () -> Unit
) {
    val hasText = text.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEmoji) { Icon(Icons.Default.EmojiEmotions, contentDescription = tr("emoji")) }
                TextField(
                    value = text, onValueChange = onText, modifier = Modifier.weight(1f), maxLines = 5,
                    placeholder = { Text(hint) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent
                    )
                )
                if (canAttach) IconButton(onClick = onAttach, enabled = !busy) { Icon(Icons.Default.AttachFile, contentDescription = tr("attach")) }
            }
        }
        Surface(
            modifier = Modifier.size(52.dp), shape = CircleShape, enabled = !busy || recording,
            color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            onClick = { if (hasText) onSend() else onMic() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (hasText) Icons.Default.Send else if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun OrraEmojiPanel(onPick: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().height(180.dp)) {
        items(EMOJIS) { emoji ->
            Box(Modifier.clickable { onPick(emoji) }.padding(6.dp), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrraFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** Telegram-style attachment sheet. Files come from the phone and are uploaded for the other person. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrraAttachSheet(
    viewLimit: Int, onViewLimit: (Int) -> Unit, onDismiss: () -> Unit,
    onGallery: () -> Unit, onCamera: () -> Unit, onFile: () -> Unit, onMusic: () -> Unit, onLocation: () -> Unit, onContact: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("media_visibility"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "view_unlimited", 1 to "view_once", 2 to "view_twice").forEach { (value, key) ->
                    FilterChip(selected = viewLimit == value, onClick = { onViewLimit(value) }, label = { Text(tr(key)) })
                }
            }
            Text(tr("view_limit_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            val items = listOf(
                Triple(Icons.Default.PhotoLibrary, "attach_gallery", onGallery), Triple(Icons.Default.CameraAlt, "attach_camera", onCamera),
                Triple(Icons.Default.InsertDriveFile, "attach_file", onFile), Triple(Icons.Default.MusicNote, "attach_music", onMusic),
                Triple(Icons.Default.LocationOn, "attach_location", onLocation), Triple(Icons.Default.Person, "attach_contact", onContact)
            )
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (icon, key, action) ->
                        Column(Modifier.clickable(onClick = action).padding(8.dp).width(88.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            Text(tr(key), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen contact page: cover, photo, status, quick actions, ID and phone number. */
@Composable
fun OrraPeerProfile(
    name: String, status: String, phone: String?, muted: Boolean, canCall: Boolean,
    onDismiss: () -> Unit, onMessage: () -> Unit, onToggleMute: () -> Unit, onCall: () -> Unit, onVideo: () -> Unit,
    avatarUrl: String? = null, coverUrl: String? = null, bio: String = "", username: String = "", userId: String? = null
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp).padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, onClick = onDismiss) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, contentDescription = tr("back")) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    if (!coverUrl.isNullOrBlank()) AsyncImage(coverUrl, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Surface(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Box(Modifier.align(Alignment.BottomCenter)) { OrraAvatar(name, avatarUrl, 120.dp) }
                }
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileTile(Icons.Default.Send, tr("profile_message"), true, onMessage)
                    ProfileTile(if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications, tr(if (muted) "profile_unmute" else "profile_mute"), true, onToggleMute)
                    ProfileTile(Icons.Default.Call, tr("voice_call"), canCall, onCall)
                    ProfileTile(Icons.Default.Videocam, tr("video_call"), canCall, onVideo)
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (bio.isNotBlank()) ProfileInfo(tr("profile_about"), bio)
                        if (username.isNotBlank()) ProfileInfo(tr("profile_username"), "@" + username)
                        if (!userId.isNullOrBlank()) ProfileInfo(tr("profile_id"), userId.take(8).uppercase())
                        if (!phone.isNullOrBlank()) ProfileInfo(tr("profile_mobile"), phone)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfo(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

/** Voice message recording bar: timer, delete and send. */
@Composable
fun OrraRecordingBar(seconds: Int, onCancel: () -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error) {}
                Text(String.format("%d:%02d", seconds / 60, seconds % 60), Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                Text(tr("recording"), Modifier.weight(1f).padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                IconButton(onClick = onCancel) { Icon(Icons.Default.Delete, contentDescription = tr("cancel_recording"), tint = MaterialTheme.colorScheme.error) }
            }
        }
        Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary, onClick = onSend) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ProfileTile(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.weight(1f).height(76.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        enabled = enabled, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            Text(label, fontSize = 12.sp, maxLines = 1, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        }
    }
}

/** Telegram-style conversation row. */
@Composable
fun OrraChatRow(name: String, preview: String, online: Boolean, locked: Boolean, isGroup: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            OrraAvatar(if (isGroup) "G" else name, null, 54.dp)
            if (online) Surface(modifier = Modifier.align(Alignment.BottomEnd).size(13.dp), shape = CircleShape, color = Color(0xFF34C759)) {}
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (locked) tr("locked") else preview, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun OrraSearchPill(value: String, onChange: (String) -> Unit, hint: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            TextField(
                value = value, onValueChange = onChange, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(hint) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

private data class MenuEntry(val route: String, val icon: ImageVector, val title: String, val sub: String?)

/** Profile header followed by the settings pages, listed one under another inside the 3-dot menu. */
@Composable
fun OrraSettingsMenu(name: String, phone: String, onProfile: () -> Unit, onOpen: (String) -> Unit) {
    val entries = listOf(
        MenuEntry("devices", androidx.compose.material.icons.Icons.Default.Laptop, "devices", "devices_sub"),
        MenuEntry("account", androidx.compose.material.icons.Icons.Default.VpnKey, "account", "account_sub"),
        MenuEntry("privacy", androidx.compose.material.icons.Icons.Default.Lock, "privacy_security", "privacy_sub"),
        MenuEntry("folders", androidx.compose.material.icons.Icons.Default.Folder, "folders", "folders_sub"),
        MenuEntry("chat", androidx.compose.material.icons.Icons.Default.ChatBubble, "chat_settings", "chat_settings_sub"),
        MenuEntry("broadcast", androidx.compose.material.icons.Icons.Default.Campaign, "broadcast", "broadcast_sub"),
        MenuEntry("events", androidx.compose.material.icons.Icons.Default.Event, "events", "events_sub"),
        MenuEntry("notifications", Icons.Default.Notifications, "notifications", "notifications_sub"),
        MenuEntry("data", androidx.compose.material.icons.Icons.Default.PieChart, "data_storage", "data_sub"),
        MenuEntry("parental", androidx.compose.material.icons.Icons.Default.FamilyRestroom, "parental", "parental_sub"),
        MenuEntry("accessibility", androidx.compose.material.icons.Icons.Default.Accessibility, "accessibility", "accessibility_sub"),
        MenuEntry("callscreen", androidx.compose.material.icons.Icons.Default.Call, "call_screen", "call_screen_sub"),
        MenuEntry("language", androidx.compose.material.icons.Icons.Default.Language, "language", null),
        MenuEntry("sounds", Icons.Default.MusicNote, "sounds", "sounds_sub"),
        MenuEntry("power", androidx.compose.material.icons.Icons.Default.BatteryChargingFull, "power_saving", "power_sub"),
        MenuEntry("security", androidx.compose.material.icons.Icons.Default.Security, "security", "security_sub")
    )
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrraAvatar(name, null, 42.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    if (phone.isNotBlank()) Text(phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
        },
        onClick = onProfile
    )
    androidx.compose.material3.HorizontalDivider()
    entries.forEach { entry ->
        DropdownMenuItem(
            text = {
                Column {
                    Text(tr(entry.title))
                    if (entry.sub != null) Text(tr(entry.sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            leadingIcon = { Icon(entry.icon, contentDescription = null) },
            onClick = { onOpen(entry.route) }
        )
    }
    androidx.compose.material3.HorizontalDivider()
}

/** Drop-in replacement for AlertDialog that fills the screen like a normal page. */
@Composable
fun OrraFullScreenDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    status: String = ""
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, onClick = onDismissRequest) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, contentDescription = tr("back")) }
                    }
                    Box(Modifier.padding(start = 12.dp)) { title() }
                }
                Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    text()
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { dismissButton(); confirmButton() }
            }
        }
    }
}
