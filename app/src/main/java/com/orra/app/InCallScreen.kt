package com.orra.app

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val call = OrraCallState.activeCall
    val number = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val ringing = call?.state == Call.STATE_RINGING
    val holding = call?.state == Call.STATE_HOLDING
    val videoActive = OrraCallState.videoState != VideoProfile.STATE_AUDIO_ONLY
    var showKeypad by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(true) }
    var videoMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val videoCall = call?.videoCall
    val cameraManager = remember { context.getSystemService(CameraManager::class.java) }

    fun setCallCamera(front: Boolean) {
        runCatching {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            }
            if (cameraId != null) videoCall?.setCamera(cameraId)
            else videoMessage = "This device has no matching camera."
        }.onFailure { videoMessage = "Could not switch the call camera." }
    }

    LaunchedEffect(videoActive, call) {
        if (videoActive) setCallCamera(frontCamera)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (videoActive) "ORRA VIDEO CALL" else "ORRA CALL",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        Text(number.ifBlank { "Unknown number" }, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(OrraCallState.status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))

        if (videoActive && call != null && videoCall != null) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                        SurfaceView(ctx).also { view ->
                            view.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    runCatching { videoCall.setDisplaySurface(holder.surface) }
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    runCatching { videoCall.setDisplaySurface(holder.surface) }
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    runCatching { videoCall.setDisplaySurface(null) }
                                }
                            })
                        }
                    })
                }
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                        SurfaceView(ctx).also { view ->
                            view.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    runCatching { videoCall.setPreviewSurface(holder.surface) }
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    runCatching { videoCall.setPreviewSurface(holder.surface) }
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    runCatching { videoCall.setPreviewSurface(null) }
                                }
                            })
                        }
                    })
                }
            }
            Text("Carrier video calling availability depends on both SIMs, network and device support.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (videoMessage.isNotBlank()) Text(videoMessage, color = MaterialTheme.colorScheme.error)
        } else if (videoActive) {
            Text("This carrier or device did not provide an in-call video surface.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(22.dp))
        if (call != null) {
            if (ringing) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        val answerState = if (OrraCallState.videoState != VideoProfile.STATE_AUDIO_ONLY)
                            VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
                        call.answer(answerState)
                    }) { Text(if (OrraCallState.videoState != VideoProfile.STATE_AUDIO_ONLY) "Answer video" else "Answer") }
                    Button(onClick = { call.disconnect() }) { Text("Decline") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        OrraCallState.updateMute(!OrraCallState.muted)
                    }) { Text(if (OrraCallState.muted) "Unmute" else "Mute") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        OrraCallState.updateSpeaker(!OrraCallState.speaker)
                    }) { Text(if (OrraCallState.speaker) "Earpiece" else "Speaker") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        if (holding) call.unhold() else call.hold()
                    }) { Text(if (holding) "Resume" else "Hold") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showKeypad = !showKeypad }) {
                        Text(if (showKeypad) "Hide keypad" else "Keypad")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { call.disconnect() }) { Text("End") }
                }
                if (!videoActive && videoCall != null) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching { videoCall.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL)) }
                            .onFailure { videoMessage = "The carrier rejected the video upgrade." }
                    }) { Text("Upgrade to video") }
                }
                if (videoActive) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        frontCamera = !frontCamera
                        setCallCamera(frontCamera)
                    }) { Text("Switch camera") }
                }
                if (showKeypad) {
                    Spacer(Modifier.height(12.dp))
                    listOf(listOf('1','2','3'), listOf('4','5','6'), listOf('7','8','9'), listOf('*','0','#')).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { digit ->
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                    call.playDtmfTone(digit)
                                    scope.launch { delay(140); runCatching { call.stopDtmfTone() } }
                                }) { Text(digit.toString(), fontSize = 20.sp) }
                            }
                        }
                    }
                }
            }
        } else Text("No active call")
    }
}
