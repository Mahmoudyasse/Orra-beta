package com.orra.app

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.time.Instant

private fun saveOrraContact(context: Context, name: String, number: String) {
    val ops = arrayListOf(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
    )
    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
}

private fun formatChatTimestamp(value: String): String =
    runCatching {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(Instant.parse(value)))
    }.getOrElse { value.take(16).replace("T", " ") }

private fun copyUriToCache(context: Context, uri: Uri): File {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.takeIf { it.isNotBlank() } ?: "orra_upload"
    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70)
    val file = File(context.cacheDir, "orra_${System.currentTimeMillis()}_$safeName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("Cannot read the selected file.")
    return file
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SupabaseChatClient.PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var projectUrl by remember { mutableStateOf(SupabaseChatClient.DEFAULT_PROJECT_URL) }
    var publishableKey by remember { mutableStateOf(SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY) }
    var token by remember { mutableStateOf(prefs.getString("token", "").orEmpty()) }
    var refreshToken by remember { mutableStateOf(prefs.getString("refresh_token", "").orEmpty()) }
    var userId by remember { mutableStateOf(prefs.getString("user_id", "").orEmpty()) }
    var username by remember { mutableStateOf(prefs.getString("username", "").orEmpty()) }
    LaunchedEffect(Unit) { SupabaseChatClient.saveConfig(context, projectUrl, publishableKey) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var requestedUsername by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var authMethod by remember { mutableStateOf("email") }
    var phoneInput by remember { mutableStateOf("") }
    var phoneOtp by remember { mutableStateOf("") }
    var phoneOtpSent by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var conversationSearch by remember { mutableStateOf("") }
    var chatSection by remember { mutableStateOf("all") }
    var presenceByUser by remember { mutableStateOf<Map<String, OrraUserPresence>>(emptyMap()) }
    var activePeerPresence by remember { mutableStateOf<OrraUserPresence?>(null) }
    var dndEnabled by remember { mutableStateOf(prefs.getBoolean("orra_dnd_enabled", false)) }
    var chatSearch by remember { mutableStateOf("") }
    var chatSearchVisible by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showContactShare by remember { mutableStateOf(false) }
    var contactShareName by remember { mutableStateOf("") }
    var contactShareNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<OrraChatUser>>(emptyList()) }
    var selectedUsers by remember { mutableStateOf(setOf<String>()) }
    var conversations by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var messages by remember { mutableStateOf<List<OrraChatMessage>>(emptyList()) }
    var selected by remember { mutableStateOf<OrraChatConversation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var showGroup by remember { mutableStateOf(false) }
    var showChatOptions by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    var addingToGroup by remember { mutableStateOf<OrraChatConversation?>(null) }
    var profile by remember { mutableStateOf<OrraChatProfile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var newAvatarPath by remember { mutableStateOf<String?>(null) }
    var newCoverPath by remember { mutableStateOf<String?>(null) }
    var avatarPreview by remember { mutableStateOf<String?>(null) }
    var coverPreview by remember { mutableStateOf<String?>(null) }
    var selectedMessage by remember { mutableStateOf<OrraChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var messageHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingSaveContact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var chatTheme by remember(selected?.id) {
        mutableStateOf(prefs.getString("chat_theme_${selected?.id}", "liquid") ?: "liquid")
    }
    var muted by remember(selected?.id) {
        mutableStateOf(prefs.getBoolean("chat_muted_${selected?.id}", false))
    }
    var recording by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val recorder = remember { OrraVoiceRecorder(context.cacheDir) }
    val clipboard = LocalClipboardManager.current
    val startCellularCall = rememberOrraCallAction()
    val startCarrierVideoCall = rememberOrraCallAction(video = true)
    val configured = projectUrl.startsWith("https://") && publishableKey.isNotBlank()
    val signedIn = token.isNotBlank() && userId.isNotBlank()
    val client = remember(projectUrl, publishableKey, token, userId) {
        if (configured) SupabaseChatClient(context, projectUrl, publishableKey,
            token.ifBlank { null }, userId.ifBlank { null }) else null
    }

    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = ""
            try { task() } catch (e: Exception) { error = e.message ?: "Something went wrong." }
            finally { busy = false }
        }
    }

    fun refreshMessages() {
        val active = selected ?: return
        val api = client ?: return
        runTask { messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) } }
    }

    BackHandler(enabled = selected != null || showProfile || showGroup || showChatOptions || selectedMessage != null || showAttachmentMenu || showContactShare) {
        when {
            showContactShare -> showContactShare = false
            showAttachmentMenu -> showAttachmentMenu = false
            selectedMessage != null -> selectedMessage = null
            showChatOptions -> showChatOptions = false
            showGroup -> { showGroup = false; addingToGroup = null }
            showProfile -> showProfile = false
            selected != null -> { selected = null; messages = emptyList(); chatSearch = ""; chatSearchVisible = false }
        }
    }

    fun shareLocation(location: Location) {
        val active = selected ?: return
        val api = client ?: return
        val lat = String.format(Locale.US, "%.6f", location.latitude)
        val lon = String.format(Locale.US, "%.6f", location.longitude)
        runTask {
            withContext(Dispatchers.IO) { api.sendLocationMessage(active.id, lat, lon) }
            messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) }
            showAttachmentMenu = false
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && client != null) runTask {
            val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val path = withContext(Dispatchers.IO) {
                client.uploadMedia("orra-profiles", file, "$userId/avatar_${System.currentTimeMillis()}.jpg", mime)
            }
            newAvatarPath = path
            avatarPreview = withContext(Dispatchers.IO) { client.createSignedMediaUrl("orra-profiles", path) }
            file.delete()
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && client != null) runTask {
            val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val path = withContext(Dispatchers.IO) {
                client.uploadMedia("orra-profiles", file, "$userId/cover_${System.currentTimeMillis()}.jpg", mime)
            }
            newCoverPath = path
            coverPreview = withContext(Dispatchers.IO) { client.createSignedMediaUrl("orra-profiles", path) }
            file.delete()
        }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val active = selected
        if (uri != null && active != null && client != null) runTask {
            val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            try {
                require(file.length() <= 20L * 1024L * 1024L) { "Files larger than 20 MB are not supported yet." }
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val type = if (mime.startsWith("image/")) "image" else "file"
                val path = withContext(Dispatchers.IO) {
                    client.uploadMedia("orra-chat-media", file,
                        "$userId/${active.id}/${System.currentTimeMillis()}_${file.name}", mime)
                }
                withContext(Dispatchers.IO) { client.sendAttachment(active.id, "", type, path) }
                messages = withContext(Dispatchers.IO) { client.loadMessages(active.id) }
            } finally { file.delete() }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val active = selected
        if (uri != null && active != null && client != null) runTask {
            val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            try {
                require(file.length() <= 20L * 1024L * 1024L) { "Files larger than 20 MB are not supported yet." }
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val type = when {
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("audio/") -> "audio"
                    mime.startsWith("video/") -> "video"
                    else -> "file"
                }
                val path = withContext(Dispatchers.IO) {
                    client.uploadMedia("orra-chat-media", file,
                        "$userId/" + active.id + "/" + System.currentTimeMillis() + "_" + file.name, mime)
                }
                withContext(Dispatchers.IO) { client.sendAttachment(active.id, file.name, type, path) }
                messages = withContext(Dispatchers.IO) { client.loadMessages(active.id) }
            } finally { file.delete() }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!allowed) error = "Location permission was denied."
        else {
            val manager = context.getSystemService(LocationManager::class.java)
            val provider = runCatching {
                when {
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
            }.getOrNull()
            if (provider == null) error = "Turn on Location services and try again."
            else {
                val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                if (cached != null && System.currentTimeMillis() - cached.time < 120000L) shareLocation(cached)
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                        if (location != null) shareLocation(location) else error = "Could not get your current location."
                    }
                } else {
                    try {
                        manager.requestSingleUpdate(provider, object : LocationListener {
                            override fun onLocationChanged(location: Location) = shareLocation(location)
                            @Deprecated("Deprecated by Android")
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) { error = "Location provider was disabled." }
                        }, Looper.getMainLooper())
                    } catch (_: SecurityException) { error = "Location permission is required." }
                }
            }
        }
    }
    val contactsWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingSaveContact
        if (granted && pending != null) runCatching {
            saveOrraContact(context, pending.first, pending.second)
            error = "Contact saved to your phone."
        }.onFailure { error = "Could not save contact. Check Contacts permission." }
        else if (!granted) error = "Contacts permission was denied."
        pendingSaveContact = null
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val active = selected
            val api = client
            if (active != null && api != null) {
                recording = true
                voiceStatus = "Recording with available Android voice processing…"
                recorder.start(
                    onComplete = { file ->
                        recording = false
                        scope.launch {
                            runCatching {
                                val path = withContext(Dispatchers.IO) {
                                    api.uploadMedia("orra-chat-media", file,
                                        "$userId/${active.id}/voice_${System.currentTimeMillis()}.wav", "audio/wav")
                                }
                                withContext(Dispatchers.IO) { api.sendAttachment(active.id, "", "audio", path) }
                                messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) }
                                voiceStatus = "Voice message sent"
                            }.onFailure { voiceStatus = it.message ?: "Voice upload failed" }
                            file.delete()
                        }
                    },
                    onError = { recording = false; voiceStatus = it }
                )
            }
        } else voiceStatus = "Microphone permission denied"
    }

    LaunchedEffect(token, refreshToken, client) {
        if (signedIn && client != null && refreshToken.isNotBlank()) {
            while (true) {
                val expiresAt = prefs.getLong("expires_at", System.currentTimeMillis() + 3600000L)
                val waitMillis = (expiresAt - System.currentTimeMillis() - 300000L).coerceAtLeast(30000L)
                delay(waitMillis)
                try {
                    val refreshed = withContext(Dispatchers.IO) { client.refreshSession(refreshToken) }
                    val nextAccess = refreshed.optString("access_token")
                    val nextRefresh = refreshed.optString("refresh_token").ifBlank { refreshToken }
                    if (nextAccess.isBlank()) throw IllegalStateException("Session refresh returned no access token.")
                    val nextExpiry = refreshed.optLong("expires_in", 3600L)
                    SupabaseChatClient.saveSession(context, nextAccess, userId, username, nextRefresh, nextExpiry)
                    token = nextAccess
                    refreshToken = nextRefresh
                } catch (e: Exception) {
                    error = e.message ?: "Session expired. Sign in again."
                    break
                }
            }
        }
    }
    LaunchedEffect(token, selected?.id, client) {
        if (signedIn && client != null) {
            while (true) {
                try {
                    if (selected == null) {
                        val loaded = withContext(Dispatchers.IO) { client.loadConversations() }
                        conversations = loaded
                        presenceByUser = withContext(Dispatchers.IO) {
                            client.loadPresence(loaded.mapNotNull { it.peerUserId })
                        }
                    } else {
                        val activeConversation = selected!!
                        messages = withContext(Dispatchers.IO) { client.loadMessages(activeConversation.id) }
                        runCatching {
                            withContext(Dispatchers.IO) { client.markConversationDelivered(activeConversation.id) }
                            delay(450)
                            withContext(Dispatchers.IO) { client.markConversationRead(activeConversation.id) }
                        }
                    }
                } catch (e: Exception) { error = e.message ?: "Could not refresh chats." }
                delay(3500)
            }
        }
    }

    LaunchedEffect(token, client, dndEnabled) {
        if (signedIn && client != null) {
            while (true) {
                runCatching { withContext(Dispatchers.IO) { client.updatePresence(true, dndEnabled, false, null) } }
                delay(12000)
            }
        }
    }

    LaunchedEffect(messageText, selected?.id, token, client) {
        if (signedIn && client != null && selected != null && messageText.isNotBlank()) {
            delay(550)
            runCatching {
                withContext(Dispatchers.IO) { client.updatePresence(true, dndEnabled, true, selected!!.id) }
            }
            delay(2600)
            runCatching {
                withContext(Dispatchers.IO) { client.updatePresence(true, dndEnabled, false, null) }
            }
        }
    }

    LaunchedEffect(selected?.peerUserId, client, dndEnabled) {
        val peer = selected?.peerUserId
        if (signedIn && client != null && !peer.isNullOrBlank()) {
            while (true) {
                activePeerPresence = runCatching {
                    withContext(Dispatchers.IO) { client.loadPresence(listOf(peer))[peer] }
                }.getOrNull()
                delay(5000)
            }
        } else activePeerPresence = null
    }

    LaunchedEffect(selectedMessage?.id, client) {
        val target = selectedMessage
        if (target != null && client != null) {
            messageHistory = runCatching { withContext(Dispatchers.IO) { client.loadMessageHistory(target.id) } }.getOrDefault(emptyList())
        } else messageHistory = emptyList()
    }

    LaunchedEffect(token, client) {
        if (signedIn && client != null) {
            runCatching {
                val loaded = withContext(Dispatchers.IO) { client.loadMyProfile() }
                profile = loaded
                displayName = loaded.displayName
                bio = loaded.bio
                phoneNumber = loaded.phoneNumber
                avatarPreview = loaded.avatarUrl
                coverPreview = loaded.coverUrl
            }.onFailure { error = it.message ?: "Profile could not be loaded." }
            runCatching {
                val backupId = withContext(Dispatchers.IO) { client.ensureDailyCloudBackup() }
                backupStatus = if (backupId != null) "Cloud chat backup is up to date." else "Cloud backup is not available yet."
            }.onFailure { backupStatus = "Cloud backup pending: " + (it.message ?: "connection error") }
        }
    }

    DisposableEffect(client) {
        onDispose {
            recorder.stop()
            runCatching { player?.release() }
            Thread {
                val finalDnd = context.getSharedPreferences("orra_chat_backend", Context.MODE_PRIVATE)
                    .getBoolean("orra_dnd_enabled", false)
                runCatching { client?.updatePresence(false, finalDnd, false, null) }
            }.start()
        }
    }

    val chatBackground = when (chatTheme) {
        "dark" -> Color(0xFF080A12)
        "ocean" -> Color(0xFF0B1C2A)
        "violet" -> Color(0xFF17112A)
        "rose" -> Color(0xFF24121E)
        else -> MaterialTheme.colorScheme.background
    }

    val chatSurface = if (selected != null && chatTheme == "liquid") {
        Modifier.background(Brush.linearGradient(listOf(
            Color(0xFF0B111A), Color(0xFF17152B), Color(0xFF101C26)
        )))
    } else {
        Modifier.background(if (selected != null) chatBackground else MaterialTheme.colorScheme.background)
    }

    Column(
        Modifier.fillMaxSize().then(chatSurface)
            .padding(start = 16.dp, end = 16.dp, top = 58.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!configured) {
            Text("Connect ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Connect your Supabase project to use real ORRA accounts and synchronized messaging.")
            OutlinedTextField(projectUrl, { projectUrl = it }, Modifier.fillMaxWidth(), label = { Text("Project URL") }, singleLine = true)
            OutlinedTextField(publishableKey, { publishableKey = it }, Modifier.fillMaxWidth(), label = { Text("Publishable key") }, singleLine = true)
            Button(onClick = {
                if (projectUrl.startsWith("https://") && publishableKey.isNotBlank()) {
                    SupabaseChatClient.saveConfig(context, projectUrl, publishableKey)
                    error = "Server settings saved. Reopen Chats to connect."
                } else error = "Enter a valid HTTPS URL and publishable key."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save server connection") }
        } else if (!signedIn) {
            Text("ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Choose how to access your ORRA account.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (authMethod == "email") Button(onClick = { authMethod = "email"; phoneOtpSent = false; error = "" }) { Text("Email") }
                else OutlinedButton(onClick = { authMethod = "email"; error = "" }) { Text("Email") }
                if (authMethod == "phone") Button(onClick = { authMethod = "phone"; error = "" }) { Text("Phone / SIM") }
                else OutlinedButton(onClick = { authMethod = "phone"; error = "" }) { Text("Phone / SIM") }
            }
            if (authMethod == "email") {
                Text(if (isSignUp) "Create your account" else "Sign in")
            if (backupStatus.isNotBlank()) Text(backupStatus, style = MaterialTheme.typography.labelSmall)
                if (isSignUp) OutlinedTextField(requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(enabled = !busy && email.contains("@") && password.length >= 8 && (!isSignUp || requestedUsername.length >= 3),
                    onClick = {
                        val api = SupabaseChatClient(context, projectUrl, publishableKey)
                        runTask {
                            val response = withContext(Dispatchers.IO) {
                                if (isSignUp) api.signUp(email, password, requestedUsername) else api.signIn(email, password)
                            }
                            val access = response.optString("access_token")
                            val user = response.optJSONObject("user")
                            if (access.isBlank() || user == null) error = "Account created. Confirm your email if required, then sign in."
                            else {
                                val id = user.optString("id")
                                val name = user.optJSONObject("user_metadata")?.optString("username")
                                    ?.takeIf { !it.isNullOrBlank() } ?: email.substringBefore("@")
                                val nextRefresh = response.optString("refresh_token")
                                val expiresIn = response.optLong("expires_in", 3600L)
                                SupabaseChatClient.saveSession(context, access, id, name, nextRefresh, expiresIn)
                                token = access; refreshToken = nextRefresh; userId = id; username = name
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Please wait…" else if (isSignUp) "Create account" else "Sign in")
                }
                TextButton(onClick = { isSignUp = !isSignUp; error = "" }) {
                    Text(if (isSignUp) "Already have an account? Sign in" else "Create a new ORRA account")
                }
                OutlinedButton(
                    onClick = {
                        val authUrl = Uri.parse(projectUrl.trimEnd('/') + "/auth/v1/authorize").buildUpon()
                            .appendQueryParameter("provider", "google")
                            .appendQueryParameter("redirect_to", "com.orra.beta://auth-callback/")
                            .build()
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, authUrl)) }
                            .onFailure { error = "Could not open Google sign-in." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue with Google") }
                Text("Google sign-in requires the Google provider and OAuth credentials to be enabled in Supabase.")
            } else {
                Text("Sign in or create an account using a verified mobile number.")
                if (!phoneOtpSent) {
                    OutlinedTextField(
                        phoneInput, { phoneInput = it }, Modifier.fillMaxWidth(),
                        label = { Text("Mobile number (+20...)") }, singleLine = true
                    )
                    if (isSignUp) OutlinedTextField(
                        requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(),
                        label = { Text("Username (optional)") }, singleLine = true
                    )
                    Button(enabled = !busy && phoneInput.filter { it.isDigit() }.length >= 10,
                        onClick = {
                            runTask {
                                withContext(Dispatchers.IO) {
                                    SupabaseChatClient(context, projectUrl, publishableKey)
                                        .sendPhoneOtp(phoneInput, requestedUsername)
                                }
                                phoneOtpSent = true
                                error = "A one-time code was requested. Check the SMS inbox for this number."
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Sending…" else "Send SMS code")
                    }
                } else {
                    Text("Enter the six-digit code sent by SMS.")
                    OutlinedTextField(
                        phoneOtp, { phoneOtp = it.filter(Char::isDigit).take(6) },
                        Modifier.fillMaxWidth(), label = { Text("SMS verification code") }, singleLine = true
                    )
                    Button(enabled = !busy && phoneOtp.length == 6,
                        onClick = {
                            runTask {
                                val response = withContext(Dispatchers.IO) {
                                    SupabaseChatClient(context, projectUrl, publishableKey)
                                        .verifyPhoneOtp(phoneInput, phoneOtp)
                                }
                                val access = response.optString("access_token")
                                val user = response.optJSONObject("user")
                                    ?: throw IllegalStateException("The SMS code was not accepted.")
                                if (access.isBlank()) throw IllegalStateException("No login session was returned.")
                                val id = user.optString("id")
                                val name = user.optJSONObject("user_metadata")?.optString("username")
                                    ?.takeIf { !it.isNullOrBlank() } ?: phoneInput.takeLast(4).let { "user_$it" }
                                val nextRefresh = response.optString("refresh_token")
                                val expiresIn = response.optLong("expires_in", 3600L)
                                SupabaseChatClient.saveSession(context, access, id, name, nextRefresh, expiresIn)
                                withContext(Dispatchers.IO) {
                                    SupabaseChatClient(context, projectUrl, publishableKey, access, id)
                                        .saveVerifiedPhone(phoneInput)
                                }
                                token = access; refreshToken = nextRefresh; userId = id; username = name
                                phoneOtpSent = false
                                phoneOtp = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Verifying…" else "Verify and sign in") }
                    TextButton(onClick = { phoneOtpSent = false; phoneOtp = ""; error = "" }) { Text("Change number") }
                }
                Text("SMS delivery requires an enabled SMS provider in Supabase Auth. The SIM only receives the code; ORRA does not read SMS messages.")
            }
            Text("Use your own verified account. Never share your one-time code.")
        } else if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ORRA Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("@$username", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    dndEnabled = !dndEnabled
                    prefs.edit().putBoolean("orra_dnd_enabled", dndEnabled).apply()
                    runTask { withContext(Dispatchers.IO) { client!!.updatePresence(true, dndEnabled, false, null) } }
                }) { Text(if (dndEnabled) "DND ON" else "DND") }
                IconButton(onClick = { showProfile = true }) {
                    if (!profile?.avatarUrl.isNullOrBlank()) AsyncImage(profile?.avatarUrl, null, Modifier.size(38.dp).clip(CircleShape))
                    else Icon(Icons.Default.Person, contentDescription = "Profile")
                }
                IconButton(onClick = { showGroup = true; addingToGroup = null; selectedUsers = emptySet() }) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "New group")
                }
                IconButton(onClick = {
                    SupabaseChatClient.clearSession(context)
                    token = ""; userId = ""; username = ""; conversations = emptyList(); selected = null
                }) { Icon(Icons.Default.Logout, contentDescription = "Sign out") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(search, { search = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Find people by username") })
                IconButton(enabled = search.isNotBlank() && !busy, onClick = {
                    runTask { users = withContext(Dispatchers.IO) { client!!.searchUsers(search) } }
                }) { Icon(Icons.Default.Search, contentDescription = "Search") }
            }
            OutlinedTextField(conversationSearch, { conversationSearch = it }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search your conversations") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "groups" to "Groups", "direct" to "Direct").forEach { (key, label) ->
                    if (chatSection == key) Button(onClick = { chatSection = key }) { Text(label) }
                    else OutlinedButton(onClick = { chatSection = key }) { Text(label) }
                }
            }
            if (users.isNotEmpty()) {
                Text("People", fontWeight = FontWeight.SemiBold)
                users.forEach { user ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("@${user.username}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            TextButton(onClick = {
                                runTask {
                                    val id = withContext(Dispatchers.IO) { client!!.createConversation(user.id) }
                                    val all = withContext(Dispatchers.IO) { client!!.loadConversations() }
                                    conversations = all
                                    selected = all.firstOrNull { it.id == id }
                                        ?: OrraChatConversation(id, user.username, "", false, 2, user.id)
                                    users = emptyList(); messages = emptyList()
                                }
                            }) { Text("Message") }
                            TextButton(onClick = { selectedUsers = selectedUsers + user.id; showGroup = true }) { Text("Add to group") }
                        }
                    }
                }
            }
            Text("Conversations", fontWeight = FontWeight.SemiBold)
            if (conversations.isEmpty()) {
                Text("No conversations yet. Search for a username or create a group.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(conversations.filter { conversation ->
                        (chatSection == "all" || (chatSection == "groups" && conversation.isGroup) ||
                            (chatSection == "direct" && !conversation.isGroup)) &&
                            (conversationSearch.isBlank() || conversation.username.contains(conversationSearch, ignoreCase = true) ||
                                conversation.lastMessage.contains(conversationSearch, ignoreCase = true))
                    }, key = { it.id }) { conversation ->
                        Surface(Modifier.fillMaxWidth().clickable {
                            selected = conversation
                            messages = emptyList()
                        }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (conversation.isGroup) "G" else conversation.username.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(conversation.username, fontWeight = FontWeight.SemiBold)
                                    Text(conversation.lastMessage, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (!conversation.isGroup) {
                                        val presence = presenceByUser[conversation.peerUserId]
                                        Text(
                                            when {
                                                presence?.dnd == true -> "● Do not disturb"
                                                presence?.online == true -> "● Online"
                                                else -> "○ Offline"
                                            },
                                            color = if (presence?.dnd == true) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (conversation.isGroup) Text("${conversation.memberCount}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null; messages = emptyList(); error = "" }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(selected!!.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (selected!!.isGroup) "Group • ${selected!!.memberCount} members"
                        else when {
                            activePeerPresence?.dnd == true -> "Do not disturb"
                            activePeerPresence?.typing == true && activePeerPresence?.typingConversation == selected!!.id -> "Typing…"
                            activePeerPresence?.online == true -> "Online"
                            else -> "Offline • " + (selected!!.peerPhone?.takeIf { it.isNotBlank() } ?: "ORRA")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activePeerPresence?.dnd == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!selected!!.isGroup) {
                    IconButton(onClick = {
                        val number = selected!!.peerPhone
                        if (number.isNullOrBlank()) error = "This user has not added a phone number to their ORRA profile."
                        else startCellularCall(selected!!.username, number)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice call")
                    }
                    IconButton(onClick = {
                        val number = selected!!.peerPhone
                        if (number.isNullOrBlank()) error = "This user has not added a phone number to their ORRA profile."
                        else startCarrierVideoCall(selected!!.username, number)
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Carrier video call")
                    }
                    IconButton(onClick = {
                        val peerId = selected!!.peerUserId
                        if (peerId.isNullOrBlank()) error = "This ORRA user ID is unavailable."
                        else runTask {
                            val call = withContext(Dispatchers.IO) {
                                OrraCallSignaling(context, userId, token).createCall(peerId, "audio")
                            }
                            OrraInternetCallState.begin(call, selected!!.username, true, userId, token)
                            recordOrraInternetCall(context, selected!!.username, peerId, "Outgoing", "audio")
                        }
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "ORRA internet voice call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        val peerId = selected!!.peerUserId
                        if (peerId.isNullOrBlank()) error = "This ORRA user ID is unavailable."
                        else runTask {
                            val call = withContext(Dispatchers.IO) {
                                OrraCallSignaling(context, userId, token).createCall(peerId, "video")
                            }
                            OrraInternetCallState.begin(call, selected!!.username, true, userId, token)
                            recordOrraInternetCall(context, selected!!.username, peerId, "Outgoing", "video")
                        }
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "ORRA internet video call", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { addingToGroup = selected; showGroup = true; selectedUsers = emptySet() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add members")
                    }
                }
                IconButton(onClick = { chatSearchVisible = !chatSearchVisible; chatSearch = "" }) {
                    Icon(Icons.Default.Search, contentDescription = "Search in chat")
                }
                IconButton(onClick = { showChatOptions = true }) { Icon(Icons.Default.Settings, contentDescription = "Chat options") }
            }
            if (chatSearchVisible) {
                OutlinedTextField(chatSearch, { chatSearch = it }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Search messages in this chat") })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.filter { chatSearch.isBlank() || it.body.contains(chatSearch, ignoreCase = true) }, key = { it.id }) { message ->
                    val mine = message.senderId == userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                                selectedMessage = message
                                editText = message.body
                            }),
                            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (!mine && selected!!.isGroup && message.senderUsername.isNotBlank()) {
                                    Text("@${message.senderUsername}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                when (message.type) {
                                    "image" -> if (!message.mediaUrl.isNullOrBlank()) {
                                        AsyncImage(message.mediaUrl, contentDescription = "Shared image",
                                            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)))
                                    } else Text("Image attachment")
                                    "audio" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            runCatching {
                                                player?.release()
                                                player = MediaPlayer().apply {
                                                    setDataSource(message.mediaUrl)
                                                    setOnPreparedListener { it.start() }
                                                    prepareAsync()
                                                }
                                            }.onFailure { error = "Could not play this voice message." }
                                        }) { Icon(Icons.Default.Mic, contentDescription = "Play voice message") }
                                        Text("Voice message")
                                    }
                                    "file", "video" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (message.type == "video") "Video attachment: ${message.body}" else "File: ${message.body}")
                                        if (!message.mediaUrl.isNullOrBlank()) {
                                            TextButton(onClick = {
                                                val mime = if (message.type == "video") "video/*" else "*/*"
                                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(message.mediaUrl), mime)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                runCatching { context.startActivity(intent) }
                                                    .onFailure { error = "No compatible app could open this attachment." }
                                            }) { Text("Open attachment") }
                                        }
                                    }
                                    "location" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Current location", fontWeight = FontWeight.SemiBold)
                                        Text(message.body, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            val mapsUrl = message.body.substringAfterLast("\n")
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                                .onFailure { error = "Could not open map link." }
                                        }) { Text("Open in maps") }
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.body)) }) { Text("Copy location details") }
                                    }
                                    "contact" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Shared contact", fontWeight = FontWeight.SemiBold)
                                        Text(message.body)
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.body)) }) { Text("Copy contact") }
                                    }
                                    else -> Text(message.body)
                                }
                                if (message.editedAt != null) Text("Edited", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatChatTimestamp(message.createdAt),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (mine && message.receiptStatus != null) {
                                    Text(
                                        when (message.receiptStatus) {
                                            "read" -> "✓✓ Read"
                                            "delivered" -> "✓✓ Delivered"
                                            else -> "✓ Sent"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (message.receiptStatus == "read") MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (voiceStatus.isNotBlank()) Text(voiceStatus, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { showAttachmentMenu = true }, enabled = !busy) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach media, document, location or contact")
                }
                IconButton(onClick = {
                    if (recording) {
                        recorder.stop()
                        voiceStatus = "Finishing voice message…"
                    } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !busy) {
                    Icon(Icons.Default.Mic, contentDescription = if (recording) "Stop recording" else "Record voice message",
                        tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(messageText, { messageText = it }, Modifier.weight(1f),
                    placeholder = { Text("Message") }, maxLines = 4)
                IconButton(enabled = messageText.isNotBlank() && !busy && selected != null && client != null, onClick = {
                    val body = messageText.trim()
                    val active = selected
                    val api = client
                    if (body.isNotBlank() && active != null && api != null) {
                        scope.launch {
                            busy = true
                            error = ""
                            messageText = ""
                            try {
                                withContext(Dispatchers.IO) { api.sendMessage(active.id, body) }
                                messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) }
                            } catch (e: Exception) {
                                messageText = body
                                error = e.message ?: "Message could not be sent. Your text has been restored."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }) { Icon(Icons.Default.Send, contentDescription = "Send") }
            }
        }
        if (busy) Text("Syncing…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            title = { Text("Share with this chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showAttachmentMenu = false; attachmentPicker.launch("image/*") }) { Text("Photo from gallery") }
                    TextButton(onClick = { showAttachmentMenu = false; documentPicker.launch(arrayOf("*/*")) }) { Text("Document, audio or video file") }
                    TextButton(onClick = {
                        showAttachmentMenu = false
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text("Share current location") }
                    TextButton(onClick = { showAttachmentMenu = false; showContactShare = true }) { Text("Share a contact") }
                }
            },
            confirmButton = { TextButton(onClick = { showAttachmentMenu = false }) { Text("Close") } }
        )
    }

    if (showContactShare) {
        AlertDialog(
            onDismissRequest = { showContactShare = false },
            title = { Text("Share contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(contactShareName, { contactShareName = it }, label = { Text("Contact name") }, singleLine = true)
                    OutlinedTextField(contactShareNumber, { contactShareNumber = it }, label = { Text("Phone number") }, singleLine = true)
                    Text("This sends a contact card in ORRA. It does not add the contact to the recipient's phone automatically.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = contactShareName.isNotBlank() && contactShareNumber.isNotBlank() && !busy, onClick = {
                    val active = selected
                    val api = client
                    if (active != null && api != null) runTask {
                        withContext(Dispatchers.IO) { api.sendContactCard(active.id, contactShareName.trim(), contactShareNumber.trim()) }
                        messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) }
                        showContactShare = false
                        contactShareName = ""; contactShareNumber = ""
                    }
                }) { Text("Send contact") }
            },
            dismissButton = { TextButton(onClick = { showContactShare = false }) { Text("Cancel") } }
        )
    }

    if (showProfile) {
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Your ORRA profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (!coverPreview.isNullOrBlank()) AsyncImage(coverPreview, null,
                        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(14.dp)))
                    if (!avatarPreview.isNullOrBlank()) AsyncImage(avatarPreview, null,
                        Modifier.size(72.dp).clip(CircleShape))
                    OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, singleLine = true)
                    OutlinedTextField(bio, { bio = it }, label = { Text("About") }, maxLines = 3)
                    OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Phone number for SIM calls") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }) { Icon(Icons.Default.Person, null); Text(" Photo") }
                        OutlinedButton(onClick = { coverPicker.launch("image/*") }) { Icon(Icons.Default.Photo, null); Text(" Cover") }
                    }
                    Text("Images are stored in your private ORRA profile storage. Your phone number is shown to signed-in ORRA users so they can call you using their SIM.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runTask {
                        withContext(Dispatchers.IO) {
                            client!!.updateProfile(displayName, bio, phoneNumber, newAvatarPath, newCoverPath)
                        }
                        profile = withContext(Dispatchers.IO) { client!!.loadMyProfile() }
                        newAvatarPath = null; newCoverPath = null
                        showProfile = false
                    }
                }) { Text("Save profile") }
            },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Cancel") } }
        )
    }

    if (showGroup) {
        AlertDialog(
            onDismissRequest = { showGroup = false; addingToGroup = null },
            title = { Text(if (addingToGroup == null) "Create group" else "Add group members") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (addingToGroup == null) OutlinedTextField(groupTitle, { groupTitle = it }, label = { Text("Group name") }, singleLine = true)
                    OutlinedTextField(search, { search = it }, label = { Text("Find members") }, singleLine = true)
                    Button(enabled = search.isNotBlank() && !busy, onClick = {
                        runTask { users = withContext(Dispatchers.IO) { client!!.searchUsers(search) } }
                    }) { Text("Search people") }
                    users.forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = user.id in selectedUsers, onCheckedChange = {
                                selectedUsers = if (it) selectedUsers + user.id else selectedUsers - user.id
                            })
                            Text("@${user.username}")
                        }
                    }
                    Text("${selectedUsers.size} selected", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(enabled = selectedUsers.isNotEmpty() && !busy &&
                    (addingToGroup != null || groupTitle.isNotBlank()), onClick = {
                    val members = selectedUsers.toList()
                    runTask {
                        if (addingToGroup == null) {
                            val id = withContext(Dispatchers.IO) { client!!.createGroup(groupTitle, members) }
                            conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            selected = conversations.firstOrNull { it.id == id }
                        } else {
                            members.forEach { id -> withContext(Dispatchers.IO) { client!!.addGroupMember(addingToGroup!!.id, id) } }
                            conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            selected = conversations.firstOrNull { it.id == addingToGroup!!.id }
                        }
                        showGroup = false; addingToGroup = null; selectedUsers = emptySet(); users = emptyList(); groupTitle = ""
                    }
                }) { Text(if (addingToGroup == null) "Create group" else "Add members") }
            },
            dismissButton = { TextButton(onClick = { showGroup = false; addingToGroup = null }) { Text("Cancel") } }
        )
    }

    if (showChatOptions && selected != null) {
        AlertDialog(
            onDismissRequest = { showChatOptions = false },
            title = { Text("Chat settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conversation theme")
                    listOf("liquid" to "iPhone Island Liquid", "dark" to "iPhone Dark",
                        "ocean" to "Ocean", "violet" to "Violet", "rose" to "Rose").forEach { (key, label) ->
                        TextButton(onClick = {
                            chatTheme = key
                            prefs.edit().putString("chat_theme_${selected!!.id}", key).apply()
                        }) { Text(if (chatTheme == key) "✓  $label" else label) }
                    }
                    TextButton(onClick = {
                        val next = !muted
                        runTask {
                            withContext(Dispatchers.IO) { client!!.setConversationMuted(selected!!.id, next) }
                            muted = next
                            prefs.edit().putBoolean("chat_muted_${selected!!.id}", next).apply()
                        }
                    }) { Text(if (muted) "Unmute notifications" else "Mute notifications") }
                    if (!selected!!.isGroup && !selected!!.peerPhone.isNullOrBlank()) {
                        TextButton(onClick = {
                            val contact = selected!!.username to selected!!.peerPhone!!
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                runCatching { saveOrraContact(context, contact.first, contact.second); error = "Contact saved to your phone." }
                                    .onFailure { error = "Could not save contact." }
                            } else {
                                pendingSaveContact = contact
                                contactsWritePermission.launch(Manifest.permission.WRITE_CONTACTS)
                            }
                        }) { Text("Save contact to phone") }
                    }
                    if (selected!!.isGroup) {
                        TextButton(onClick = {
                            runTask {
                                withContext(Dispatchers.IO) { client!!.leaveGroup(selected!!.id) }
                                selected = null
                                conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            }
                            showChatOptions = false
                        }) { Text("Leave group") }
                    } else {
                        TextButton(onClick = {
                            val peer = selected!!.peerUserId
                            if (peer != null) runTask {
                                withContext(Dispatchers.IO) { client!!.blockUser(peer) }
                                withContext(Dispatchers.IO) { client!!.deleteConversationForMe(selected!!.id) }
                                selected = null
                                conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            }
                            showChatOptions = false
                        }) { Text("Block user") }
                    }
                    TextButton(onClick = {
                        runTask {
                            withContext(Dispatchers.IO) { client!!.deleteConversationForMe(selected!!.id) }
                            selected = null
                            conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                        }
                        showChatOptions = false
                    }) { Text("Delete chat for me", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { showChatOptions = false }) { Text("Close") } }
        )
    }

    if (selectedMessage != null && selected != null) {
        val message = selectedMessage!!
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Message options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.senderId == userId && !message.deletedForEveryone) {
                        if (message.type == "text") {
                            OutlinedTextField(editText, { editText = it }, label = { Text("Edit message") }, maxLines = 4)
                            TextButton(onClick = {
                                runTask {
                                    withContext(Dispatchers.IO) { client!!.editMessage(message.id, editText) }
                                    messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                                }
                                selectedMessage = null
                            }) { Text("Save edit") }
                        }
                        TextButton(onClick = {
                            runTask {
                                withContext(Dispatchers.IO) { client!!.deleteMessageForEveryone(message.id) }
                                messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                            }
                            selectedMessage = null
                        }) { Text("Delete for everyone", color = MaterialTheme.colorScheme.error) }
                    }
                    if (messageHistory.isNotEmpty()) {
                        Text("Message history", fontWeight = FontWeight.SemiBold)
                        messageHistory.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = {
                        runTask {
                            withContext(Dispatchers.IO) { client!!.deleteMessageForMe(message.id) }
                            messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                        }
                        selectedMessage = null
                    }) { Text("Delete for me") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("Close") } }
        )
    }
}
