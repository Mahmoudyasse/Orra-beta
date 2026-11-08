package com.orra.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Calls over the internet: every ORRA chat with a video button and a voice button next to it. */
@Composable
fun CallsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { OrraPrefs.get(context) }
    val userId = prefs.getString("user_id", "").orEmpty()
    val accessToken = prefs.getString("token", "").orEmpty()
    var people by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val client = orraClient(context)
        if (client == null) status = tr("sign_in_first")
        else runCatching {
            people = withContext(Dispatchers.IO) { client.loadConversations() }.filter { !it.isAi && !it.isGroup && !it.peerUserId.isNullOrBlank() }
        }.onFailure { status = if (!OrraNet.isOnline(context)) tr("no_internet") else tr("load_failed") }
    }

    fun call(person: OrraChatConversation, type: String) {
        val peerId = person.peerUserId.orEmpty()
        if (!OrraNet.isOnline(context)) { status = tr("no_internet"); OrraSfx.play(context, "error"); return }
        status = ""
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { OrraCallSignaling(context, userId, accessToken).createCall(peerId, type) }
            }
            result.onSuccess { created ->
                OrraSfx.play(context, "call_start")
                OrraInternetCallState.begin(created, person.username, true, userId, accessToken)
                recordOrraInternetCall(context, person.username, peerId, "Outgoing", type)
            }.onFailure {
                OrraSfx.play(context, "error")
                status = if (!OrraNet.isOnline(context)) tr("no_internet") else (it.message ?: tr("calling_failed"))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("calls_title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tr("calls_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        OrraSearchPill(search, { search = it }, tr("list_search"))
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
        if (people.isEmpty() && status.isBlank()) Text(tr("no_contacts_call"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        LazyColumn {
            items(people.filter { search.isBlank() || it.username.contains(search, ignoreCase = true) }, key = { it.id }) { person ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrraAvatar(person.username, null, 50.dp)
                    Text(person.username, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    IconButton(onClick = { call(person, "video") }) { Icon(Icons.Default.Videocam, contentDescription = tr("video_call"), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { call(person, "audio") }) { Icon(Icons.Default.Call, contentDescription = tr("voice_call"), tint = CallGreen) }
                }
            }
        }
    }
}
