package com.orra.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.Proxy
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.io.File
import java.io.FileInputStream

data class OrraChatUser(val id: String, val username: String)
data class OrraChatConversation(
    val id: String, val username: String, val lastMessage: String,
    val isGroup: Boolean = false, val memberCount: Int = 2, val peerUserId: String? = null, val peerPhone: String? = null
)
data class OrraChatMessage(
    val id: String, val senderId: String, val body: String, val createdAt: String,
    val type: String = "text", val mediaUrl: String? = null, val editedAt: String? = null,
    val deletedForEveryone: Boolean = false, val senderUsername: String = ""
)
data class OrraUserPresence(val userId: String, val online: Boolean, val dnd: Boolean, val typing: Boolean, val typingConversation: String?, val lastSeen: String)
data class OrraChatProfile(
    val username: String, val displayName: String, val bio: String, val phoneNumber: String,
    val avatarUrl: String?, val coverUrl: String?
)

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
        fun saveProxy(context: Context, host: String, port: Int, type: String, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("proxy_host", host.trim())
                .putInt("proxy_port", port)
                .putString("proxy_type", type)
                .putBoolean("proxy_enabled", enabled)
                .apply()
        }
    }

    private val signedUrlCache = mutableMapOf<String, Pair<String, Long>>()
    private val senderNameCache = mutableMapOf<String, String>()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun openHttp(url: URL): HttpURLConnection {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("proxy_enabled", false)
        val host = prefs.getString("proxy_host", "").orEmpty().trim()
        val port = prefs.getInt("proxy_port", 0)
        if (!enabled || host.isBlank() || port !in 1..65535) return url.openConnection() as HttpURLConnection
        val kind = if (prefs.getString("proxy_type", "http") == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP
        val proxy = Proxy(kind, InetSocketAddress(host, port))
        return url.openConnection(proxy) as HttpURLConnection
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): String {
        val connection = openHttp(URL(projectUrl.trimEnd('/') + path))
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
            val conversationRows = JSONArray(request(
                "/rest/v1/conversations?select=title,is_group&id=eq." + encode(conversationId) + "&limit=1"
            ))
            val conversationRow = conversationRows.optJSONObject(0)
            val isGroup = conversationRow?.optBoolean("is_group") ?: false
            val members = JSONArray(request(
                "/rest/v1/conversation_members?select=user_id&conversation_id=eq." + encode(conversationId)
            ))
            val otherId = (0 until members.length()).mapNotNull { members.optJSONObject(it)?.optString("user_id") }
                .firstOrNull { it != uid }.orEmpty()
            val profiles = if (otherId.isNotBlank()) JSONArray(request(
                "/rest/v1/profiles?select=username,phone_number&id=eq." + encode(otherId) + "&limit=1"
            )) else JSONArray()
            val username = if (isGroup) conversationRow?.optString("title").orEmpty().ifBlank { "Group" }
                else profiles.optJSONObject(0)?.optString("username") ?: "User"
            val latest = JSONArray(request(
                "/rest/v1/messages?select=body,message_type,deleted_for_everyone&conversation_id=eq." + encode(conversationId) +
                    "&order=created_at.desc&limit=1"
            ))
            val latestRow = latest.optJSONObject(0)
            val last = if (latestRow == null) "Start a conversation"
                else if (latestRow.optBoolean("deleted_for_everyone")) "This message was deleted"
                else latestRow.optString("body").ifBlank { "Attachment" }
            result.add(OrraChatConversation(conversationId, username, last, isGroup, members.length(), otherId.takeIf { it.isNotBlank() }, profiles.optJSONObject(0)?.optString("phone_number")?.takeIf { !it.isNullOrBlank() }))
        }
        return result
    }

    fun loadMessages(conversationId: String): List<OrraChatMessage> {
        val rows = JSONArray(request(
            "/rest/v1/messages?select=id,sender_id,body,created_at,message_type,media_url,edited_at,deleted_for_everyone&conversation_id=eq." +
                encode(conversationId) + "&order=created_at.asc&limit=250"
        ))
        val deletions = JSONArray(request(
            "/rest/v1/message_deletions?select=message_id&user_id=eq." + encode(currentUserId.orEmpty())
        ))
        val hidden = (0 until deletions.length()).mapNotNull { deletions.optJSONObject(it)?.optString("message_id") }.toSet()
        val senderIds = (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString("sender_id") }.distinct()
        val senderNames = mutableMapOf<String, String>()
        senderIds.forEach { senderId ->
            val cachedName = synchronized(senderNameCache) { senderNameCache[senderId] }
            val resolvedName = cachedName ?: run {
                val profileRows = JSONArray(request("/rest/v1/profiles?select=username&id=eq." + encode(senderId) + "&limit=1"))
                profileRows.optJSONObject(0)?.optString("username").orEmpty().also { name ->
                    synchronized(senderNameCache) { senderNameCache[senderId] = name }
                }
            }
            senderNames[senderId] = resolvedName
        }
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            if (row.optString("id") in hidden) return@mapNotNull null
            val deleted = row.optBoolean("deleted_for_everyone")
            val sender = row.optString("sender_id")
            OrraChatMessage(row.optString("id"), sender,
                if (deleted) "This message was deleted" else row.optString("body"),
                row.optString("created_at"), row.optString("message_type", "text"),
                row.optString("media_url").takeIf { it.isNotBlank() }?.let { createSignedMediaUrl("orra-chat-media", it) },
                row.optString("edited_at").takeIf { it.isNotBlank() }, deleted, senderNames[sender].orEmpty())
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

    fun createGroup(title: String, memberIds: List<String>): String {
        val response = request("/rest/v1/rpc/create_orra_group", "POST",
            JSONObject().put("group_title", title.trim()).put("member_ids", JSONArray(memberIds)))
        return response.trim().trim('"').ifBlank { throw IllegalStateException("Group creation failed.") }
    }

    fun addGroupMember(conversationId: String, userId: String) {
        request("/rest/v1/rpc/orra_add_group_member", "POST",
            JSONObject().put("target_conversation", conversationId).put("new_member", userId))
    }

    fun leaveGroup(conversationId: String) {
        request("/rest/v1/rpc/orra_leave_group", "POST",
            JSONObject().put("target_conversation", conversationId))
    }

    fun deleteConversationForMe(conversationId: String) {
        request("/rest/v1/rpc/orra_delete_conversation_for_me", "POST",
            JSONObject().put("target_conversation", conversationId))
    }

    fun updateProfile(displayName: String, bio: String, phoneNumber: String, avatarUrl: String?, coverUrl: String?) {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again.")
        val body = JSONObject().put("display_name", displayName.trim())
            .put("bio", bio.trim()).put("phone_number", phoneNumber.trim()).put("updated_at", java.time.Instant.now().toString())
        if (avatarUrl != null) body.put("avatar_url", avatarUrl)
        if (coverUrl != null) body.put("cover_url", coverUrl)
        request("/rest/v1/profiles?id=eq." + encode(uid), "PATCH", body)
    }

    fun loadMyProfile(): OrraChatProfile {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again.")
        val rows = JSONArray(request("/rest/v1/profiles?select=username,display_name,bio,phone_number,avatar_url,cover_url&id=eq." + encode(uid) + "&limit=1"))
        val row = rows.optJSONObject(0) ?: throw IllegalStateException("Profile not found.")
        return OrraChatProfile(row.optString("username"),
            row.optString("display_name").ifBlank { row.optString("username") },
            row.optString("bio"), row.optString("phone_number"), row.optString("avatar_url").takeIf { it.isNotBlank() }?.let { createSignedMediaUrl("orra-profiles", it) },
            row.optString("cover_url").takeIf { it.isNotBlank() }?.let { createSignedMediaUrl("orra-profiles", it) })
    }

    fun blockUser(userId: String) {
        request("/rest/v1/blocked_users", "POST",
            JSONObject().put("blocker_id", currentUserId).put("blocked_id", userId))
    }

    fun unblockUser(userId: String) {
        request("/rest/v1/blocked_users?blocker_id=eq." + encode(currentUserId.orEmpty()) +
            "&blocked_id=eq." + encode(userId), "DELETE")
    }

    fun updatePresence(online: Boolean, dnd: Boolean, typing: Boolean, conversationId: String? = null) {
        val uid = currentUserId ?: return
        val body = JSONObject()
            .put("user_id", uid)
            .put("is_online", online)
            .put("is_dnd", dnd)
            .put("is_typing", typing)
            .put("typing_conversation", conversationId ?: JSONObject.NULL)
            .put("last_seen", java.time.Instant.now().toString())
        val connection = openHttp(URL(projectUrl.trimEnd('/') + "/rest/v1/user_presence?on_conflict=user_id"))
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken.orEmpty())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            connection.outputStream.use { it.write(JSONArray().put(body).toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Presence update failed ($code): " + detail.take(180))
            }
        } finally { connection.disconnect() }
    }

    fun loadPresence(userIds: List<String>): Map<String, OrraUserPresence> {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = JSONArray(request("/rest/v1/user_presence?select=user_id,is_online,is_dnd,is_typing,typing_conversation,last_seen&user_id=in.(" +
            ids.joinToString(",") + ")"))
        return buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("user_id")
                put(id, OrraUserPresence(id, row.optBoolean("is_online"), row.optBoolean("is_dnd"),
                    row.optBoolean("is_typing"), row.optString("typing_conversation").takeIf { it.isNotBlank() }, row.optString("last_seen")))
            }
        }
    }

    fun setConversationMuted(conversationId: String, muted: Boolean) {
        val body = JSONObject().put("conversation_id", conversationId).put("user_id", currentUserId)
            .put("muted_until", if (muted) java.time.Instant.now().plusSeconds(315360000).toString() else JSONObject.NULL)
        val url = URL(projectUrl.trimEnd('/') + "/rest/v1/conversation_settings?on_conflict=conversation_id,user_id")
        val connection = openHttp(url)
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken.orEmpty())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            connection.outputStream.use { it.write(JSONArray().put(body).toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Could not update chat notifications.")
        } finally { connection.disconnect() }
    }

    fun editMessage(messageId: String, newBody: String) {
        request("/rest/v1/rpc/orra_edit_message", "POST",
            JSONObject().put("target_message", messageId).put("new_body", newBody.trim()))
    }

    fun deleteMessageForEveryone(messageId: String) {
        request("/rest/v1/rpc/orra_delete_message_for_everyone", "POST",
            JSONObject().put("target_message", messageId))
    }

    fun loadMessageHistory(messageId: String): List<String> {
        val rows = JSONArray(request("/rest/v1/message_events?select=event_type,created_at&message_id=eq." +
            encode(messageId) + "&order=created_at.desc"))
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val label = if (row.optString("event_type") == "edited") "Message edited" else "Deleted for everyone"
            label + " • " + row.optString("created_at").take(16).replace("T", " ")
        }
    }

    fun deleteMessageForMe(messageId: String) {
        request("/rest/v1/message_deletions", "POST",
            JSONObject().put("message_id", messageId).put("user_id", currentUserId))
    }

    fun uploadMedia(bucket: String, file: File, objectPath: String, mimeType: String): String {
        val safePath = objectPath.split("/").joinToString("/") { encode(it) }
        val connection = openHttp(URL(projectUrl.trimEnd('/') + "/storage/v1/object/" + bucket + "/" + safePath))
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 30000; connection.readTimeout = 60000
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken.orEmpty())
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("x-upsert", "true")
            FileInputStream(file).use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            if (connection.responseCode !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Upload failed: " + detail.take(180))
            }
            return objectPath
        } finally { connection.disconnect() }
    }

    fun createSignedMediaUrl(bucket: String, objectPath: String): String {
        val cacheKey = "$bucket/$objectPath"
        val now = System.currentTimeMillis()
        synchronized(signedUrlCache) {
            signedUrlCache[cacheKey]?.let { cached ->
                if (cached.second > now + 60000L) return cached.first
            }
        }
        val response = JSONObject(request("/storage/v1/object/sign/" + bucket + "/" +
            objectPath.split("/").joinToString("/") { encode(it) }, "POST", JSONObject().put("expiresIn", 3600)))
        val signed = response.optString("signedURL")
        val fullUrl = if (signed.startsWith("http")) signed else projectUrl.trimEnd('/') + signed
        synchronized(signedUrlCache) { signedUrlCache[cacheKey] = fullUrl to (now + 3300000L) }
        return fullUrl
    }

    fun sendAttachment(conversationId: String, caption: String, type: String, mediaPath: String) {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again.")
        request("/rest/v1/messages", "POST", JSONObject().put("conversation_id", conversationId)
            .put("sender_id", uid).put("body", caption.ifBlank { type })
            .put("message_type", type).put("media_url", mediaPath))
    }

    fun sendLocationMessage(conversationId: String, latitude: String, longitude: String) {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again.")
        val body = "Latitude: $latitude\nLongitude: $longitude"
        request("/rest/v1/messages", "POST", JSONObject().put("conversation_id", conversationId)
            .put("sender_id", uid).put("body", body).put("message_type", "location"))
    }

    fun sendContactCard(conversationId: String, name: String, phone: String) {
        val uid = currentUserId ?: throw IllegalStateException("Sign in again.")
        val body = "$name\n$phone"
        request("/rest/v1/messages", "POST", JSONObject().put("conversation_id", conversationId)
            .put("sender_id", uid).put("body", body).put("message_type", "contact"))
    }

}
