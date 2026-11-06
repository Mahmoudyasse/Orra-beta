package com.orra.app

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import android.app.Activity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OrraAppearance.load(this)
        consumeAuthCallback(intent)
        setContent { OrraTheme { if (OrraInternetCallState.callId != null) OrraInternetCallScreen() else if (OrraCallState.activeCall != null) InCallScreen() else OrraApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAuthCallback(intent)
    }

    private fun consumeAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.orra.beta" || uri.host != "auth-callback") return
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
    Tab("Keypad", Icons.Filled.Phone, GlowKeypad),
    Tab("Contacts", Icons.Filled.Person, GlowContacts),
    Tab("Settings", Icons.Filled.Settings, GlowSettings)
)

@Composable
fun OrraApp() {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1200); splash = false }
    if (splash) { Splash(); return }

    var selected by remember { mutableIntStateOf(2) }
    var tabHistory by remember { mutableStateOf<List<Int>>(emptyList()) }
    BackHandler(enabled = true) {
        if (tabHistory.isNotEmpty()) {
            selected = tabHistory.last()
            tabHistory = tabHistory.dropLast(1)
        } else if (selected != 2) {
            selected = 2
        }
    }
    Scaffold(bottomBar = { NeonBar(selected) { next ->
        if (next != selected) { tabHistory = tabHistory + selected; selected = next }
    } }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                if (selected == 0) ChatScreen()
                else if (selected == 1) CallsScreen()
                else if (selected == 2) DialerScreen()
                else if (selected == 3) ContactsScreen()
                else if (selected == 4) SettingsScreen()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
            ) {
                OrraDynamicIsland()
            }
        }
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit ORRA?") },
            text = { Text("Press Exit to close the app, or Cancel to stay in ORRA.") },
            confirmButton = { TextButton(onClick = { showExitDialog = false; activity?.finish() }) { Text("Exit") } },
            dismissButton = { Button(onClick = { showExitDialog = false }) { Text("Stay in ORRA") } }
        )
    }
}

@Composable
private fun NeonBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
        Text(tab.label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        OrraWordmark(Modifier.fillMaxWidth(0.55f), color = Color.White, dot = Mint)
    }
}
