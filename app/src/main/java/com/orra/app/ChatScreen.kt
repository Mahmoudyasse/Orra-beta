package com.orra.app

import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.RingtoneManager
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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


private data class OrraPhoneCountry(val name: String, val code: String)
private val orraPhoneCountries = listOf(
    OrraPhoneCountry("Egypt", "+20"), OrraPhoneCountry("Saudi Arabia", "+966"),
    OrraPhoneCountry("United Arab Emirates", "+971"), OrraPhoneCountry("Kuwait", "+965"),
    OrraPhoneCountry("Qatar", "+974"), OrraPhoneCountry("Bahrain", "+973"),
    OrraPhoneCountry("Oman", "+968"), OrraPhoneCountry("Jordan", "+962"),
    OrraPhoneCountry("Lebanon", "+961"), OrraPhoneCountry("Iraq", "+964"),
    OrraPhoneCountry("Palestine", "+970"), OrraPhoneCountry("Morocco", "+212"),
    OrraPhoneCountry("Algeria", "+213"), OrraPhoneCountry("Tunisia", "+216"),
    OrraPhoneCountry("Libya", "+218"), OrraPhoneCountry("Sudan", "+249"),
    OrraPhoneCountry("United States", "+1"), OrraPhoneCountry("Canada", "+1"),
    OrraPhoneCountry("United Kingdom", "+44"), OrraPhoneCountry("Ireland", "+353"),
    OrraPhoneCountry("France", "+33"), OrraPhoneCountry("Germany", "+49"),
    OrraPhoneCountry("Italy", "+39"), OrraPhoneCountry("Spain", "+34"),
    OrraPhoneCountry("Netherlands", "+31"), OrraPhoneCountry("Belgium", "+32"),
    OrraPhoneCountry("Switzerland", "+41"), OrraPhoneCountry("Austria", "+43"),
    OrraPhoneCountry("Sweden", "+46"), OrraPhoneCountry("Norway", "+47"),
    OrraPhoneCountry("Denmark", "+45"), OrraPhoneCountry("Turkey", "+90"),
    OrraPhoneCountry("Pakistan", "+92"), OrraPhoneCountry("India", "+91"),
    OrraPhoneCountry("Bangladesh", "+880"), OrraPhoneCountry("Indonesia", "+62"),
    OrraPhoneCountry("Malaysia", "+60"), OrraPhoneCountry("Philippines", "+63"),
    OrraPhoneCountry("Singapore", "+65"), OrraPhoneCountry("Japan", "+81"),
    OrraPhoneCountry("South Korea", "+82"), OrraPhoneCountry("China", "+86"),
    OrraPhoneCountry("Australia", "+61"), OrraPhoneCountry("New Zealand", "+64"),
    OrraPhoneCountry("South Africa", "+27"), OrraPhoneCountry("Nigeria", "+234"),
    OrraPhoneCountry("Kenya", "+254"), OrraPhoneCountry("Brazil", "+55"),
    OrraPhoneCountry("Mexico", "+52"), OrraPhoneCountry("Argentina", "+54")
)

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
    var phoneCountryCode by remember { mutableStateOf(prefs.getString("phone_country_code", "+20").orEmpty().ifBlank { "+20" }) }
    var phoneCountryName by remember { mutableStateOf(prefs.getString("phone_country_name", "Egypt").orEmpty().ifBlank { "Egypt" }) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    val internationalPhone = remember(phoneInput, phoneCountryCode) {
        val raw = phoneInput.trim().replace(Regex("[^0-9+]"), "")
        if (raw.startsWith("+")) raw else phoneCountryCode + raw.removePrefix("0")
    }
    var phoneOtp by remember { mutableStateOf("") }
    var phoneOtpSent by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var conversationSearch by remember { mutableStateOf("") }
    var chatSection by remember { mutableStateOf("all") }
    var lockTarget by remember { mutableStateOf<OrraChatConversation?>(null) }
    var folderUnlockTarget by remember { mutableStateOf<OrraFolder?>(null) }
    var showListMenu by remember { mutableStateOf(false) }
    var showNewChat by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showPeerProfile by remember { mutableStateOf(false) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    var viewLimit by remember { mutableIntStateOf(0) }
    var viewerImage by remember { mutableStateOf<String?>(null) }
    val expiredViews = remember { mutableStateListOf<String>() }
    var lastSeenId by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var cancelVoice by remember { mutableStateOf(false) }
    var peerProfile by remember { mutableStateOf<OrraChatProfile?>(null) }
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
    var demoMode by remember { mutableStateOf(false) }
    var demoNotificationsGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val demoNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { demoNotificationsGranted = it }
    var selected by remember { mutableStateOf<OrraChatConversation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var showGroup by remember { mutableStateOf(false) }
    var showChatOptions by remember { mutableStateOf(false) }
    var showDeletedHistory by remember { mutableStateOf(false) }
    var deletedHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupTitle by remember { mutableStateOf("") }
    var addingToGroup by remember { mutableStateOf<OrraChatConversation?>(null) }
    var profile by remember { mutableStateOf<OrraChatProfile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var phoneOtpPending by remember { mutableStateOf(false) }
    var phoneOtpCode by remember { mutableStateOf("") }
    var phoneStatus by remember { mutableStateOf("") }
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
            try { task() } catch (e: Exception) { android.util.Log.w("ORRA", "Background operation failed", e); error = e.message.orEmpty() }
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
            val file = withContext(Dispatchers.IO) { OrraImage.toJpegFile(context, uri) }
            val mime = "image/jpeg"
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
            val file = withContext(Dispatchers.IO) { OrraImage.toJpegFile(context, uri) }
            val mime = "image/jpeg"
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
                if (OrraMedia.isDangerous(file.name)) throw IllegalStateException(tr("unsafe_file"))
                require(file.length() <= 100L * 1024L * 1024L) { "Files larger than 100 MB are not supported yet." }
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
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val active = selected
        if (uris.isNotEmpty() && active != null && client != null) runTask {
            for (uri in uris) {
                val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                try {
                    if (OrraMedia.isDangerous(file.name)) throw IllegalStateException(tr("unsafe_file"))
                    require(file.length() <= 100L * 1024L * 1024L) { "Files larger than 100 MB are not supported yet." }
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val type = if (mime.startsWith("video/")) "video" else "image"
                    val path = withContext(Dispatchers.IO) {
                        client.uploadMedia("orra-chat-media", file, "$userId/${active.id}/${System.currentTimeMillis()}_${file.name}", mime)
                    }
                    withContext(Dispatchers.IO) { client.sendAttachment(active.id, OrraMedia.viewPrefix(viewLimit) + type, type, path) }
                } finally { file.delete() }
            }
            messages = withContext(Dispatchers.IO) { client.loadMessages(active.id) }
            OrraSfx.play(context, "send")
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val active = selected
        val photo = cameraFile
        if (taken && photo != null && active != null && client != null) runTask {
            try {
                val path = withContext(Dispatchers.IO) {
                    client.uploadMedia("orra-chat-media", photo, "$userId/${active.id}/${System.currentTimeMillis()}_camera.jpg", "image/jpeg")
                }
                withContext(Dispatchers.IO) { client.sendAttachment(active.id, OrraMedia.viewPrefix(viewLimit) + "image", "image", path) }
                messages = withContext(Dispatchers.IO) { client.loadMessages(active.id) }
                OrraSfx.play(context, "send")
            } finally { photo.delete() }
        } else photo?.delete()
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        val photo = File(dir, "orra_" + System.currentTimeMillis() + ".jpg")
        cameraFile = photo
        cameraLauncher.launch(androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".orrafiles", photo))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else error = tr("camera_denied")
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val active = selected
        if (uri != null && active != null && client != null) runTask {
            val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
            try {
                if (OrraMedia.isDangerous(file.name)) throw IllegalStateException(tr("unsafe_file"))
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
        }.onFailure { android.util.Log.w("ORRA", "Contact save failed", it) }
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
                        if (cancelVoice) { cancelVoice = false; file.delete(); voiceStatus = tr("recording_cancelled") } else scope.launch {
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
            var firstRefresh = true
            while (true) {
                val expiresAt = prefs.getLong("expires_at", System.currentTimeMillis() + 3600000L)
                val waitMillis = (expiresAt - System.currentTimeMillis() - 300000L).coerceAtLeast(if (firstRefresh) 0L else 30000L)
                firstRefresh = false
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
                    android.util.Log.w("ORRA", "Session refresh failed", e)
                    if (OrraNet.isOnline(context)) {
                        SupabaseChatClient.clearSession(context)
                        token = ""; refreshToken = ""; userId = ""; username = ""
                        conversations = emptyList(); selected = null
                        error = tr("session_expired")
                    }
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
                        if (activeConversation.isAi) {
                            messages = withContext(Dispatchers.IO) { client.loadOrraAiMessages() }
                        } else {
                            messages = withContext(Dispatchers.IO) { client.loadMessages(activeConversation.id) }
                            runCatching {
                                withContext(Dispatchers.IO) { client.markConversationDelivered(activeConversation.id) }
                                delay(450)
                                withContext(Dispatchers.IO) { client.markConversationRead(activeConversation.id) }
                            }
                        }
                    }
                } catch (e: Exception) { android.util.Log.w("ORRA", "Chat refresh failed", e) }
                delay(OrraPower.pollDelay(context, 3500))
            }
        }
    }

    LaunchedEffect(token, client, dndEnabled) {
        if (signedIn && client != null) {
            while (true) {
                runCatching { withContext(Dispatchers.IO) { client.updatePresence(OrraPrivacy.shareOnline(context), dndEnabled, false, null) } }
                delay(OrraPower.pollDelay(context, 12000))
            }
        }
    }

    LaunchedEffect(messageText, selected?.id, token, client) {
        if (signedIn && client != null && selected != null && messageText.isNotBlank()) {
            delay(550)
            runCatching {
                withContext(Dispatchers.IO) { client.updatePresence(OrraPrivacy.shareOnline(context), dndEnabled, true, selected!!.id) }
            }
            delay(2600)
            runCatching {
                withContext(Dispatchers.IO) { client.updatePresence(OrraPrivacy.shareOnline(context), dndEnabled, false, null) }
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
                delay(OrraPower.pollDelay(context, 5000))
            }
        } else activePeerPresence = null
    }

    LaunchedEffect(selectedMessage?.id, client) {
        val target = selectedMessage
        if (target != null && client != null) {
            messageHistory = runCatching { withContext(Dispatchers.IO) { client.loadMessageHistory(target.id) } }.getOrDefault(emptyList())
        } else messageHistory = emptyList()
    }

    LaunchedEffect(selected?.id, showDeletedHistory, client) {
        val active = selected
        if (showDeletedHistory && active != null && client != null) {
            deletedHistory = runCatching {
                withContext(Dispatchers.IO) { client.loadDeletedHistoryForConversation(active.id) }
            }.getOrDefault(emptyList())
        }
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
            }.onFailure { android.util.Log.w("ORRA", "Profile load failed", it) }
            runCatching {
                val backupId = withContext(Dispatchers.IO) { client.ensureDailyCloudBackup() }
                backupStatus = if (backupId != null) "Cloud chat backup is up to date." else "Cloud backup is not available yet."
            }.onFailure { backupStatus = "Cloud backup pending: " + (it.message ?: "connection error") }
        }
    }

    LaunchedEffect(OrraPendingChat.userId, client) {
        val targetUser = OrraPendingChat.userId
        if (signedIn && client != null && !targetUser.isNullOrBlank()) {
            runCatching {
                val conversationId = withContext(Dispatchers.IO) { client.createConversation(targetUser) }
                conversations = withContext(Dispatchers.IO) { client.loadConversations() }
                selected = conversations.firstOrNull { it.id == conversationId }
                    ?: throw IllegalStateException("The ORRA conversation could not be opened.")
                messages = withContext(Dispatchers.IO) { client.loadMessages(conversationId) }
            }.onFailure { android.util.Log.w("ORRA", "Chat open failed", it) }
            OrraPendingChat.clear()
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
        "dark" -> Color(0xFF050505)
        "ocean" -> Color(0xFF0B1C2A)
        "violet" -> Color(0xFF17112A)
        "rose" -> Color(0xFF24121E)
        else -> MaterialTheme.colorScheme.background
    }

    val wallpaperModifier = if (selected != null && chatTheme == "liquid") rememberOrraWallpaperModifier() else null
    val chatSurface = if (wallpaperModifier != null) wallpaperModifier else if (selected != null && chatTheme == "liquid") {
        Modifier.background(Brush.linearGradient(listOf(
            Color(0xFF000000), Color(0xFF0A0A0F), Color(0xFF050809)
        )))
    } else {
        Modifier.background(if (selected != null) chatBackground else MaterialTheme.colorScheme.background)
    }

    LaunchedEffect(selected?.id) { lastSeenId = null }
    LaunchedEffect(recording) {
        if (recording) { recordSeconds = 0; while (true) { delay(1000); recordSeconds++ } }
    }
    LaunchedEffect(selected?.peerUserId, client) {
        peerProfile = null
        val peer = selected?.peerUserId
        if (peer != null && client != null && selected?.isGroup != true && selected?.isAi != true) {
            peerProfile = runCatching { withContext(Dispatchers.IO) { client.loadPublicProfile(peer) } }.getOrNull()
        }
    }
    LaunchedEffect(messages) {
        val last = messages.lastOrNull()
        if (last != null && lastSeenId != null && last.id != lastSeenId && last.senderId != userId) OrraSfx.play(context, "receive")
        lastSeenId = last?.id
    }
    val clearedAt = if (selected != null) prefs.getLong("chat_cleared_" + selected!!.id, 0L) else 0L
    val visibleMessages = if (clearedAt > 0L) messages.filter {
        runCatching { java.time.OffsetDateTime.parse(it.createdAt).toInstant().toEpochMilli() > clearedAt }.getOrDefault(true)
    } else messages
    lockTarget?.let { target ->
        OrraUnlockDialog(target.username, OrraLocks.chatKey(target.id), { lockTarget = null }) {
            selected = target; messages = emptyList(); lockTarget = null
        }
    }
    folderUnlockTarget?.let { folder ->
        OrraUnlockDialog(folder.name, OrraFolders.folderKey(folder.id), { folderUnlockTarget = null }) {
            chatSection = "folder:" + folder.id; folderUnlockTarget = null
        }
    }

    Column(
        Modifier.fillMaxSize().then(chatSurface)
            .padding(start = 16.dp, end = 16.dp, top = 58.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (demoMode || !configured) {
            Text("ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Surface(Modifier.fillMaxWidth().clickable { demoMode = true; selected = OrraChatConversation("demo-local", "Demo Contact", "Local demo chat", peerUserId = "demo-peer"); messages = emptyList() }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Person, null); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Demo Contact", fontWeight = FontWeight.SemiBold); Text(tr("s_99a25b80"), style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.Send, null) }
            }
            if (demoMode && selected?.id == "demo-local") {
                Text(tr("s_3f26b24d"), style = MaterialTheme.typography.bodySmall)
            } else Text(tr("s_22279d96"))
            if (!demoMode) {
                Text(tr("s_d84b40a2"))
            }
            if (demoMode && selected?.id == "demo-local") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null; demoMode = false }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text("Demo Contact", fontWeight = FontWeight.Bold)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { m -> Text((if (m.senderId == "demo-me") "You: " else "Demo Contact: ") + m.body, modifier = Modifier.fillMaxWidth().padding(8.dp)) } }
                if (!demoNotificationsGranted) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { if (Build.VERSION.SDK_INT >= 33) demoNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else demoNotificationsGranted = true }) { Text(tr("s_93c72c4a")) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(messageText, { messageText = it }, Modifier.weight(1f), placeholder = { Text(tr("s_b0b519e2")) })
                    IconButton(enabled = messageText.isNotBlank(), onClick = {
                        val body = messageText.trim(); messageText = ""
                        val sent = OrraChatMessage("demo-" + System.currentTimeMillis(), "demo-me", body, Instant.now().toString())
                        messages = messages + sent
                        scope.launch {
                            delay(1000)
                            val reply = OrraChatMessage("demo-" + System.currentTimeMillis(), "demo-peer", "وصلت رسالتك: " + body.take(60) + " ✅ دي رسالة رد تجريبية.", Instant.now().toString())
                            messages = messages + reply
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@launch
                            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel("orra-demo") == null) manager.createNotificationChannel(NotificationChannel("orra-demo", "ORRA demo messages", NotificationManager.IMPORTANCE_HIGH).apply { setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null) })
                            val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, "orra-demo") else android.app.Notification.Builder(context)
                            manager.notify(991, builder.setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("ORRA Demo Contact").setContentText(reply.body).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).build())
                        }
                    }) { Icon(Icons.Default.Send, "Send") }
                }
            }
            Text(tr("s_24f351fe"))
            OutlinedTextField(projectUrl, { projectUrl = it }, Modifier.fillMaxWidth(), label = { Text(tr("s_9bc13a6d")) }, singleLine = true)
            OutlinedTextField(publishableKey, { publishableKey = it }, Modifier.fillMaxWidth(), label = { Text(tr("s_30ca9973")) }, singleLine = true)
            Button(onClick = {
                if (projectUrl.startsWith("https://") && publishableKey.isNotBlank()) {
                    SupabaseChatClient.saveConfig(context, projectUrl, publishableKey)
                    error = "Server settings saved. Reopen Chats to connect."
                } else error = "Enter a valid HTTPS URL and publishable key."
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("s_3c0838cd")) }
        } else if (!signedIn) {
            Text("ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(tr("s_90ef781a"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (authMethod == "email") Button(onClick = { authMethod = "email"; phoneOtpSent = false; error = "" }) { Text(tr("s_ce8ae9da")) }
                else OutlinedButton(onClick = { authMethod = "email"; error = "" }) { Text(tr("s_ce8ae9da")) }
                if (authMethod == "phone") Button(onClick = { authMethod = "phone"; error = "" }) { Text(tr("s_8b0576ef")) }
                else OutlinedButton(onClick = { authMethod = "phone"; error = "" }) { Text(tr("s_8b0576ef")) }
            }
            if (authMethod == "email") {
                Text(if (isSignUp) "Create your account" else "Sign in")
                if (isSignUp) OutlinedTextField(requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(), label = { Text(tr("s_f6039d44")) }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr("s_ce8ae9da")) }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(tr("s_dc647eb6")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
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
                            .onFailure { android.util.Log.w("ORRA", "Google sign-in launch failed", it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("s_a7f3dfc6")) }
                Text(tr("s_c561de12"))
            } else {
                Text(tr("s_6eef5d72"))
                if (!phoneOtpSent) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { countryMenuExpanded = true }) {
                                Text(phoneCountryName + " " + phoneCountryCode)
                            }
                            DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                                orraPhoneCountries.forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text(country.name + " (" + country.code + ")") },
                                        onClick = {
                                            phoneCountryCode = country.code
                                            phoneCountryName = country.name
                                            prefs.edit().putString("phone_country_code", country.code)
                                                .putString("phone_country_name", country.name).apply()
                                            countryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(phoneInput, { phoneInput = it }, Modifier.weight(1f),
                            label = { Text(tr("s_d1457170")) }, singleLine = true)
                    }
                    if (isSignUp) OutlinedTextField(
                        requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(),
                        label = { Text(tr("s_b131f167")) }, singleLine = true
                    )
                    Button(enabled = !busy && phoneInput.filter { it.isDigit() }.length >= 6,
                        onClick = {
                            runTask {
                                withContext(Dispatchers.IO) {
                                    SupabaseChatClient(context, projectUrl, publishableKey)
                                        .sendPhoneOtp(internationalPhone, requestedUsername)
                                }
                                phoneOtpSent = true
                                error = "A one-time code was requested. Check the SMS inbox for this number."
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Sending…" else "Send SMS code")
                    }
                } else {
                    Text(tr("s_75cf5820"))
                    OutlinedTextField(
                        phoneOtp, { phoneOtp = it.filter(Char::isDigit).take(6) },
                        Modifier.fillMaxWidth(), label = { Text(tr("s_45eb9ede")) }, singleLine = true
                    )
                    Button(enabled = !busy && phoneOtp.length == 6,
                        onClick = {
                            runTask {
                                val response = withContext(Dispatchers.IO) {
                                    SupabaseChatClient(context, projectUrl, publishableKey)
                                        .verifyPhoneOtp(internationalPhone, phoneOtp)
                                }
                                val access = response.optString("access_token")
                                val user = response.optJSONObject("user")
                                    ?: throw IllegalStateException("The SMS code was not accepted.")
                                if (access.isBlank()) throw IllegalStateException("No login session was returned.")
                                val id = user.optString("id")
                                val name = user.optJSONObject("user_metadata")?.optString("username")
                                    ?.takeIf { !it.isNullOrBlank() } ?: internationalPhone.takeLast(4).let { "user_$it" }
                                val nextRefresh = response.optString("refresh_token")
                                val expiresIn = response.optLong("expires_in", 3600L)
                                SupabaseChatClient.saveSession(context, access, id, name, nextRefresh, expiresIn)
                                token = access; refreshToken = nextRefresh; userId = id; username = name
                                phoneOtpSent = false
                                phoneOtp = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Verifying…" else "Verify and sign in") }
                    TextButton(onClick = { phoneOtpSent = false; phoneOtp = ""; error = "" }) { Text(tr("s_10197289")) }
                }
                Text(tr("s_dbb8c2fd"))
            }
            Text(tr("s_7c0c27d6"))
        } else if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("@$username", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showNewChat = true }) { Icon(Icons.Default.Edit, contentDescription = tr("new_chat_title")) }
                IconButton(onClick = { showFind = !showFind }) { Icon(Icons.Default.Search, contentDescription = tr("find_people")) }
                Box {
                    IconButton(onClick = { showListMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = tr("more_options")) }
                    DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                        OrraSettingsMenu(
                            name = displayName.ifBlank { username }, phone = phoneNumber,
                            onProfile = { showListMenu = false; showProfile = true },
                            onOpen = { route -> showListMenu = false; OrraNav.openSettings(route) }
                        )
                        DropdownMenuItem(text = { Text(tr("menu_new_group")) }, leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                            onClick = { showListMenu = false; showGroup = true; addingToGroup = null; selectedUsers = emptySet() })
                        DropdownMenuItem(text = { Text(if (dndEnabled) tr("menu_dnd_off") else tr("menu_dnd_on")) }, leadingIcon = { Icon(Icons.Default.Notifications, null) },
                            onClick = {
                                showListMenu = false
                                dndEnabled = !dndEnabled
                                prefs.edit().putBoolean("orra_dnd_enabled", dndEnabled).apply()
                                runTask { withContext(Dispatchers.IO) { client!!.updatePresence(OrraPrivacy.shareOnline(context), dndEnabled, false, null) } }
                            })
                        DropdownMenuItem(text = { Text(tr("menu_sign_out")) }, leadingIcon = { Icon(Icons.Default.Logout, null) },
                            onClick = {
                                showListMenu = false
                                SupabaseChatClient.clearSession(context)
                                token = ""; userId = ""; username = ""; conversations = emptyList(); selected = null
                            })
                    }
                }
            }
            OrraSearchPill(conversationSearch, { conversationSearch = it }, tr("list_search"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrraFilterChip(tr("tab_all_chats") + " " + conversations.size, chatSection == "all") { chatSection = "all" }
                OrraFilterChip(tr("groups"), chatSection == "groups") { chatSection = "groups" }
                OrraFilterChip(tr("direct"), chatSection == "direct") { chatSection = "direct" }
                OrraFolders.list(context).forEach { folder ->
                    val folderKey = "folder:" + folder.id
                    OrraFilterChip(folder.name, chatSection == folderKey) {
                        if (OrraFolders.isLocked(context, folder.id)) folderUnlockTarget = folder else chatSection = folderKey
                    }
                }
            }
            if (showFind) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(search, { search = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("s_ec908230")) })
                    IconButton(enabled = search.isNotBlank() && !busy, onClick = {
                        runTask { users = withContext(Dispatchers.IO) { client!!.searchUsers(search) } }
                    }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                }
            }
            if (users.isNotEmpty()) {
                Text(tr("s_78386ec8"), fontWeight = FontWeight.SemiBold)
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
                            }) { Text(tr("s_4c2a8fe7")) }
                            TextButton(onClick = { selectedUsers = selectedUsers + user.id; showGroup = true }) { Text(tr("s_8e822dcd")) }
                        }
                    }
                }
            }
            Text(tr("s_f004c5e3"), fontWeight = FontWeight.SemiBold)
            Surface(Modifier.fillMaxWidth().clickable {
                selected = OrraChatConversation("orra-ai", "ORRA AI", "Your personal AI assistant", false, 0, null, null, true)
                messages = emptyList()
                error = ""
            }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center) {
                        Text("AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("ORRA AI", fontWeight = FontWeight.SemiBold)
                        Text(tr("s_5dae4b8f"), maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (conversations.isEmpty()) {
                // Keep the conversation area clean when there are no chats yet.
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hiddenByFolderLock = OrraFolders.hiddenChatIds(context)
                    items(conversations.filter { conversation ->
                        ((chatSection == "all" || (chatSection == "groups" && conversation.isGroup) ||
                            (chatSection == "direct" && !conversation.isGroup)) && conversation.id !in hiddenByFolderLock ||
                            (chatSection.startsWith("folder:") && OrraFolders.list(context).any {
                                it.id == chatSection.removePrefix("folder:") && conversation.id in it.chatIds
                            })) &&
                            (conversationSearch.isBlank() || conversation.username.contains(conversationSearch, ignoreCase = true) ||
                                conversation.lastMessage.contains(conversationSearch, ignoreCase = true))
                    }, key = { it.id }) { conversation ->
                        val presence = conversation.peerUserId?.let { presenceByUser[it] }
                        OrraChatRow(conversation.username, conversation.lastMessage, presence?.online == true && presence.dnd != true,
                            OrraLocks.isChatLocked(context, conversation.id), conversation.isGroup) {
                            if (OrraLocks.isChatLocked(context, conversation.id)) lockTarget = conversation
                            else { selected = conversation; messages = emptyList() }
                        }
                    }
                }
            }
        } else {
            val headerStatus = when {
                selected!!.isAi -> "AI assistant • Ready"
                selected!!.isGroup -> selected!!.memberCount.toString() + " " + tr("members")
                activePeerPresence?.typing == true -> tr("typing")
                activePeerPresence?.dnd == true -> tr("menu_dnd_on")
                activePeerPresence?.online == true -> tr("online_now")
                else -> tr("offline")
            }
            fun startOrraCall(type: String) {
                val peerId = selected!!.peerUserId
                if (peerId.isNullOrBlank()) error = "This ORRA user ID is unavailable."
                else if (OrraParental.blockCalls(context)) error = tr("parental_blocked")
                else if (!OrraNet.isOnline(context)) { error = tr("no_internet"); OrraSfx.play(context, "error") }
                else runTask {
                    val created = withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).createCall(peerId, type) }
                    OrraSfx.play(context, "call_start")
                    OrraInternetCallState.begin(created, selected!!.username, true, userId, token)
                    recordOrraInternetCall(context, selected!!.username, peerId, "Outgoing", type)
                }
            }
            fun toggleMute() {
                val next = !muted
                runTask {
                    withContext(Dispatchers.IO) { client!!.setConversationMuted(selected!!.id, next) }
                    muted = next
                    prefs.edit().putBoolean("chat_muted_${selected!!.id}", next).apply()
                }
            }
            OrraChatTopBar(
                title = selected!!.username, status = headerStatus,
                statusColor = if (activePeerPresence?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                avatarUrl = peerProfile?.avatarUrl,
                onBack = { selected = null; messages = emptyList(); error = ""; showEmoji = false },
                onOpenProfile = { if (!selected!!.isAi) showPeerProfile = true },
                onCall = if (selected!!.isAi) null else ({ startOrraCall("audio") }),
                menu = if (selected!!.isAi) listOf(
                    OrraMenuItem(tr("menu_search"), Icons.Default.Search) { chatSearchVisible = true }
                ) else listOf(
                    OrraMenuItem(tr(if (muted) "menu_unmute" else "menu_mute"), if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications) { toggleMute() },
                    OrraMenuItem(tr("menu_video"), Icons.Default.Videocam) { startOrraCall("video") },
                    OrraMenuItem(tr("menu_search"), Icons.Default.Search) { chatSearchVisible = true },
                    OrraMenuItem(tr("menu_wallpaper"), Icons.Default.Wallpaper) { showWallpaper = true },
                    OrraMenuItem(tr("menu_clear"), Icons.Default.DeleteSweep) {
                        val last = messages.lastOrNull()
                        val cutoff = runCatching { java.time.OffsetDateTime.parse(last?.createdAt.orEmpty()).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                        prefs.edit().putLong("chat_cleared_" + selected!!.id, cutoff).apply()
                        messages = emptyList()
                    },
                    OrraMenuItem(tr("menu_delete"), Icons.Default.Delete, danger = true) { confirmDeleteChat = true },
                    OrraMenuItem(tr("more_options"), Icons.Default.MoreVert) { showChatOptions = true }
                )
            )
            if (chatSearchVisible) {
                OutlinedTextField(chatSearch, { chatSearch = it }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(tr("s_1ab0d30f")) })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleMessages.filter { chatSearch.isBlank() || it.body.contains(chatSearch, ignoreCase = true) }, key = { it.id }) { message ->
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
                                OrraMediaGate(
                                    when (message.type) { "image" -> "image"; "audio" -> "audio"; "video" -> "video"; "file" -> "file"; else -> "" },
                                    message.id, selected!!.isGroup
                                ) {
                                if (!mine && OrraMedia.viewLimit(message.body) > 0 && (message.type == "image" || message.type == "video")) {
                                    val limit = OrraMedia.viewLimit(message.body)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (message.id in expiredViews) tr("view_expired") else tr("view_tap_open") + " (" + limit + ")", fontWeight = FontWeight.SemiBold)
                                        if (message.id !in expiredViews) Button(onClick = {
                                            scope.launch {
                                                val left = runCatching { withContext(Dispatchers.IO) { client!!.consumeMediaView(message.id) } }.getOrDefault(-2)
                                                if (left == -2) expiredViews.add(message.id)
                                                else if (message.type == "image") viewerImage = message.mediaUrl
                                                else runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(message.mediaUrl), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                }
                                            }
                                        }) { Text(tr("view_open")) }
                                    }
                                } else when (message.type) {
                                    "image" -> if (!message.mediaUrl.isNullOrBlank()) {
                                        AsyncImage(message.mediaUrl, contentDescription = "Shared image",
                                            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)))
                                    } else Text(tr("s_0b73346c"))
                                    "audio" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            runCatching {
                                                player?.release()
                                                player = MediaPlayer().apply {
                                                    setDataSource(message.mediaUrl)
                                                    setOnPreparedListener { it.start() }
                                                    prepareAsync()
                                                }
                                            }.onFailure { android.util.Log.w("ORRA", "Voice playback failed", it) }
                                        }) { Icon(Icons.Default.Mic, contentDescription = "Play voice message") }
                                        Text(tr("s_a716aa47"))
                                    }
                                    "file", "video" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (message.type == "video") "Video attachment: ${OrraMedia.cleanBody(message.body)}" else "File: ${OrraMedia.cleanBody(message.body)}")
                                        if (!message.mediaUrl.isNullOrBlank()) {
                                            TextButton(onClick = {
                                                val mime = if (message.type == "video") "video/*" else "*/*"
                                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(message.mediaUrl), mime)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                runCatching { context.startActivity(intent) }
                                                    .onFailure { android.util.Log.w("ORRA", "Attachment open failed", it) }
                                            }) { Text(tr("s_191499bc")) }
                                        }
                                    }
                                    "location" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(tr("s_cec99c77"), fontWeight = FontWeight.SemiBold)
                                        Text(message.body, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            val mapsUrl = message.body.substringAfterLast("\n")
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                                .onFailure { android.util.Log.w("ORRA", "Map launch failed", it) }
                                        }) { Text(tr("s_eae42dd7")) }
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.body)) }) { Text(tr("s_5bbd311e")) }
                                    }
                                    "contact" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(tr("s_94b1ced2"), fontWeight = FontWeight.SemiBold)
                                        Text(message.body)
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.body)) }) { Text(tr("s_7fec99c6")) }
                                    }
                                    else -> Text(message.body)
                                }
                                }
                                if (message.type in listOf("image", "video", "audio", "file") && !message.mediaUrl.isNullOrBlank() && OrraMedia.viewLimit(message.body) == 0) {
                                    TextButton(onClick = {
                                        val savedName = Uri.parse(message.mediaUrl).lastPathSegment ?: "orra_file"
                                        voiceStatus = if (OrraMedia.save(context, message.mediaUrl!!, savedName)) tr("saved_downloads") else tr("save_blocked")
                                    }) { Text(tr("save_to_device")) }
                                }
                                if (message.editedAt != null) Text(tr("s_67ed6ddb"), style = MaterialTheme.typography.labelSmall,
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
            if (showEmoji) OrraEmojiPanel { messageText += it }
            if (recording) OrraRecordingBar(recordSeconds, onCancel = { cancelVoice = true; recorder.stop() },
                onSend = { recorder.stop(); voiceStatus = "Finishing voice message…" }) else OrraComposerBar(
                text = messageText, onText = { messageText = it },
                hint = if (selected!!.isAi) "Message ORRA AI…" else tr("hint_message"),
                canAttach = !selected!!.isAi, recording = recording, busy = busy,
                onEmoji = { showEmoji = !showEmoji },
                onAttach = { showEmoji = false; showAttachmentMenu = true },
                onMic = {
                    if (!selected!!.isAi) {
                        if (recording) { recorder.stop(); voiceStatus = "Finishing voice message…" }
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSend = {
                    if (selected != null && client != null) {
                    val body = messageText.trim()
                        val active = selected
                    val api = client
                    if (body.isNotBlank() && active != null && api != null) {
                        scope.launch {
                            busy = true
                            error = ""
                            messageText = ""
                            try {
                                if (active.isAi) {
                                    withContext(Dispatchers.IO) { api.sendOrraAiMessage(body) }
                                    messages = withContext(Dispatchers.IO) { api.loadOrraAiMessages() }
                                } else {
                                    withContext(Dispatchers.IO) { api.sendMessage(active.id, body) }
                                    runCatching { messages = withContext(Dispatchers.IO) { api.loadMessages(active.id) } }
                                        .onFailure { error = tr("sent_refresh_failed") + " " + (it.message ?: "") }
                                }
                                OrraSfx.play(context, "send")
                            } catch (e: Exception) {
                                OrraSfx.play(context, "error")
                                android.util.Log.w("ORRA", "Message send failed", e)
                                var failure: Exception? = e
                                if (!OrraNet.isOnline(context)) failure = IllegalStateException(tr("no_internet"))
                                else if ((e.message.orEmpty().contains("session or permissions", ignoreCase = true) || e.message.orEmpty().contains("HTTP 401") ||
                                        e.message.orEmpty().contains("sign in", ignoreCase = true)) && refreshToken.isNotBlank()) {
                                    val refreshedOk = withContext(Dispatchers.IO) { orraRefreshSession(context) }
                                    if (refreshedOk) {
                                        token = prefs.getString("token", "").orEmpty()
                                        refreshToken = prefs.getString("refresh_token", "").orEmpty()
                                        val retry = orraClient(context)
                                        failure = try {
                                            withContext(Dispatchers.IO) {
                                                if (retry == null) throw IllegalStateException("Sign in again to send messages.")
                                                if (active.isAi) retry.sendOrraAiMessage(body) else retry.sendMessage(active.id, body)
                                            }
                                            null
                                        } catch (e2: Exception) { e2 }
                                        if (failure != null && failure!!.message.orEmpty().contains("HTTP 401")) failure = IllegalStateException(tr("session_expired"))
                                        if (failure == null && retry != null) {
                                            messages = withContext(Dispatchers.IO) { if (active.isAi) retry.loadOrraAiMessages() else retry.loadMessages(active.id) }
                                        }
                                    }
                                }
                                if (failure != null) {
                                    messageText = body
                                    error = tr("send_failed") + ": " + (failure.message ?: "")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    }
                    }
                }
            )
        }
        if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        if (busy) Text(tr("s_50e713b4"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }

    if (showAttachmentMenu) {
        OrraAttachSheet(
            viewLimit = viewLimit, onViewLimit = { viewLimit = it }, onDismiss = { showAttachmentMenu = false },
            onGallery = { showAttachmentMenu = false; if (OrraParental.blockMedia(context)) error = tr("parental_blocked") else galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onCamera = {
                showAttachmentMenu = false
                if (OrraParental.blockMedia(context)) error = tr("parental_blocked")
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onFile = { showAttachmentMenu = false; documentPicker.launch(arrayOf("*/*")) },
            onMusic = { showAttachmentMenu = false; documentPicker.launch(arrayOf("audio/*")) },
            onLocation = {
                showAttachmentMenu = false
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onContact = { showAttachmentMenu = false; showContactShare = true }
        )
    }

    if (showPeerProfile && selected != null) {
        OrraPeerProfile(
            name = selected!!.username,
            status = if (selected!!.isGroup) selected!!.memberCount.toString() + " " + tr("members")
                else if (activePeerPresence?.online == true) tr("online_now") else tr("offline"),
            phone = peerProfile?.phoneNumber?.takeIf { it.isNotBlank() } ?: selected!!.peerPhone, muted = muted, canCall = !selected!!.isGroup && !selected!!.isAi,
            avatarUrl = peerProfile?.avatarUrl, coverUrl = peerProfile?.coverUrl, bio = peerProfile?.bio.orEmpty(),
            username = peerProfile?.username.orEmpty(), userId = selected!!.peerUserId,
            onDismiss = { showPeerProfile = false }, onMessage = { showPeerProfile = false },
            onToggleMute = {
                val next = !muted
                runTask {
                    withContext(Dispatchers.IO) { client!!.setConversationMuted(selected!!.id, next) }
                    muted = next
                    prefs.edit().putBoolean("chat_muted_${selected!!.id}", next).apply()
                }
            },
            onCall = {
                showPeerProfile = false
                val peerId = selected!!.peerUserId
                if (!peerId.isNullOrBlank()) {
                    if (OrraParental.blockCalls(context)) error = tr("parental_blocked")
                    else if (!OrraNet.isOnline(context)) error = tr("no_internet")
                    else runTask {
                        val created = withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).createCall(peerId, "audio") }
                        OrraInternetCallState.begin(created, selected!!.username, true, userId, token)
                    }
                }
            },
            onVideo = {
                showPeerProfile = false
                val peerId = selected!!.peerUserId
                if (!peerId.isNullOrBlank()) {
                    if (OrraParental.blockCalls(context)) error = tr("parental_blocked")
                    else if (!OrraNet.isOnline(context)) error = tr("no_internet")
                    else runTask {
                        val created = withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).createCall(peerId, "video") }
                        OrraInternetCallState.begin(created, selected!!.username, true, userId, token)
                    }
                }
            }
        )
    }

    if (showWallpaper) {
        val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { withContext(Dispatchers.IO) { OrraWallpaper.setCustom(context, uri) } }
        }
        AlertDialog(
            onDismissRequest = { showWallpaper = false },
            title = { Text(tr("menu_wallpaper")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrraWallpaper.presets.forEach { preset ->
                        TextButton(onClick = { OrraWallpaper.setPreset(context, preset) }) {
                            Text((if (OrraWallpaper.current(context) == preset) "✓  " else "") + tr("wp_" + preset.lowercase()))
                        }
                    }
                    TextButton(onClick = { wallpaperPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(tr("wallpaper_from_phone"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWallpaper = false }) { Text(tr("save")) } }
        )
    }

    if (confirmDeleteChat && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text(tr("menu_delete")) },
            text = { Text(tr("delete_chat_body")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteChat = false
                    runTask {
                        withContext(Dispatchers.IO) { client!!.deleteConversationForMe(selected!!.id) }
                        selected = null
                        conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                    }
                }) { Text(tr("delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteChat = false }) { Text(tr("cancel")) } }
        )
    }

    if (showNewChat) {
        var newPhone by remember { mutableStateOf("") }
        var notFound by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNewChat = false },
            title = { Text(tr("new_chat_title")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("new_chat_sub"), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(newPhone, { newPhone = it; notFound = false }, singleLine = true, label = { Text(tr("phone_number")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                    if (notFound) {
                        Text(tr("new_chat_not_found"), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + newPhone.trim()))
                                    .putExtra("sms_body", tr("share_text") + " https://github.com/Mahmoudyasse/Orra-beta"))
                            }
                        }) { Text(tr("invite_sms")) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newPhone.isNotBlank() && !busy, onClick = {
                    runTask {
                        val found = withContext(Dispatchers.IO) { client!!.searchUsersByPhone(newPhone) }
                        if (found == null) notFound = true
                        else {
                            val id = withContext(Dispatchers.IO) { client!!.createConversation(found.id) }
                            val all = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            conversations = all
                            selected = all.firstOrNull { it.id == id } ?: OrraChatConversation(id, found.username, "", false, 2, found.id)
                            messages = emptyList()
                            showNewChat = false
                        }
                    }
                }) { Text(tr("new_chat_open")) }
            },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text(tr("cancel")) } }
        )
    }

    viewerImage?.let { url ->
        Dialog(onDismissRequest = { viewerImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect { dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
            Surface(modifier = Modifier.fillMaxSize().clickable { viewerImage = null }, color = Color.Black) {
                AsyncImage(url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }


    if (showContactShare) {
        AlertDialog(
            onDismissRequest = { showContactShare = false },
            title = { Text(tr("s_b32f077b")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(contactShareName, { contactShareName = it }, label = { Text(tr("s_8728ddcf")) }, singleLine = true)
                    OutlinedTextField(contactShareNumber, { contactShareNumber = it }, label = { Text(tr("s_1f8261d1")) }, singleLine = true)
                    Text(tr("s_9b9055fc"),
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
                }) { Text(tr("s_9c1370d7")) }
            },
            dismissButton = { TextButton(onClick = { showContactShare = false }) { Text(tr("s_ea478870")) } }
        )
    }

    if (showProfile) {
        OrraFullScreenDialog(
            onDismissRequest = { showProfile = false },
            status = error,
            header = {
                Box(Modifier.fillMaxWidth().height(230.dp)) {
                    if (!coverPreview.isNullOrBlank()) AsyncImage(coverPreview, null, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Surface(modifier = Modifier.fillMaxWidth().height(170.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    Surface(onClick = { coverPicker.launch("image/*") }, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Photo, null, tint = Color.White)
                            Text("  " + tr("profile_edit_cover"), color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        if (!avatarPreview.isNullOrBlank()) AsyncImage(avatarPreview, null, Modifier.size(120.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else OrraAvatar(displayName.ifBlank { username }, null, 120.dp)
                        Surface(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.align(Alignment.BottomEnd).size(38.dp),
                            shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayName.ifBlank { username }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("@" + username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            title = { Text(tr("s_9965e37a")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (!coverPreview.isNullOrBlank()) AsyncImage(coverPreview, null,
                        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(14.dp)))
                    if (!avatarPreview.isNullOrBlank()) AsyncImage(avatarPreview, null,
                        Modifier.size(72.dp).clip(CircleShape))
                    OutlinedTextField(displayName, { displayName = it }, label = { Text(tr("s_bc9e270b")) }, singleLine = true)
                    OutlinedTextField(bio, { bio = it }, label = { Text(tr("s_8f7f4c1c")) }, maxLines = 3)
                    OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text(tr("s_ea7237f4")) }, singleLine = true)
                    Text(tr("s_6e08c853"),
                        style = MaterialTheme.typography.bodySmall)
                    if (phoneNumber.isNotBlank() && phoneNumber != profile?.phoneNumber && !phoneOtpPending) {
                        OutlinedButton(enabled = !busy, onClick = {
                            runTask {
                                withContext(Dispatchers.IO) { client!!.sendPhoneChangeOtp(phoneNumber) }
                                phoneOtpPending = true
                                phoneOtpCode = ""
                                phoneStatus = "Verification code requested. Check the SMS inbox."
                            }
                        }) { Text(tr("s_a490be42")) }
                    }
                    if (phoneOtpPending) {
                        OutlinedTextField(phoneOtpCode, { phoneOtpCode = it.filter(Char::isDigit).take(6) },
                            label = { Text(tr("s_45eb9ede")) }, singleLine = true)
                        Button(enabled = !busy && phoneOtpCode.length == 6, onClick = {
                            runTask {
                                val response = withContext(Dispatchers.IO) { client!!.verifyPhoneChangeOtp(phoneNumber, phoneOtpCode) }
                                val access = response.optString("access_token")
                                if (access.isNotBlank()) {
                                    val nextRefresh = response.optString("refresh_token").ifBlank { refreshToken }
                                    val expires = response.optLong("expires_in", 3600L)
                                    SupabaseChatClient.saveSession(context, access, userId, username, nextRefresh, expires)
                                    token = access
                                    refreshToken = nextRefresh
                                }
                                profile = withContext(Dispatchers.IO) { client!!.loadMyProfile() }
                                phoneNumber = profile?.phoneNumber.orEmpty()
                                phoneOtpPending = false
                                phoneOtpCode = ""
                                phoneStatus = if (phoneNumber.isNotBlank()) "Phone number verified." else "Verification was not confirmed by the server."
                            }
                        }) { Text(tr("s_db566c12")) }
                        TextButton(onClick = { phoneOtpPending = false; phoneOtpCode = "" }) { Text(tr("s_b2f2a308")) }
                    }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }) { Icon(Icons.Default.Person, null); Text(" " + tr("s_c03d53b7")) }
                        OutlinedButton(onClick = { coverPicker.launch("image/*") }) { Icon(Icons.Default.Photo, null); Text(" " + tr("s_c795dfff")) }
                    }
                    Text(tr("s_bc43a2b2"), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runTask {
                        withContext(Dispatchers.IO) {
                            client!!.updateProfile(displayName, bio, newAvatarPath, newCoverPath)
                        }
                        profile = withContext(Dispatchers.IO) { client!!.loadMyProfile() }
                        newAvatarPath = null; newCoverPath = null
                        showProfile = false
                    }
                }) { Text(tr("s_5d3d95cb")) }
            },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text(tr("s_ea478870")) } }
        )
    }

    if (showGroup) {
        AlertDialog(
            onDismissRequest = { showGroup = false; addingToGroup = null },
            title = { Text(if (addingToGroup == null) "Create group" else "Add group members") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (addingToGroup == null) OutlinedTextField(groupTitle, { groupTitle = it }, label = { Text(tr("s_c6155aae")) }, singleLine = true)
                    OutlinedTextField(search, { search = it }, label = { Text(tr("s_94e8be8c")) }, singleLine = true)
                    Button(enabled = search.isNotBlank() && !busy, onClick = {
                        runTask { users = withContext(Dispatchers.IO) { client!!.searchUsers(search) } }
                    }) { Text(tr("s_df32b432")) }
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
            dismissButton = { TextButton(onClick = { showGroup = false; addingToGroup = null }) { Text(tr("s_ea478870")) } }
        )
    }

    if (showChatOptions && selected != null) {
        AlertDialog(
            onDismissRequest = { showChatOptions = false },
            title = { Text(tr("s_dfe59e71")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("s_c7bf504f"))
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
                                    .onFailure { android.util.Log.w("ORRA", "Contact save failed", it) }
                            } else {
                                pendingSaveContact = contact
                                contactsWritePermission.launch(Manifest.permission.WRITE_CONTACTS)
                            }
                        }) { Text(tr("s_45248b9a")) }
                    }
                    if (selected!!.isGroup) {
                        TextButton(onClick = {
                            runTask {
                                withContext(Dispatchers.IO) { client!!.leaveGroup(selected!!.id) }
                                selected = null
                                conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                            }
                            showChatOptions = false
                        }) { Text(tr("s_129b9e34")) }
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
                        }) { Text(tr("s_f84e2dc4")) }
                    }
                    TextButton(onClick = {
                        showDeletedHistory = true
                        showChatOptions = false
                    }) { Text(tr("s_e5a96785")) }
                    TextButton(onClick = {
                        runTask {
                            withContext(Dispatchers.IO) { client!!.deleteConversationForMe(selected!!.id) }
                            selected = null
                            conversations = withContext(Dispatchers.IO) { client!!.loadConversations() }
                        }
                        showChatOptions = false
                    }) { Text(tr("s_b3b95826"), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { showChatOptions = false }) { Text(tr("s_d3d2e617")) } }
        )
    }

    if (showDeletedHistory) {
        AlertDialog(
            onDismissRequest = { showDeletedHistory = false },
            title = { Text(tr("s_9c78b3e3")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (deletedHistory.isEmpty()) Text(tr("s_ab03e279"))
                    else deletedHistory.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { showDeletedHistory = false }) { Text(tr("s_d3d2e617")) } }
        )
    }

    if (selectedMessage != null && selected != null) {
        val message = selectedMessage!!
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text(tr("s_04a07156")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.senderId == userId && !message.deletedForEveryone) {
                        if (message.type == "text") {
                            OutlinedTextField(editText, { editText = it }, label = { Text(tr("s_c128557c")) }, maxLines = 4)
                            TextButton(onClick = {
                                runTask {
                                    withContext(Dispatchers.IO) { client!!.editMessage(message.id, editText) }
                                    messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                                }
                                selectedMessage = null
                            }) { Text(tr("s_34c54dc7")) }
                        }
                        TextButton(onClick = {
                            runTask {
                                withContext(Dispatchers.IO) { client!!.deleteMessageForEveryone(message.id) }
                                messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                            }
                            selectedMessage = null
                        }) { Text(tr("s_ac3c6d8b"), color = MaterialTheme.colorScheme.error) }
                    }
                    if (messageHistory.isNotEmpty()) {
                        Text(tr("s_3d14cac6"), fontWeight = FontWeight.SemiBold)
                        messageHistory.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = {
                        runTask {
                            withContext(Dispatchers.IO) { client!!.deleteMessageForMe(message.id) }
                            messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                        }
                        selectedMessage = null
                    }) { Text(tr("s_c7fe5a06")) }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text(tr("s_d3d2e617")) } }
        )
    }
}
