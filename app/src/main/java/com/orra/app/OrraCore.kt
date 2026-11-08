package com.orra.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Shared preferences used by every ORRA setting (same file the app already uses). */
object OrraPrefs {
    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(SupabaseChatClient.PREFS, Context.MODE_PRIVATE)
    fun animationsEnabled(context: Context): Boolean =
        get(context).getBoolean("chat_animations", true) && !OrraPower.active(context)
}

object OrraPrivacy {
    /** False = "Last seen & online: Nobody". Peers then always see you as offline. */
    fun shareOnline(context: Context): Boolean = !OrraPrefs.get(context).getBoolean("hide_last_seen", false)
}

/** Salted PBKDF2 passwords stored on this device (chat locks and folder locks). */
object OrraLocks {
    val version = mutableIntStateOf(0)
    private val unlocked = mutableSetOf<String>()
    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("orra_locks", Context.MODE_PRIVATE)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hash(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }
    fun has(context: Context, key: String): Boolean = sp(context).contains(key)
    fun set(context: Context, key: String, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(context).edit().putString(key, hex(salt) + ":" + hash(password, salt)).apply()
        unlocked.remove(key); version.intValue++
    }
    fun remove(context: Context, key: String) { sp(context).edit().remove(key).apply(); unlocked.remove(key); version.intValue++ }
    fun verify(context: Context, key: String, password: String): Boolean {
        val stored = sp(context).getString(key, null) ?: return true
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ok = MessageDigest.isEqual(hash(password, salt).toByteArray(), parts[1].toByteArray())
        if (ok) { unlocked.add(key); version.intValue++ }
        return ok
    }
    fun isUnlocked(key: String): Boolean = unlocked.contains(key)
    fun removeAll(context: Context) { sp(context).edit().clear().apply(); unlocked.clear(); version.intValue++ }
    fun lockAll() { unlocked.clear(); version.intValue++ }
    // Chat helpers
    fun chatKey(conversationId: String) = "chat:$conversationId"
    fun isChatLocked(context: Context, conversationId: String): Boolean = has(context, chatKey(conversationId))
}

data class OrraFolder(val id: String, val name: String, val chatIds: Set<String>)

object OrraFolders {
    fun folderKey(id: String) = "folder:$id"
    fun list(context: Context): List<OrraFolder> {
        OrraLocks.version.intValue // subscribe composables to lock changes
        val raw = OrraPrefs.get(context).getString("chat_folders", "[]").orEmpty()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("chats") ?: JSONArray()
                OrraFolder(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) }.toSet())
            }
        }.getOrDefault(emptyList())
    }
    fun save(context: Context, folders: List<OrraFolder>) {
        val arr = JSONArray()
        folders.forEach { f -> arr.put(JSONObject().put("id", f.id).put("name", f.name).put("chats", JSONArray(f.chatIds.toList()))) }
        OrraPrefs.get(context).edit().putString("chat_folders", arr.toString()).apply()
        OrraLocks.version.intValue++
    }
    fun create(context: Context, name: String, chatIds: Set<String>, password: String): OrraFolder {
        val folder = OrraFolder(UUID.randomUUID().toString(), name.trim(), chatIds)
        save(context, list(context) + folder)
        if (password.isNotBlank()) OrraLocks.set(context, folderKey(folder.id), password)
        return folder
    }
    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id }); OrraLocks.remove(context, folderKey(id))
    }
    fun isLocked(context: Context, id: String) = OrraLocks.has(context, folderKey(id)) && !OrraLocks.isUnlocked(folderKey(id))
    /** Chats that live in a password folder which is still locked: hidden from All / Groups / Direct. */
    fun hiddenChatIds(context: Context): Set<String> =
        list(context).filter { isLocked(context, it.id) }.flatMap { it.chatIds }.toSet()
}

object OrraDownloads {
    val kinds = listOf("image", "video", "audio", "file")
    private fun key(kind: String, group: Boolean) = "dl_" + (if (group) "group_" else "chat_") + kind
    fun enabled(context: Context, kind: String, group: Boolean) = OrraPrefs.get(context).getBoolean(key(kind, group), true)
    fun setEnabled(context: Context, kind: String, group: Boolean, value: Boolean) {
        OrraPrefs.get(context).edit().putBoolean(key(kind, group), value).apply()
    }
    fun wifiOnly(context: Context) = OrraPrefs.get(context).getBoolean("dl_wifi_only", false)
    fun allowed(context: Context, kind: String, group: Boolean): Boolean {
        if (!enabled(context, kind, group)) return false
        if (wifiOnly(context)) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            if (cm?.isActiveNetworkMetered == true) return false
        }
        return true
    }
}

object OrraPower {
    private fun lowBattery(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return scale > 0 && level * 100 / scale <= 20 && !charging
    }
    fun active(context: Context): Boolean {
        val prefs = OrraPrefs.get(context)
        return prefs.getBoolean("power_saving", false) || (prefs.getBoolean("power_auto_low", true) && lowBattery(context))
    }
    /** Background polling slows down 4x while power saving is active. */
    fun pollDelay(context: Context, baseMillis: Long): Long = if (active(context)) baseMillis * 4 else baseMillis
}

object OrraEye {
    val enabled = mutableStateOf(false)
    val level = mutableStateOf(0.5f)
    val auto = mutableStateOf(false)
    val island = mutableStateOf(true)
    fun load(context: Context) {
        val p = OrraPrefs.get(context)
        enabled.value = p.getBoolean("eye_enabled", false)
        level.value = p.getFloat("eye_level", 0.5f)
        auto.value = p.getBoolean("eye_auto", false)
        island.value = p.getBoolean("show_island", true)
    }
    fun save(context: Context) {
        OrraPrefs.get(context).edit().putBoolean("eye_enabled", enabled.value)
            .putFloat("eye_level", level.value).putBoolean("eye_auto", auto.value)
            .putBoolean("show_island", island.value).apply()
    }
    fun active(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return enabled.value || (auto.value && (hour >= 19 || hour < 7))
    }
}

object OrraWallpaper {
    val version = mutableIntStateOf(0)
    val presets = listOf("Default", "Midnight", "Ocean", "Violet")
    fun file(context: Context) = File(context.filesDir, "chat_wallpaper.jpg")
    fun current(context: Context) = OrraPrefs.get(context).getString("chat_wallpaper", "Default").orEmpty()
    fun setPreset(context: Context, name: String) {
        OrraPrefs.get(context).edit().putString("chat_wallpaper", name).apply(); version.intValue++
    }
    /** Copies and downsizes an image picked from the phone's media. */
    fun setCustom(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        setPreset(context, "Custom")
        true
    }.getOrDefault(false)
}

data class OrraSound(val key: String, val label: String, val resName: String)
data class OrraCustomSound(val id: String, val name: String, val file: String)

object OrraSounds {
    const val DEFAULT = "default"
    const val SILENT = "silent"
    val notificationTones = listOf(
        OrraSound("pulse", "Pulse", "orra_tone_pulse"), OrraSound("chime", "Chime", "orra_tone_chime"),
        OrraSound("drop", "Drop", "orra_tone_drop"), OrraSound("bell", "Bell", "orra_tone_bell"),
        OrraSound("pop", "Pop", "orra_tone_pop")
    )
    val ringtones = listOf(
        OrraSound("classic", "Classic", "orra_ring_classic"), OrraSound("pulse", "Pulse", "orra_ring_pulse"),
        OrraSound("glass", "Glass", "orra_ring_glass")
    )
    fun selection(context: Context, call: Boolean): String =
        OrraPrefs.get(context).getString(if (call) "sound_call" else "sound_message", DEFAULT) ?: DEFAULT
    fun setSelection(context: Context, call: Boolean, value: String) {
        OrraPrefs.get(context).edit().putString(if (call) "sound_call" else "sound_message", value).apply()
    }
    private fun dir(context: Context) = File(context.filesDir, "sounds").also { it.mkdirs() }
    fun customSounds(context: Context): List<OrraCustomSound> = runCatching {
        val arr = JSONArray(OrraPrefs.get(context).getString("custom_sounds", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); OrraCustomSound(o.getString("id"), o.getString("name"), o.getString("file")) }
    }.getOrDefault(emptyList())
    private fun saveCustom(context: Context, list: List<OrraCustomSound>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.file)) }
        OrraPrefs.get(context).edit().putString("custom_sounds", arr.toString()).apply()
    }
    /** Imports an audio file picked from the phone into private app storage. */
    fun addCustom(context: Context, uri: Uri): OrraCustomSound? = runCatching {
        var name = "Custom sound"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)?.substringBeforeLast('.') ?: name
        }
        val id = UUID.randomUUID().toString().take(8)
        val target = File(dir(context), "$id.snd")
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n; if (total > 15L * 1024 * 1024) { target.delete(); return null }
                    out.write(buffer, 0, n)
                }
            }
        } ?: return null
        val sound = OrraCustomSound(id, name.take(40), target.name)
        saveCustom(context, customSounds(context) + sound)
        sound
    }.getOrNull()
    fun removeCustom(context: Context, id: String) {
        customSounds(context).firstOrNull { it.id == id }?.let { File(dir(context), it.file).delete() }
        saveCustom(context, customSounds(context).filterNot { it.id == id })
        listOf(true, false).forEach { call -> if (selection(context, call) == "custom:$id") setSelection(context, call, DEFAULT) }
    }
    fun label(context: Context, selection: String): String = when {
        selection == DEFAULT -> "Default"
        selection == SILENT -> "Silent"
        selection.startsWith("builtin:") -> (notificationTones + ringtones).firstOrNull { it.resName == selection.removePrefix("builtin:") }?.label ?: "ORRA tone"
        selection.startsWith("custom:") -> customSounds(context).firstOrNull { it.id == selection.removePrefix("custom:") }?.name ?: "Custom"
        else -> "Default"
    }
    fun uriFor(context: Context, selection: String, call: Boolean): Uri? = when {
        selection == SILENT -> null
        selection.startsWith("builtin:") -> Uri.parse("android.resource://" + context.packageName + "/raw/" + selection.removePrefix("builtin:"))
        selection.startsWith("custom:") -> {
            val sound = customSounds(context).firstOrNull { it.id == selection.removePrefix("custom:") }
            val file = sound?.let { File(dir(context), it.file) }
            if (file != null && file.exists()) runCatching {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".orrafiles", file)
                // Notification channels are played by System UI, which needs read access to the file.
                context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                uri
            }.getOrNull() else null
        }
        else -> RingtoneManager.getDefaultUri(if (call) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
    }

    /** Channel sounds are immutable once created, so each sound/vibration combination gets its own channel id. */
    fun channelId(context: Context, kind: String): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (kind == "quiet") {
            if (manager.getNotificationChannel("orra_quiet") == null) {
                manager.createNotificationChannel(NotificationChannel("orra_quiet", "ORRA quiet (power saving)", NotificationManager.IMPORTANCE_LOW))
            }
            return "orra_quiet"
        }
        val call = kind == "call"
        val sel = selection(context, call)
        val vibrate = OrraPrefs.get(context).getBoolean("notification_vibration", true)
        val id = "orra_${kind}_" + Integer.toHexString((sel + vibrate).hashCode())
        manager.notificationChannels.filter { it.id.startsWith("orra_${kind}_") && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, if (call) "ORRA calls" else "ORRA messages", NotificationManager.IMPORTANCE_HIGH)
            channel.enableVibration(vibrate)
            val attrs = AudioAttributes.Builder()
                .setUsage(if (call) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            channel.setSound(uriFor(context, sel, call), attrs)
            manager.createNotificationChannel(channel)
        }
        return id
    }
}

object OrraRinger {
    private var player: MediaPlayer? = null
    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
    fun play(context: Context, selection: String, call: Boolean, loop: Boolean) {
        stop()
        val app = context.applicationContext
        val uri = OrraSounds.uriFor(app, selection, call) ?: return
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(if (call) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            mp.setDataSource(app, uri)
            mp.isLooping = loop
            mp.setOnCompletionListener { if (!loop) stop() }
            mp.prepare()
            mp.start()
            player = mp
        }.onFailure { Log.w("ORRA", "Sound playback failed", it) }
    }
    fun startIncomingCallRing(context: Context) {
        if (!OrraPrefs.get(context).getBoolean("call_notifications", true)) return
        play(context, OrraSounds.selection(context, true), true, true)
    }
}

object OrraNotify {
    /** kind: "message" or "call". Honors the user's switches, power saving and chat locks. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, kind: String, title: String, body: String, conversationId: String? = null) {
        val app = context.applicationContext
        val prefs = OrraPrefs.get(app)
        if (kind == "message" && !prefs.getBoolean("message_notifications", true)) return
        if (kind == "call" && !prefs.getBoolean("call_notifications", true)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val locked = conversationId != null && OrraLocks.isChatLocked(app, conversationId)
        val channel = if (kind == "message" && OrraPower.active(app)) OrraSounds.channelId(app, "quiet") else OrraSounds.channelId(app, kind)
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(app, channel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(if (locked) "ORRA" else title)
            .setContentText(if (locked) "New message" else body)
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(if (kind == "call") NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (!prefs.getBoolean("notification_vibration", true)) builder.setVibrate(null)
        app.getSystemService(NotificationManager::class.java).notify((conversationId ?: title).hashCode(), builder.build())
    }
}

// ---------------------------------------------------------------- device sessions + push tokens

data class OrraSession(
    val id: String, val deviceId: String, val name: String, val os: String,
    val appVersion: String, val createdAt: String, val lastActive: String
)

object OrraDevice {
    fun id(context: Context): String {
        val prefs = OrraPrefs.get(context)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    }
    fun name(): String = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()
}

fun orraClient(context: Context): SupabaseChatClient? {
    val prefs = OrraPrefs.get(context)
    val userId = prefs.getString("user_id", "").orEmpty()
    val token = prefs.getString("token", "").orEmpty()
    if (userId.isBlank() || token.isBlank()) return null
    return SupabaseChatClient(context.applicationContext, SupabaseChatClient.DEFAULT_PROJECT_URL, SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY, token, userId)
}

fun SupabaseChatClient.registerSession(context: Context, reactivate: Boolean) {
    request("/rest/v1/rpc/register_session", "POST", JSONObject()
        .put("p_device_id", OrraDevice.id(context)).put("p_device_name", OrraDevice.name())
        .put("p_os_version", "Android " + Build.VERSION.RELEASE).put("p_app_version", BuildConfigVersion.name(context))
        .put("p_reactivate", reactivate))
}

fun SupabaseChatClient.isSessionActive(context: Context): Boolean =
    request("/rest/v1/rpc/is_session_active", "POST", JSONObject().put("p_device_id", OrraDevice.id(context))).trim() != "false"

fun SupabaseChatClient.loadSessions(): List<OrraSession> {
    val rows = JSONArray(request("/rest/v1/device_sessions?select=id,device_id,device_name,os_version,app_version,created_at,last_active&revoked=eq.false&order=last_active.desc"))
    return (0 until rows.length()).map { i ->
        val o = rows.getJSONObject(i)
        OrraSession(o.optString("id"), o.optString("device_id"), o.optString("device_name"), o.optString("os_version"),
            o.optString("app_version"), o.optString("created_at"), o.optString("last_active"))
    }
}

fun SupabaseChatClient.terminateSession(sessionId: String) {
    request("/rest/v1/rpc/terminate_session", "POST", JSONObject().put("p_session_id", sessionId))
}

fun SupabaseChatClient.terminateOtherSessions(context: Context) {
    request("/rest/v1/rpc/terminate_other_sessions", "POST", JSONObject().put("p_current_device_id", OrraDevice.id(context)))
}

fun SupabaseChatClient.savePushToken(context: Context, token: String) {
    request("/rest/v1/rpc/save_push_token", "POST", JSONObject().put("p_device_id", OrraDevice.id(context)).put("p_token", token))
}

object BuildConfigVersion {
    fun name(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

/** Registers/validates this device. Returns false when the session was ended from another device. */
object OrraSessionGuard {
    fun check(context: Context): Boolean {
        val client = orraClient(context) ?: return true
        val prefs = OrraPrefs.get(context)
        return runCatching {
            val fresh = !prefs.getBoolean("session_registered", false)
            client.registerSession(context, fresh)
            if (fresh) prefs.edit().putBoolean("session_registered", true).apply()
            client.isSessionActive(context)
        }.getOrElse { Log.w("ORRA", "Session check skipped", it); true }
    }
    fun registerPushToken(context: Context) {
        val client = orraClient(context) ?: return
        val token = OrraPrefs.get(context).getString("fcm_token", null) ?: return
        runCatching { client.savePushToken(context, token) }.onFailure { Log.w("ORRA", "Push token upload failed", it) }
    }
}

object OrraNet {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Refreshes the saved Supabase session. Returns true when a new access token was stored. */
fun orraRefreshSession(context: Context): Boolean {
    val prefs = OrraPrefs.get(context)
    val refresh = prefs.getString("refresh_token", "").orEmpty()
    if (refresh.isBlank()) return false
    return runCatching {
        val client = SupabaseChatClient(context.applicationContext,
            prefs.getString("url", SupabaseChatClient.DEFAULT_PROJECT_URL).orEmpty(),
            prefs.getString("key", SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY).orEmpty())
        val result = client.refreshSession(refresh)
        val access = result.optString("access_token")
        if (access.isBlank()) false else {
            SupabaseChatClient.saveSession(context, access, prefs.getString("user_id", "").orEmpty(),
                prefs.getString("username", "").orEmpty(), result.optString("refresh_token").ifBlank { refresh }, result.optLong("expires_in", 3600L))
            true
        }
    }.getOrDefault(false)
}

object OrraSfx {
    val events = listOf("tap", "send", "receive", "call_start", "call_end", "error")
    private var pool: android.media.SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    fun masterEnabled(context: Context) = OrraPrefs.get(context).getBoolean("sfx_master", true)
    fun setMaster(context: Context, value: Boolean) { OrraPrefs.get(context).edit().putBoolean("sfx_master", value).apply() }
    fun enabled(context: Context, event: String) = OrraPrefs.get(context).getBoolean("sfx_$event", event != "tap")
    fun setEnabled(context: Context, event: String, value: Boolean) { OrraPrefs.get(context).edit().putBoolean("sfx_$event", value).apply() }
    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        val p = android.media.SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        ids["tap"] = p.load(app, R.raw.orra_sfx_tap, 1)
        ids["send"] = p.load(app, R.raw.orra_sfx_send, 1)
        ids["receive"] = p.load(app, R.raw.orra_sfx_receive, 1)
        ids["call_start"] = p.load(app, R.raw.orra_sfx_call_start, 1)
        ids["call_end"] = p.load(app, R.raw.orra_sfx_call_end, 1)
        ids["error"] = p.load(app, R.raw.orra_sfx_error, 1)
        pool = p
    }
    fun play(context: Context, event: String) {
        if (!masterEnabled(context) || !enabled(context, event)) return
        init(context)
        ids[event]?.let { pool?.play(it, 0.7f, 0.7f, 1, 0, 1f) }
    }
}

object OrraSecurity {
    val secureScreen = mutableStateOf(false)
    val locked = mutableStateOf(false)
    var lastStopAt = 0L
    fun load(context: Context) {
        val p = OrraPrefs.get(context)
        secureScreen.value = p.getBoolean("secure_screen", false)
        locked.value = p.getBoolean("app_lock", false) && deviceSecure(context)
    }
    fun deviceSecure(context: Context): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
    fun setSecureScreen(context: Context, value: Boolean) {
        OrraPrefs.get(context).edit().putBoolean("secure_screen", value).apply(); secureScreen.value = value
    }
    fun appLockEnabled(context: Context) = OrraPrefs.get(context).getBoolean("app_lock", false)
    fun setAppLock(context: Context, value: Boolean) { OrraPrefs.get(context).edit().putBoolean("app_lock", value).apply() }
    fun looksRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        return listOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/data/local/su", "/su/bin/su").any { File(it).exists() }
    }
    fun credentialIntent(context: Context): Intent? =
        context.getSystemService(android.app.KeyguardManager::class.java)
            ?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent("ORRA", "Unlock ORRA")
}
