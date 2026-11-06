package com.orra.app

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var projectUrl by remember { mutableStateOf(prefs.getString("url", "").orEmpty()) }
    var publishableKey by remember { mutableStateOf(prefs.getString("key", "").orEmpty()) }
    var token by remember { mutableStateOf(prefs.getString("token", "").orEmpty()) }
    var userId by remember { mutableStateOf(prefs.getString("user_id", "").orEmpty()) }
    var username by remember { mutableStateOf(prefs.getString("username", "").orEmpty()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var requestedUsername by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<OrraChatUser>>(emptyList()) }
    var selectedUsers by remember { mutableStateOf(setOf<String>()) }
    var conversations by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var messages by remember { mutableStateOf<List<OrraChatMessage>>(emptyList()) }
    var selected by remember { mutableStateOf<OrraChatConversation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
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
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val type = if (mime.startsWith("image/")) "image" else "file"
            val path = withContext(Dispatchers.IO) {
                client.uploadMedia("orra-chat-media", file,
                    "$userId/${active.id}/${System.currentTimeMillis()}_${file.name}", mime)
            }
            withContext(Dispatchers.IO) { client.sendAttachment(active.id, "", type, path) }
            file.delete()
            messages = withContext(Dispatchers.IO) { client.loadMessages(active.id) }
        }
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

    LaunchedEffect(token, selected?.id, client) {
        if (signedIn && client != null) {
            while (true) {
                try {
                    if (selected == null) conversations = withContext(Dispatchers.IO) { client.loadConversations() }
                    else messages = withContext(Dispatchers.IO) { client.loadMessages(selected!!.id) }
                } catch (e: Exception) { error = e.message ?: "Could not refresh chats." }
                delay(3500)
            }
        }
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
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.stop(); runCatching { player?.release() } }
    }

    val chatBackground = when (chatTheme) {
        "dark" -> Color(0xFF080A12)
        "ocean" -> Color(0xFF0B1C2A)
        "violet" -> Color(0xFF17112A)
        "rose" -> Color(0xFF24121E)
        else -> MaterialTheme.colorScheme.background
    }

    Column(
        Modifier.fillMaxSize().background(if (selected != null) chatBackground else MaterialTheme.colorScheme.background)
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
            Text(if (isSignUp) "Create your account" else "Sign in")
            if (isSignUp) OutlinedTextField(requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
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
                            SupabaseChatClient.saveSession(context, access, id, name)
                            token = access; userId = id; username = name
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Please wait…" else if (isSignUp) "Create account" else "Sign in")
            }
            TextButton(onClick = { isSignUp = !isSignUp; error = "" }) {
                Text(if (isSignUp) "Already have an account? Sign in" else "Create a new ORRA account")
            }
            Text("Use a unique password. ORRA never needs passwords from other apps.")
        } else if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ORRA Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("@$username", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                    items(conversations, key = { it.id }) { conversation ->
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
                    Text(if (selected!!.isGroup) "Group • ${selected!!.memberCount} members" else "ORRA conversation",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                } else {
                    IconButton(onClick = { addingToGroup = selected; showGroup = true; selectedUsers = emptySet() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add members")
                    }
                }
                IconButton(onClick = { showChatOptions = true }) { Icon(Icons.Default.Settings, contentDescription = "Chat options") }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
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
                                    "file" -> Text("Attachment: ${message.body}")
                                    else -> Text(message.body)
                                }
                                if (message.editedAt != null) Text("Edited", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(message.createdAt.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (voiceStatus.isNotBlank()) Text(voiceStatus, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { attachmentPicker.launch("*/*") }, enabled = !busy) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
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
                IconButton(enabled = messageText.isNotBlank() && !busy, onClick = {
                    val body = messageText.trim()
                    messageText = ""
                    runTask {
                        withContext(Dispatchers.IO) { client!!.sendMessage(selected!!.id, body) }
                        messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                    }
                }) { Icon(Icons.Default.Send, contentDescription = "Send") }
            }
        }
        if (busy) Text("Syncing…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
