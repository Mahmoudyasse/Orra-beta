package com.orra.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.text.DateFormat
import java.util.Date

@Composable
fun CallsScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(readOrraCallHistory(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

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
                    "Numbers you called from ORRA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = { showClearDialog = true }, enabled = entries.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = "Clear call history")
            }
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
                        "Calls started from ORRA will appear here. This is a local app history, not your phone's full call log.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { "${it.timestamp}-${it.number}" }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.Medium)
                            Text(entry.number, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:" + Uri.encode(entry.number))
                            })
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call ${entry.name}", tint = CallGreen)
                        }
                    }
                }
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
