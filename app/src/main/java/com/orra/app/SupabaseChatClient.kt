package com.orra.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OrraChatUser(val id: String, val username: String)
data class OrraChatConversation(val id: String, val username: String, val lastMessage: String)
data class OrraChatMessage(val id: String, val senderId: String, val body: String, val createdAt: String)

class SupabaseChatClient(
    private val context: Context,
    private val projectUrl: String,
    private val publishableKey: String,
    private val accessToken: String? = null,
    private val currentUserId: String? = null
) {
    companion object {
        const val PREFS = "orra_chat_backend"
        fun saveConfig(context: Context, url: String, key: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("url", url.trim().trimEnd('/'))
                .putString("key", key.trim())
                .apply()
        }
        fun saveSession(context: Context, token: String, userId: String, username: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("token", token).putString("user_id", userId)
                .putString("username", username).apply()
        }
        fun clearSession(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("token").remove("user_id").remove("username").apply()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): String {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated && !accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken)
            } else {
                connection.setRequestProperty("Authorization", "Bearer " + publishableKey)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                if (method == "POST" || method == "PATCH") {
                    connection.setRequestProperty("Prefer", "return=representation")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("message").ifBlank { response } }.getOrDefault(response)
                throw IllegalStateException("Server error $code: " + detail.take(220))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun signUp(email: String, password: String, username: String): JSONObject {
        val body = JSONObject().put("email", email.trim()).put("password", password)
            .put("data", JSONObject().put("username", username.trim().lowercase()))
        return JSONObject(request("/auth/v1/signup", "POST", body, false))
    }

    fun signIn(email: String, password: String): JSONObject {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        return JSONObject(request("/auth/v1/token?grant_type=password", "POST", body, false))
    }

    fun searchUsers(query: String): List<OrraChatUser> {
        val uid = currentUserId ?: return emptyList()
        val path = "/rest/v1/profiles?select=id,username&id=neq." + encode(uid) +
            "&username=ilike." + encode("*" + query.trim() + "*") + "&limit=25"
        val rows = JSONArray(request(path))
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            OrraChatUser(row.optString("id"), row.optString("username"))
        }
    }

    fun createConversation(otherUserId: String): String {
        val response = request(
            "/rest/v1/rpc/create_or_get_conversation",
            "POST",
            JSONObject().put("other_user_id", otherUserId)
        ).trim()
        return response.trim('"').ifBlank { throw IllegalStateException("The server did not return a conversation ID.") }
    }

    fun loadConversations(): List<OrraChatConversation> {
        val uid = currentUserId ?: return emptyList()
        val memberships = JSONArray(request(
            "/rest/v1/conversation_members?select=conversation_id&user_id=eq." + encode(uid)
        ))
        val result = mutableListOf<OrraChatConversation>()
        for (i in 0 until memberships.length()) {
            val conversationId = memberships.optJSONObject(i)?.optString("conversation_id").orEmpty()
            if (conversationId.isBlank()) continue
            val members = JSONArray(request(
                "/rest/v1/conversation_members?select=user_id&conversation_id=eq." +
                    encode(conversationId) + "&user_id=neq." + encode(uid) + "&limit=1"
            ))
            val otherId = members.optJSONObject(0)?.optString("user_id").orEmpty()
            if (otherId.isBlank()) continue
            val profiles = JSONArray(request(
                "/rest/v1/profiles?select=username&id=eq." + encode(otherId) + "&limit=1"
            ))
            val username = profiles.optJSONObject(0)?.optString("username") ?: "User"
            val latest = JSONArray(request(
                "/rest/v1/messages?select=body&conversation_id=eq." + encode(conversationId) +
                    "&order=created_at.desc&limit=1"
            ))
            val last = latest.optJSONObject(0)?.optString("body") ?: "Start a conversation"
            result.add(OrraChatConversation(conversationId, username, last))
        }
        return result
    }

    fun loadMessages(conversationId: String): List<OrraChatMessage> {
        val rows = JSONArray(request(
            "/rest/v1/messages?select=id,sender_id,body,created_at&conversation_id=eq." +
                encode(conversationId) + "&order=created_at.asc&limit=150"
        ))
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            OrraChatMessage(
                row.optString("id"), row.optString("sender_id"),
                row.optString("body"), row.optString("created_at")
            )
        }
    }

    fun sendMessage(conversationId: String, body: String) {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again to send messages.")
        request(
            "/rest/v1/messages",
            "POST",
            JSONObject().put("conversation_id", conversationId)
                .put("sender_id", uid).put("body", body.trim())
        )
    }
}
