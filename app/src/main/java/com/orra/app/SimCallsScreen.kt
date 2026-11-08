package com.orra.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.DateFormat
import java.util.Date

data class SimCallEntry(val name: String, val number: String, val type: Int, val date: Long, val duration: Long)

/** Reads the phone's own call log (needs READ_CALL_LOG). */
@SuppressLint("MissingPermission")
fun readSimCallLog(context: Context): List<SimCallEntry> = runCatching {
    val list = mutableListOf<SimCallEntry>()
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
        null, null, CallLog.Calls.DATE + " DESC"
    )?.use { c ->
        while (c.moveToNext() && list.size < 150) {
            list.add(SimCallEntry(c.getString(1).orEmpty(), c.getString(0).orEmpty(), c.getInt(2), c.getLong(3), c.getLong(4)))
        }
    }
    list.toList()
}.getOrDefault(emptyList())

@Composable
fun SimCallsScreen() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) }
    var entries by remember { mutableStateOf<List<SimCallEntry>>(emptyList()) }
    val startCall = rememberOrraCallAction()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    fun reload() {
        entries = if (granted) readSimCallLog(context)
        else readOrraCallHistory(context).filter { !it.isInternet }
            .map { SimCallEntry(it.name, it.number, if (it.direction.equals("Incoming", true)) CallLog.Calls.INCOMING_TYPE else CallLog.Calls.OUTGOING_TYPE, it.timestamp, 0L) }
    }
    LaunchedEffect(granted) { reload() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, granted) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("sim_log"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tr("sim_log_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        if (!granted) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("call_log_note"), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { permission.launch(Manifest.permission.READ_CALL_LOG) }) { Text(tr("allow_call_log")) }
                }
            }
        }
        if (entries.isEmpty()) {
            Text(tr("no_sim_calls"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(entries, key = { it.date.toString() + it.number + it.type }) { entry ->
                    val missed = entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE
                    val label = when (entry.type) {
                        CallLog.Calls.INCOMING_TYPE -> tr("cl_incoming")
                        CallLog.Calls.OUTGOING_TYPE -> tr("cl_outgoing")
                        CallLog.Calls.MISSED_TYPE -> tr("cl_missed")
                        CallLog.Calls.REJECTED_TYPE -> tr("cl_rejected")
                        CallLog.Calls.BLOCKED_TYPE -> tr("cl_blocked")
                        else -> tr("cl_voicemail")
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (entry.type) { CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade; CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived; else -> Icons.Default.CallMissed },
                            contentDescription = label, tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(entry.name.ifBlank { entry.number.ifBlank { tr("unknown_number") } }, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(label + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.date)) +
                                (if (entry.duration > 0) " · " + entry.duration / 60 + ":" + "%02d".format(entry.duration % 60) else ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        if (entry.number.isNotBlank()) IconButton(onClick = { startCall(entry.name.ifBlank { entry.number }, entry.number) }) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = CallGreen)
                        }
                    }
                }
            }
        }
    }
}
