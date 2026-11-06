package com.orra.app

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.height
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import androidx.compose.material3.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun CallsScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(readOrraCallHistory(context)) }
    val prefs = remember { context.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE) }
    val userId = prefs.getString("user_id", "").orEmpty()
    val accessToken = prefs.getString("token", "").orEmpty()
    var incomingCalls by remember { mutableStateOf<List<OrraInternetCall>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var directionFilter by remember { mutableStateOf("All") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showVoiceStudio by remember { mutableStateOf(false) }
    val startCall = rememberOrraCallAction()
    val filteredEntries = remember(entries, query) {
        entries.filter { entry ->
            (directionFilter == "All" || entry.direction.equals(directionFilter, ignoreCase = true)) &&
                (query.isBlank() || entry.name.contains(query, ignoreCase = true) || entry.number.contains(query, ignoreCase = true))
        }
    }

    LaunchedEffect(userId, accessToken) {
        if (userId.isBlank() || accessToken.isBlank()) return@LaunchedEffect
        val api = OrraCallSignaling(context, userId, accessToken)
        while (isActive && OrraInternetCallState.callId == null) {
            runCatching { incomingCalls = withContext(Dispatchers.IO) { api.incomingCalls() } }
            delay(2500)
        }
    }

    LaunchedEffect(Unit) {
        entries = readOrraCallHistory(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Calls", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Incoming and outgoing calls handled by ORRA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            OutlinedButton(onClick = { showVoiceStudio = true }) { Text("Voice Studio / REC") }
            IconButton(onClick = { showClearDialog = true }, enabled = entries.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = "Clear call history")
            }
        }

        if (incomingCalls.isNotEmpty()) {
            Text("Incoming ORRA internet calls", style = MaterialTheme.typography.titleMedium)
            incomingCalls.forEach { call ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (call.type == "video") "Incoming video call" else "Incoming voice call", fontWeight = FontWeight.SemiBold)
                        Text("ORRA user", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        runCatching {
                            OrraCallSignaling(context, userId, accessToken).updateStatus(call.id, "accepted")
                            OrraInternetCallState.begin(call, "ORRA contact", false, userId, accessToken)
                            incomingCalls = incomingCalls.filterNot { it.id == call.id }
                        }
                    }) { Text("Answer") }
                    OutlinedButton(onClick = {
                        runCatching { OrraCallSignaling(context, userId, accessToken).updateStatus(call.id, "rejected") }
                        incomingCalls = incomingCalls.filterNot { it.id == call.id }
                    }) { Text("Decline") }
                }
            }
        }

        if (entries.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Incoming", "Outgoing").forEach { filter ->
                    if (directionFilter == filter) androidx.compose.material3.Button(onClick = { directionFilter = filter }) { Text(filter) }
                    else OutlinedButton(onClick = { directionFilter = filter }) { Text(filter) }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search name or number") }
            )
        }

        if (entries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text("No ORRA call activity yet", fontWeight = FontWeight.Medium)
                    Text(
                        "Numbers opened from the ORRA keypad or contacts appear here. This is a local app history, not your phone's full call log.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (filteredEntries.isEmpty()) {
            Text(
                "No matching numbers",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredEntries, key = { "${it.timestamp}-${it.number}" }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.Medium)
                            Text(entry.number, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(entry.direction, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        IconButton(onClick = {
                            startCall(entry.name, entry.number)
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Open dialer for ${entry.name}", tint = CallGreen)
                        }
                    }
                }
            }
        }
    }

    if (showVoiceStudio) {
        Dialog(onDismissRequest = { showVoiceStudio = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(680.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.background
            ) {
                VoiceIsolationScreen()
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear ORRA call history?") },
            text = { Text("This only clears the local history stored by ORRA.") },
            confirmButton = {
                TextButton(onClick = {
                    clearOrraCallHistory(context)
                    entries = emptyList()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

