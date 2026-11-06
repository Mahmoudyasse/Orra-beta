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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
    val recorder = remember { OrraVoiceRecorder(context.cacheDir) }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to record") }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun beginRecording() {
        recording = true
        status = "Recording with available voice processing..."
        recorder.start(
            onComplete = { file -> recording = false; savedFile = file; status = "Processed recording saved" },
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
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Voice Isolation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Reduce background noise while recording your voice. Results depend on your phone's microphone and Android audio-effect support.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("  Microphone processing", fontWeight = FontWeight.SemiBold)
                }
                Text(if (recorder.noiseSuppressionAvailable()) "Android noise suppression is available on this device."
                    else "Android does not report a hardware noise-suppression effect. Results may be limited.",
                    style = MaterialTheme.typography.bodySmall)
                Text(status, color = MaterialTheme.colorScheme.primary)
                if (recording) {
                    Button(onClick = { recorder.stop(); status = "Finishing recording..." }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null); Text("  Stop recording")
                    }
                } else {
                    Button(onClick = { if (hasPermission) beginRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Mic, contentDescription = null); Text("  Start voice recording")
                    }
                }
                if (savedFile != null && !recording) {
                    OutlinedButton(onClick = {
                        runCatching {
                            player?.release()
                            player = MediaPlayer().apply {
                                setDataSource(savedFile!!.absolutePath)
                                setOnCompletionListener { it.release(); player = null }
                                prepare(); start()
                            }
                            status = "Playing processed recording"
                        }.onFailure { status = "Could not play this recording" }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null); Text("  Play last recording")
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("This feature processes recordings made inside ORRA. It cannot replace the microphone signal used by WhatsApp or other apps. Custom noise reduction is not applied to cellular-call audio in this beta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}
