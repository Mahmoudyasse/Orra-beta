package com.orra.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SupabaseChatClient.PREFS, android.content.Context.MODE_PRIVATE) }
    var projectUrl by remember { mutableStateOf(prefs.getString("url", "").orEmpty()) }
    var publishableKey by remember { mutableStateOf(prefs.getString("key", "").orEmpty()) }
    var token by remember { mutableStateOf(prefs.getString("token", "").orEmpty()) }
    var userId by remember { mutableStateOf(prefs.getString("user_id", "").orEmpty()) }
    var username by remember { mutableStateOf(prefs.getString("username", "").orEmpty()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var requestedUsername by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<OrraChatUser>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<OrraChatConversation>>(emptyList()) }
    var messages by remember { mutableStateOf<List<OrraChatMessage>>(emptyList()) }
    var selected by remember { mutableStateOf<OrraChatConversation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val configured = projectUrl.startsWith("https://") && publishableKey.isNotBlank()
    val signedIn = token.isNotBlank() && userId.isNotBlank()
    val client = remember(projectUrl, publishableKey, token, userId) {
        if (configured) SupabaseChatClient(context, projectUrl, publishableKey, token.ifBlank { null }, userId.ifBlank { null }) else null
    }

    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = ""
            try { task() } catch (e: Exception) { error = e.message ?: "Something went wrong." }
            finally { busy = false }
        }
    }

    LaunchedEffect(token, selected?.id, client) {
        if (signedIn && client != null) {
            while (true) {
                try {
                    if (selected == null) {
                        conversations = withContext(Dispatchers.IO) { client.loadConversations() }
                    } else {
                        messages = withContext(Dispatchers.IO) { client.loadMessages(selected!!.id) }
                    }
                } catch (e: Exception) {
                    error = e.message ?: "Could not refresh chats."
                }
                delay(3000)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!configured) {
            Text("Connect ORRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Enter your Supabase project URL and publishable key. These connect ORRA to its real messaging server.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            OutlinedTextField(projectUrl, { projectUrl = it }, Modifier.fillMaxWidth(), label = { Text("Project URL") }, singleLine = true)
            OutlinedTextField(publishableKey, { publishableKey = it }, Modifier.fillMaxWidth(), label = { Text("Publishable key") }, singleLine = true)
            Button(onClick = {
                if (projectUrl.startsWith("https://") && publishableKey.isNotBlank()) {
                    SupabaseChatClient.saveConfig(context, projectUrl, publishableKey)
                    error = ""
                } else error = "Enter a valid HTTPS project URL and publishable key."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save server connection") }
        } else if (!signedIn) {
            Text("ORRA Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (isSignUp) "Create your ORRA account" else "Sign in to your ORRA account",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            if (isSignUp) OutlinedTextField(requestedUsername, { requestedUsername = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
            Button(enabled = !busy && email.contains("@") && password.length >= 8 && (!isSignUp || requestedUsername.length >= 3),
                onClick = {
                    val activeClient = SupabaseChatClient(context, projectUrl, publishableKey)
                    runTask {
                        val response = withContext(Dispatchers.IO) {
                            if (isSignUp) activeClient.signUp(email, password, requestedUsername)
                            else activeClient.signIn(email, password)
                        }
                        val access = response.optString("access_token")
                        val user = response.optJSONObject("user")
                        if (access.isBlank() || user == null) {
                            error = "Account created. If email confirmation is enabled, confirm your email, then sign in."
                        } else {
                            val id = user.optString("id")
                            val name = user.optJSONObject("user_metadata")?.optString("username")
                                ?.takeIf { !it.isNullOrBlank() } ?: email.substringBefore("@")
                            SupabaseChatClient.saveSession(context, access, id, name)
                            token = access; userId = id; username = name
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Please wait…" else if (isSignUp) "Create account" else "Sign in")
            }
            TextButton(onClick = { isSignUp = !isSignUp; error = "" }) {
                Text(if (isSignUp) "Already have an account? Sign in" else "New to ORRA? Create account")
            }
            Text("Use a unique password. ORRA never needs your email password from another service.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Signed in as @" + username, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
                IconButton(onClick = {
                    SupabaseChatClient.clearSession(context)
                    token = ""; userId = ""; username = ""; conversations = emptyList()
                }) { Icon(Icons.Default.Logout, contentDescription = "Sign out") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(search, { search = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Find username") })
                IconButton(enabled = search.isNotBlank() && !busy, onClick = {
                    runTask { users = withContext(Dispatchers.IO) { client!!.searchUsers(search) } }
                }) { Icon(Icons.Default.Search, contentDescription = "Search users") }
            }
            if (users.isNotEmpty()) {
                Text("People", fontWeight = FontWeight.SemiBold)
                users.forEach { user ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            runTask {
                                val id = withContext(Dispatchers.IO) { client!!.createConversation(user.id) }
                                selected = OrraChatConversation(id, user.username, "")
                                users = emptyList(); messages = emptyList()
                            }
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("@" + user.username + "     Start chat", Modifier.padding(14.dp))
                    }
                }
            }
            Text("Your conversations", fontWeight = FontWeight.SemiBold)
            if (conversations.isEmpty()) {
                Text("No conversations yet. Search for a username above to start one.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { selected = conversation },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("@" + conversation.username, fontWeight = FontWeight.SemiBold)
                                Text(conversation.lastMessage, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null; messages = emptyList() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to chats")
                }
                Text("@" + selected!!.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderId == userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(message.body, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(messageText, { messageText = it }, Modifier.weight(1f),
                    placeholder = { Text("Write a message") }, maxLines = 4)
                TextButton(enabled = messageText.isNotBlank() && !busy, onClick = {
                    val body = messageText.trim()
                    messageText = ""
                    runTask {
                        withContext(Dispatchers.IO) { client!!.sendMessage(selected!!.id, body) }
                        messages = withContext(Dispatchers.IO) { client!!.loadMessages(selected!!.id) }
                    }
                }) { Text("Send") }
            }
        }
        if (busy) Text("Connecting…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (configured) TextButton(onClick = {
            SupabaseChatClient.clearSession(context)
            prefs.edit().remove("url").remove("key").apply()
            token = ""; userId = ""; username = ""; projectUrl = ""; publishableKey = ""
        }) { Text("Change server settings") }
    }
}
