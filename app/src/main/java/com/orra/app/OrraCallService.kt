package com.orra.app

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object OrraCallState {
    var activeCall by mutableStateOf<Call?>(null)
    var status by mutableStateOf("Connecting")
}

class OrraInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        OrraCallState.activeCall = call
        updateStatus(call.state)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                OrraCallState.status = statusFor(state)
            }
        })
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (OrraCallState.activeCall === call) {
            OrraCallState.activeCall = null
            OrraCallState.status = "Call ended"
        }
    }

    private fun updateStatus(state: Int) {
        OrraCallState.status = statusFor(state)
    }

    private fun statusFor(state: Int): String = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING -> "Calling"
        Call.STATE_CONNECTING -> "Connecting"
        Call.STATE_ACTIVE -> "Connected"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTED -> "Call ended"
        else -> "Call"
    }
}
