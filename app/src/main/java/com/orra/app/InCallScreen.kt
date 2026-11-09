package com.orra.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
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
    BackHandler(enabled = true) { }
    val context = LocalContext.current
    val call = OrraCallState.activeCall
    val number = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val ringing = call?.state == Call.STATE_RINGING
    val holding = call?.state == Call.STATE_HOLDING
    val videoActive = OrraCallState.videoState != VideoProfile.STATE_AUDIO_ONLY
    var showKeypad by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(true) }
    var videoMessage by remember { mutableStateOf("") }
    var callClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val connectedAt = OrraCallState.connectedAtMillis
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

    LaunchedEffect(connectedAt) {
        if (connectedAt > 0L) {
            while (true) {
                callClock = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (videoActive) "ORRA VIDEO CALL" else "ORRA CALL",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(18.dp))
        OrraAvatar(number.ifBlank { "?" }, null, 110.dp)
        Spacer(Modifier.height(12.dp))
        Text(number.ifBlank { "Unknown number" }, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(OrraCallState.status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        if (connectedAt > 0L) {
            val elapsed = ((callClock - connectedAt).coerceAtLeast(0L) / 1000L)
            val hours = elapsed / 3600
            val minutes = (elapsed % 3600) / 60
            val seconds = elapsed % 60
            Text(String.format("%02d:%02d:%02d", hours, minutes, seconds),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }

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
            Text(tr("s_30652b3f"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (videoMessage.isNotBlank()) Text(videoMessage, color = MaterialTheme.colorScheme.error)
        } else if (videoActive) {
            Text(tr("s_bfe3fbff"),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(22.dp))
        if (call != null) {
            val style = remember { OrraPrefs.get(context).getString("call_style", "ios") ?: "ios" }
            if (ringing) {
                val answerState = if (OrraCallState.videoState != VideoProfile.STATE_AUDIO_ONLY)
                    VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
                if (style == "swipe") {
                    OrraSwipeToAnswer(onAnswer = { call.answer(answerState) }, onDecline = { call.disconnect() })
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CallRoundButton(Icons.Default.CallEnd, tr("call_decline"), Color(0xFFFF3B30)) { call.disconnect() }
                        CallRoundButton(Icons.Default.Call, tr("call_accept"), Color(0xFF34C759)) { call.answer(answerState) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallToggleButton(if (OrraCallState.muted) Icons.Default.MicOff else Icons.Default.Mic, tr("call_mute"), OrraCallState.muted) {
                        OrraCallState.updateMute(!OrraCallState.muted)
                    }
                    CallToggleButton(Icons.Default.Dialpad, tr("call_keypad"), showKeypad) { showKeypad = !showKeypad }
                    CallToggleButton(Icons.Default.VolumeUp, tr("call_speaker"), OrraCallState.speaker) {
                        OrraCallState.updateSpeaker(!OrraCallState.speaker)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallToggleButton(if (holding) Icons.Default.PlayArrow else Icons.Default.Pause, tr("call_hold"), holding) {
                        if (holding) call.unhold() else call.hold()
                    }
                    if (!videoActive && videoCall != null) {
                        CallToggleButton(Icons.Default.Videocam, tr("call_video"), false) {
                            runCatching { videoCall.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL)) }
                                .onFailure { videoMessage = "The carrier rejected the video upgrade." }
                        }
                    }
                    if (videoActive) {
                        CallToggleButton(Icons.Default.FlipCameraAndroid, tr("call_flip"), false) {
                            frontCamera = !frontCamera
                            setCallCamera(frontCamera)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                CallRoundButton(Icons.Default.CallEnd, tr("call_end"), Color(0xFFFF3B30)) { call.disconnect() }
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
        } else Text(tr("s_e87e8160"))
    }
}
