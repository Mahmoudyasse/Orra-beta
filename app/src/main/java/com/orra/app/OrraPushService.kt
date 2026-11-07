package com.orra.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object OrraPush {
    /** Does nothing until google-services.json is added to /app (push stays disabled, the app still works). */
    fun fetchToken(context: Context) {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { onToken(app, it) }
        }.onFailure { Log.w("ORRA", "FCM token unavailable", it) }
    }

    fun onToken(context: Context, token: String) {
        OrraPrefs.get(context).edit().putString("fcm_token", token).apply()
        Thread { OrraSessionGuard.registerPushToken(context.applicationContext) }.start()
    }
}

class OrraPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { OrraPush.onToken(applicationContext, token) }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = if (data["type"] == "call") "call" else "message"
        OrraNotify.show(applicationContext, kind, data["title"] ?: "ORRA", data["body"].orEmpty(), data["conversation_id"] ?: data["call_id"])
    }
}
