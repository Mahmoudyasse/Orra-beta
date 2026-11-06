package com.orra.app

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PhoneContact(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String
)

@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showFavorites by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingCall
        pendingCall = null
        if (granted && pending != null) placeOrraCall(context, pending.first, pending.second)
    }
    var favoriteNumbers by remember {
        mutableStateOf(
            context.getSharedPreferences("orra_contacts", Context.MODE_PRIVATE)
                .getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (hasPermission) {
            loading = true
            contacts = withContext(Dispatchers.IO) {
                val result = mutableListOf<PhoneContact>()
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val lookupIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                        val lookupKey = if (lookupIndex >= 0) cursor.getString(lookupIndex).orEmpty() else ""
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                        val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                        if (id >= 0 && lookupKey.isNotBlank() && name.isNotBlank() && number.isNotBlank()) {
                            result.add(PhoneContact(id, lookupKey, name, number))
                        }
                    }
                }
                result.distinctBy { it.number }
            }
            loading = false
        } else {
            contacts = emptyList()
        }
    }

    val filteredContacts = contacts.filter {
        (!showFavorites || favoriteNumbers.contains(it.number)) &&
            (it.name.contains(search, ignoreCase = true) || it.number.contains(search))
    }

    Column(
        Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Contacts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (hasPermission) "${contacts.size} contacts found" else "Connect your phone contacts",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        }

        if (!hasPermission) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(34.dp))
                    Text("Allow ORRA to read contacts")
                    Text(
                        "Your phone contacts stay on this device in this version.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Allow access")
                    }
                    if (permissionDenied) {
                        Text("If Android no longer shows the permission prompt, enable Contacts from App permissions in Settings.")
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showFavorites = false }) { Text(if (!showFavorites) "• All contacts" else "All contacts") }
                TextButton(onClick = { showFavorites = true }) { Text(if (showFavorites) "• Favorites" else "Favorites") }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search contacts") }
            )
            when {
                loading -> Text("Loading contacts…")
                contacts.isEmpty() -> Text("No contacts with phone numbers were found.")
                showFavorites && contacts.none { favoriteNumbers.contains(it.number) } ->
                    Text("No favorite contacts yet. Tap the star beside a contact to add one.")
                filteredContacts.isEmpty() ->
                    Text("No matching contacts.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        filteredContacts,
                        key = { it.number }
                    ) { contact ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(11.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.name, fontWeight = FontWeight.Medium)
                                Text(contact.number, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                            }
                            IconButton(onClick = {
                                val updated = favoriteNumbers.toMutableSet()
                                if (updated.contains(contact.number)) updated.remove(contact.number)
                                else updated.add(contact.number)
                                favoriteNumbers = updated
                                context.getSharedPreferences("orra_contacts", Context.MODE_PRIVATE)
                                    .edit().putStringSet("favorites", updated.toSet()).apply()
                            }) {
                                Icon(
                                    if (favoriteNumbers.contains(contact.number)) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (favoriteNumbers.contains(contact.number)) "Remove favorite" else "Add favorite",
                                    tint = if (favoriteNumbers.contains(contact.number)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                val contactUri = ContactsContract.Contacts.getLookupUri(
                                    contact.contactId,
                                    contact.lookupKey
                                )
                                if (contactUri != null) {
                                    context.startActivity(Intent(Intent.ACTION_EDIT, contactUri))
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${contact.name}")
                            }
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    placeOrraCall(context, contact.name, contact.number)
                                } else {
                                    pendingCall = contact.name to contact.number
                                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                }
                            }) {
                                Icon(Icons.Default.Call, contentDescription = "Call ${contact.name}", tint = CallGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}
