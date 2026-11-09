package com.orra.app

import androidx.compose.material.icons.filled.SimCard
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity

class MainActivity : ComponentActivity() {
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_UP) OrraSfx.play(this, "tap")
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        OrraSecurity.lastStopAt = android.os.SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (OrraSecurity.appLockEnabled(this) && OrraSecurity.lastStopAt > 0 &&
            android.os.SystemClock.elapsedRealtime() - OrraSecurity.lastStopAt > 15_000 && OrraSecurity.deviceSecure(this)) {
            OrraSecurity.locked.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OrraAppearance.load(this)
        OrraLang.load(this)
        OrraEye.load(this)
        OrraSecurity.load(this)
        OrraAccess.load(this)
        OrraSfx.init(this)
        Thread { runCatching { OrraWallpaperCache.load(applicationContext) } }.start()
        consumeAuthCallback(intent)
        setContent {
            OrraTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides (OrraLang.layoutOverride() ?: LocalLayoutDirection.current),
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density,
                        androidx.compose.ui.platform.LocalDensity.current.fontScale * OrraAccess.scale.value)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        OrraApp()
                        if (OrraInternetCallState.callId != null) {
                            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { OrraInternetCallScreen() }
                        } else if (OrraCallState.activeCall != null) {
                            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { InCallScreen() }
                        }
                        OrraEyeOverlay()
                        OrraSecurityLayer()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAuthCallback(intent)
    }

    private fun consumeAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.orra.beta" || uri.host != "auth-callback") return
        // Clear the one-shot callback intent before recreating the Activity; otherwise the same token link is consumed repeatedly.
        setIntent(Intent(this, MainActivity::class.java))
        // Clear the callback URI before recreating the activity so it cannot be consumed repeatedly.
        intent.data = null
        setIntent(intent)
        val values = uri.fragment.orEmpty().split("&").mapNotNull { part ->
            val split = part.indexOf("=")
            if (split <= 0) null else Uri.decode(part.substring(0, split)) to Uri.decode(part.substring(split + 1))
        }.toMap()
        val access = values["access_token"].orEmpty()
        val refresh = values["refresh_token"].orEmpty()
        val expires = values["expires_in"]?.toLongOrNull() ?: 3600L
        if (access.isBlank()) return
        Thread {
            try {
                val connection = URL(SupabaseChatClient.DEFAULT_PROJECT_URL + "/auth/v1/user").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("apikey", SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY)
                connection.setRequestProperty("Authorization", "Bearer " + access)
                val body = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                connection.disconnect()
                val user = JSONObject(body)
                val id = user.optString("id")
                if (id.isNotBlank()) {
                    val email = user.optString("email")
                    val name = user.optJSONObject("user_metadata")?.optString("username")?.takeIf { !it.isNullOrBlank() } ?: email.substringBefore("@")
                    SupabaseChatClient.saveSession(this, access, id, name, refresh, expires)
                    runOnUiThread { recreate() }
                }
            } catch (_: Exception) { }
        }.start()
    }
}

private data class Tab(val label: String, val icon: ImageVector, val color: Color)

private val tabs = listOf(
    Tab("Chats", Icons.Filled.Email, GlowChats),
    Tab("Calls", Icons.Filled.Call, GlowCalls),
    Tab("SIM", Icons.Filled.SimCard, GlowSim),
    Tab("Keypad", Icons.Filled.Phone, GlowKeypad),
    Tab("Contacts", Icons.Filled.Person, GlowContacts),
    Tab("Settings", Icons.Filled.Settings, GlowSettings)
)

@Composable
fun OrraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var incomingCall by remember { mutableStateOf<OrraInternetCall?>(null) }
    var incomingCallError by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE)
        while (isActive) {
            if (OrraInternetCallState.callId == null && OrraCallState.activeCall == null) {
                val userId = prefs.getString("user_id", "").orEmpty()
                val token = prefs.getString("token", "").orEmpty()
                if (userId.isNotBlank() && token.isNotBlank()) {
                    incomingCall = runCatching {
                        withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).incomingCalls().firstOrNull() }
                    }.getOrNull()
                } else incomingCall = null
            } else incomingCall = null
            delay(OrraPower.pollDelay(context, 2500))
        }
    }
    LaunchedEffect(Unit) {
        OrraPush.fetchToken(context)
        var pushed = false
        while (isActive) {
            val signedIn = OrraPrefs.get(context).getString("token", "").orEmpty().isNotBlank()
            if (signedIn) {
                val active = withContext(Dispatchers.IO) { OrraSessionGuard.check(context) }
                if (!active) {
                    SupabaseChatClient.clearSession(context)
                    (context as? Activity)?.recreate()
                    return@LaunchedEffect
                }
                if (!pushed) { withContext(Dispatchers.IO) { OrraSessionGuard.registerPushToken(context) }; pushed = true }
            }
            delay(OrraPower.pollDelay(context, 30000))
        }
    }
    val permissionRequest = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val prefs = OrraPrefs.get(context)
        if (!prefs.getBoolean("permissions_asked", false)) {
            prefs.edit().putBoolean("permissions_asked", true).apply()
            val wanted = mutableListOf(
                android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_CALL_LOG)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                wanted += listOf(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
            } else wanted += android.Manifest.permission.READ_EXTERNAL_STORAGE
            val missing = wanted.filter { androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
        }
    }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1200); splash = false }
    if (splash) { Splash(); return }

    var selected by remember { mutableIntStateOf(3) }
    LaunchedEffect(OrraNav.tab.value) { OrraNav.tab.value?.let { selected = it; OrraNav.tab.value = null } }
    var tabHistory by remember { mutableStateOf<List<Int>>(emptyList()) }
    BackHandler(enabled = true) {
        if (tabHistory.isNotEmpty()) {
            selected = tabHistory.last()
            tabHistory = tabHistory.dropLast(1)
        } else if (selected != 3) {
            selected = 3
        }
    }
    Scaffold(bottomBar = { NeonBar(selected) { next ->
        if (next != selected) { tabHistory = tabHistory + selected; selected = next }
    } }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                if (selected == 0) ChatScreen()
                else if (selected == 1) CallsScreen()
                else if (selected == 2) SimCallsScreen()
                else if (selected == 3) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { DialerScreen { selected = 0 } }
                else if (selected == 4) ContactsScreen()
                else if (selected == 5) SettingsScreen()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
            ) {
                if (OrraEye.island.value) OrraDynamicIsland()
            }
        }
    }
    val pendingCall = incomingCall
    DisposableEffect(pendingCall?.id) {
        if (pendingCall != null) OrraRinger.startIncomingCallRing(context)
        onDispose { OrraRinger.stop() }
    }
    if (pendingCall != null && OrraInternetCallState.callId == null && OrraCallState.activeCall == null) {
        val prefs = context.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "").orEmpty()
        val token = prefs.getString("token", "").orEmpty()
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (pendingCall.type == "video") "Incoming ORRA video call" else "Incoming ORRA voice call") },
            text = {
                Column {
                    Text(tr("s_86db8b3b"))
                    if (incomingCallError.isNotBlank()) Text(incomingCallError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).updateStatus(pendingCall.id, "accepted") }
                        }
                        result.onSuccess {
                            recordOrraInternetCall(context, "ORRA contact", pendingCall.callerId, "Incoming", pendingCall.type)
                            OrraInternetCallState.begin(pendingCall, "ORRA contact", false, userId, token)
                            incomingCall = null
                            incomingCallError = ""
                        }.onFailure { incomingCallError = it.message ?: "Could not answer the call." }
                    }
                }) { Text(tr("s_3751695c")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, token).updateStatus(pendingCall.id, "rejected") }
                        }
                        result.onSuccess { incomingCall = null; incomingCallError = "" }
                            .onFailure { incomingCallError = it.message ?: "Could not decline the call." }
                    }
                }) { Text(tr("s_0d362728")) }
            }
        )
    }
}

@Composable
private fun NeonBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { i, t -> NavItem(t, selected == i) { onSelect(i) } }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val tint = if (selected) tab.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Column(
        Modifier
            .weight(1f)
            .height(62.dp)
            .neonPress(tab.color, 22.dp, if (selected) 0.8f else 0f, onClick)
            .background(if (selected) tab.color.copy(alpha = 0.12f) else Color.Transparent, shape)
            .then(if (selected) Modifier.border(1.5.dp, tab.color, shape) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = tr("tab_" + tab.label.lowercase()), tint = tint, modifier = Modifier.size(22.dp))
        Text(tr("tab_" + tab.label.lowercase()), fontSize = 9.5.sp, color = tint, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        OrraWordmark(Modifier.fillMaxWidth(0.55f), color = Color.White, dot = Mint)
    }
}
