package com.orra.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import java.io.File

@Composable
fun VoiceIsolationScreen() {
    val context = LocalContext.current
    val recordingsDir = remember { File(context.filesDir, "orra_voice_recordings").apply { mkdirs() } }
    val recorder = remember { OrraVoiceRecorder(recordingsDir) }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to record") }
    var recordings by remember { mutableStateOf(recordingsDir.listFiles { file -> file.extension == "wav" }?.sortedByDescending { it.lastModified() } ?: emptyList()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingFile by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        recordings = recordingsDir.listFiles { file -> file.extension == "wav" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    fun beginRecording() {
        recording = true
        status = "Recording with available Android noise suppression, echo cancellation and gain control…"
        recorder.start(
            onComplete = { file ->
                recording = false
                refresh()
                status = "Saved securely inside ORRA"
            },
            onError = { message -> recording = false; status = message }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) beginRecording() else status = "Microphone permission was denied"
    }

    DisposableEffect(Unit) {
        onDispose { recorder.stop(); runCatching { player?.release() } }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 58.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("s_e85ac023"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tr("s_36a3f1ba"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("  " + tr("s_28e9b5b2"), fontWeight = FontWeight.SemiBold)
                }
                Text(if (recorder.noiseSuppressionAvailable()) "Noise suppression is supported by this device."
                    else "Noise suppression is not reported as available by Android on this device.",
                    style = MaterialTheme.typography.bodySmall)
                Text(status, color = MaterialTheme.colorScheme.primary)
                if (recording) {
                    Button(onClick = { recorder.stop(); status = "Finishing recording…" }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null); Text("  " + tr("s_54493c61"))
                    }
                } else {
                    Button(onClick = { if (hasPermission) beginRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Mic, contentDescription = null); Text("  " + tr("s_0368bafe"))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("s_e1fba67e"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${recordings.size} saved", style = MaterialTheme.typography.labelSmall)
        }
        if (recordings.isEmpty()) {
            Text(tr("s_5b1e3738"))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.absolutePath }) { file ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.nameWithoutExtension, fontWeight = FontWeight.Medium)
                                Text("${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                runCatching {
                                    player?.release()
                                    player = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener { it.release(); player = null; playingFile = null }
                                        prepare(); start()
                                    }
                                    playingFile = file.absolutePath
                                    status = "Playing ${file.name}"
                                }.onFailure { status = "Could not play recording" }
                            }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play recording") }
                            IconButton(onClick = {
                                if (playingFile == file.absolutePath) {
                                    runCatching { player?.release() }; player = null; playingFile = null
                                }
                                file.delete()
                                refresh()
                                status = "Recording deleted"
                            }) { Icon(Icons.Default.Delete, contentDescription = "Delete recording", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(tr("s_c0d71fde"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
