package com.orra.app

import android.telecom.Call
import android.telecom.VideoProfile
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun InCallScreen() {
    val call = OrraCallState.activeCall
    val number = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val ringing = call?.state == Call.STATE_RINGING
    val holding = call?.state == Call.STATE_HOLDING
    var showKeypad by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ORRA CALL", color = CallGreen, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))
        Text(number.ifBlank { "Unknown number" }, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text(OrraCallState.status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Spacer(Modifier.height(32.dp))
        if (call != null) {
            if (ringing) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }) { Text("Answer") }
                    Button(onClick = { call.disconnect() }) { Text("Decline") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        OrraCallState.updateMute(!OrraCallState.muted)
                    }) { Text(if (OrraCallState.muted) "Unmute" else "Mute") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        OrraCallState.updateSpeaker(!OrraCallState.speaker)
                    }) { Text(if (OrraCallState.speaker) "Earpiece" else "Speaker") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        if (holding) call.unhold() else call.hold()
                    }) { Text(if (holding) "Resume" else "Hold") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showKeypad = !showKeypad }) {
                        Text(if (showKeypad) "Hide keypad" else "Keypad")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { call.disconnect() }) { Text("End") }
                }
                if (showKeypad) {
                    Spacer(Modifier.height(16.dp))
                    listOf(listOf('1','2','3'), listOf('4','5','6'), listOf('7','8','9'), listOf('*','0','#')).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        } else {
            Text("No active call")
        }
    }
}
