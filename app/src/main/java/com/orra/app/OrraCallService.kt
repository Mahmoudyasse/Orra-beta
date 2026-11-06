package com.orra.app

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object OrraCallState {
    var activeCall by mutableStateOf<Call?>(null)
    var status by mutableStateOf("Connecting")
    var muted by mutableStateOf(false)
    var speaker by mutableStateOf(false)
    var videoState by mutableStateOf(VideoProfile.STATE_AUDIO_ONLY)
    var service: OrraInCallService? = null

    fun updateMute(value: Boolean) {
        muted = value
        service?.setMuted(value)
    }

    fun updateSpeaker(value: Boolean) {
        speaker = value
        service?.setAudioRoute(if (value) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
    }
}

class OrraInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        OrraCallState.service = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        OrraCallState.activeCall = call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val number = call.details.handle?.schemeSpecificPart.orEmpty()
            if (number.isNotBlank()) recordOrraCall(this, number, number, "Incoming")
        }
        OrraCallState.muted = false
        OrraCallState.speaker = false
        updateStatus(call.state)
        OrraCallState.videoState = call.details.videoState
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                OrraCallState.status = statusFor(state)
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                OrraCallState.videoState = details.videoState
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
            OrraCallState.muted = false
            OrraCallState.speaker = false
            OrraCallState.videoState = VideoProfile.STATE_AUDIO_ONLY
        }
    }

    override fun onDestroy() {
        if (OrraCallState.service === this) OrraCallState.service = null
        super.onDestroy()
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
