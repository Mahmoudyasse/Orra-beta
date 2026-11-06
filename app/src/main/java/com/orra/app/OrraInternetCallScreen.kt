package com.orra.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.SurfaceViewRenderer

object OrraInternetCallState {
    var callId by mutableStateOf<String?>(null)
    var peerId by mutableStateOf("")
    var peerName by mutableStateOf("")
    var callType by mutableStateOf("audio")
    var outgoing by mutableStateOf(true)
    var userId by mutableStateOf("")
    var accessToken by mutableStateOf("")

    fun begin(call: OrraInternetCall, name: String, isOutgoing: Boolean, uid: String, token: String) {
        callId = call.id
        peerId = if (call.callerId == uid) call.calleeId else call.callerId
        peerName = name
        callType = call.type
        outgoing = isOutgoing
        userId = uid
        accessToken = token
    }

    fun clear() {
        callId = null
        peerId = ""
        peerName = ""
        callType = "audio"
        outgoing = true
        userId = ""
        accessToken = ""
    }
}

@Composable
fun OrraInternetCallScreen() {
    BackHandler(enabled = true) { }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callId = OrraInternetCallState.callId ?: return
    val video = OrraInternetCallState.callType == "video"
    val token = OrraInternetCallState.accessToken
    val userId = OrraInternetCallState.userId
    val caller = OrraInternetCallState.outgoing
    var status by remember(callId) { mutableStateOf(if (caller) "Calling…" else "Connecting…") }
    var error by remember(callId) { mutableStateOf("") }
    var muted by remember(callId) { mutableStateOf(false) }
    var speaker by remember(callId) { mutableStateOf(true) }
    var cameraOn by remember(callId) { mutableStateOf(true) }
    var permissionGranted by remember(callId) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionGranted = grants[Manifest.permission.RECORD_AUDIO] == true &&
            (!video || grants[Manifest.permission.CAMERA] == true)
        if (!permissionGranted) {
            error = "Microphone and camera permission is required. The call was ended because media access was denied."
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { OrraCallSignaling(context, userId, token).updateStatus(callId, "ended") }
                }
                if (OrraInternetCallState.callId == callId) OrraInternetCallState.clear()
            }
        }
    }
    val localRenderer = remember(callId) { SurfaceViewRenderer(context) }
    val remoteRenderer = remember(callId) { SurfaceViewRenderer(context) }
    var engine by remember(callId) { mutableStateOf<OrraWebRtcEngine?>(null) }

    LaunchedEffect(callId) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = true
    }

    LaunchedEffect(callId, permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        runCatching {
            val serviceIntent = Intent(context, OrraCallForegroundService::class.java)
                .putExtra(OrraCallForegroundService.EXTRA_VIDEO, video)
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure { error = it.message ?: "Could not keep the call active in the background." }
        val api = OrraCallSignaling(context, userId, token)
        val rtc = OrraWebRtcEngine(context, api, callId, caller, video,
            onStatus = { status = it },
            onError = { error = it; status = "Call problem" })
        engine = rtc
        rtc.attachRenderers(if (video) localRenderer else null, if (video) remoteRenderer else null)
        val iceServers = runCatching { withContext(Dispatchers.IO) { api.loadIceServers() } }
            .onFailure { error = it.message ?: "TURN setup unavailable; trying STUN only." }
            .getOrDefault(emptyList())
        rtc.start(iceServers)
        var lastId = 0L
        try {
            while (isActive && OrraInternetCallState.callId == callId) {
                val signals = withContext(Dispatchers.IO) { api.signalsAfter(callId, lastId) }
                signals.forEach { signal ->
                    lastId = maxOf(lastId, signal.id)
                    rtc.handleSignal(signal)
                }
                val session = withContext(Dispatchers.IO) { api.call(callId) }
                if (session == null || session.status in setOf("ended", "rejected", "missed")) {
                    status = when (session?.status) {
                        "rejected" -> "Call declined"
                        "missed" -> "No answer"
                        else -> "Call ended"
                    }
                    delay(1400)
                    if (OrraInternetCallState.callId == callId) OrraInternetCallState.clear()
                    break
                }
                if (session.status == "ringing") {
                    val created = runCatching { java.time.Instant.parse(session.createdAt) }.getOrNull()
                    if (created != null && java.time.Instant.now().isAfter(created.plusSeconds(60))) {
                        runCatching { withContext(Dispatchers.IO) { api.updateStatus(callId, "missed") } }
                        status = "No answer"
                        delay(1400)
                        if (OrraInternetCallState.callId == callId) OrraInternetCallState.clear()
                        break
                    }
                }
                if (session.status == "accepted" && status.startsWith("Calling")) status = "Connecting…"
                delay(900)
            }
        } catch (e: Exception) {
            error = e.message ?: "Call signaling stopped."
        }
    }

    DisposableEffect(callId) {
        onDispose {
            runCatching { engine?.release() }
            if (!video) { runCatching { localRenderer.release() }; runCatching { remoteRenderer.release() } }
            context.stopService(Intent(context, OrraCallForegroundService::class.java))
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.mode = AudioManager.MODE_NORMAL
            audio.isSpeakerphoneOn = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (video) "ORRA VIDEO CALL" else "ORRA INTERNET CALL",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        Text(OrraInternetCallState.peerName, style = MaterialTheme.typography.headlineMedium)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (video) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.weight(1f).fillMaxSize(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    AndroidView(factory = { localRenderer }, modifier = Modifier.fillMaxSize())
                }
                Surface(Modifier.weight(1f).fillMaxSize(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    AndroidView(factory = { remoteRenderer }, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            Spacer(Modifier.height(30.dp))
            Surface(Modifier.size(132.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {}
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!permissionGranted) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                permissionLauncher.launch(if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                    else arrayOf(Manifest.permission.RECORD_AUDIO))
            }) { Text("Allow microphone and camera") }
        } else {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { muted = !muted; engine?.setMuted(muted) }) {
                    Text(if (muted) "Unmute" else "Mute")
                }
                OutlinedButton(onClick = {
                    speaker = !speaker
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.isSpeakerphoneOn = speaker
                }) { Text(if (speaker) "Speaker on" else "Speaker off") }
                if (video) OutlinedButton(onClick = { cameraOn = !cameraOn; engine?.setCameraEnabled(cameraOn) }) {
                    Text(if (cameraOn) "Camera off" else "Camera on")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val api = OrraCallSignaling(context, userId, token)
                    runCatching { api.sendSignal(callId, "hangup", org.json.JSONObject()) }
                    runCatching { api.updateStatus(callId, "ended") }
                }
                if (OrraInternetCallState.callId == callId) OrraInternetCallState.clear()
            }
        }) { Text("End call") }
    }
}
