package com.orra.app

import android.telecom.Call
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InCallScreen() {
    val call = OrraCallState.activeCall
    val number = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val ringing = call?.state == Call.STATE_RINGING
    val holding = call?.state == Call.STATE_HOLDING

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ORRA CALL", color = CallGreen, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))
        Text(number.ifBlank { "Unknown number" }, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text(OrraCallState.status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Spacer(Modifier.height(42.dp))
        if (call != null) {
            if (ringing) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) }) {
                        Text("Answer")
                    }
                    Button(onClick = { call.disconnect() }) {
                        Text("Decline")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = {
                        if (holding) call.unhold() else call.hold()
                    }) {
                        Text(if (holding) "Resume" else "Hold")
                    }
                    Button(onClick = { call.disconnect() }) {
                        Text("End call")
                    }
                }
            }
        } else {
            Text("No active call")
        }
    }
}
