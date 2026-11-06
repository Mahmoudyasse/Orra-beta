package com.orra.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OrraInternetCall(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val type: String,
    val status: String,
    val createdAt: String
)

data class OrraCallSignal(
    val id: Long,
    val senderId: String,
    val type: String,
    val payload: JSONObject
)

/**
 * Supabase REST signaling for WebRTC. It carries SDP/ICE metadata only;
 * voice/video media stays on the WebRTC connection.
 */
class OrraCallSignaling(
    private val context: Context,
    private val userId: String,
    private val accessToken: String
) {
    private val prefs = context.getSharedPreferences(SupabaseChatClient.PREFS, Context.MODE_PRIVATE)
    private val baseUrl = prefs.getString("url", SupabaseChatClient.DEFAULT_PROJECT_URL)
        .orEmpty().trimEnd('/')
    private val apiKey = prefs.getString("key", SupabaseChatClient.DEFAULT_PUBLISHABLE_KEY).orEmpty()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        check(baseUrl.startsWith("https://") && apiKey.isNotBlank()) { "ORRA server configuration is missing." }
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "return=representation")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("message").ifBlank { response } }
                    .getOrDefault(response)
                throw IllegalStateException("Call server error $code: ${detail.take(180)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun createCall(peerId: String, type: String): OrraInternetCall {
        require(type == "audio" || type == "video")
        require(peerId.isNotBlank() && peerId != userId)
        val body = JSONObject().put("caller_id", userId).put("callee_id", peerId).put("call_type", type)
        val rows = JSONArray(request("/rest/v1/orra_call_sessions?select=*", "POST", body))
        return rows.optJSONObject(0)?.toCall()
            ?: throw IllegalStateException("The server did not create the call session.")
    }

    fun incomingCalls(): List<OrraInternetCall> {
        val rows = JSONArray(request(
            "/rest/v1/orra_call_sessions?select=*&callee_id=eq.${enc(userId)}&status=eq.ringing&order=created_at.desc&limit=10"
        ))
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.toCall() }
    }

    fun call(callId: String): OrraInternetCall? {
        val rows = JSONArray(request("/rest/v1/orra_call_sessions?select=*&id=eq.${enc(callId)}&limit=1"))
        return rows.optJSONObject(0)?.toCall()
    }

    fun updateStatus(callId: String, status: String) {
        require(status in setOf("accepted", "rejected", "ended", "missed"))
        if (status in setOf("rejected", "ended", "missed")) {
            request("/rest/v1/rpc/orra_finish_call", "POST",
                JSONObject().put("target_call", callId).put("final_status", status))
        } else {
            request("/rest/v1/orra_call_sessions?id=eq.${enc(callId)}", "PATCH",
                JSONObject().put("status", status).put("updated_at", java.time.Instant.now().toString()))
        }
    }

    fun sendSignal(callId: String, type: String, payload: JSONObject) {
        require(type in setOf("offer", "answer", "ice", "hangup"))
        request("/rest/v1/orra_call_signals", "POST",
            JSONObject().put("call_id", callId).put("sender_id", userId)
                .put("signal_type", type).put("payload", payload))
    }

    fun signalsAfter(callId: String, afterId: Long): List<OrraCallSignal> {
        val rows = JSONArray(request(
            "/rest/v1/orra_call_signals?select=id,sender_id,signal_type,payload&call_id=eq.${enc(callId)}" +
                "&sender_id=neq.${enc(userId)}&id=gt.$afterId&order=id.asc&limit=100"
        ))
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            OrraCallSignal(row.optLong("id"), row.optString("sender_id"),
                row.optString("signal_type"), row.optJSONObject("payload") ?: JSONObject())
        }
    }

    private fun JSONObject.toCall() = OrraInternetCall(
        optString("id"), optString("caller_id"), optString("callee_id"),
        optString("call_type"), optString("status"), optString("created_at")
    )
}
