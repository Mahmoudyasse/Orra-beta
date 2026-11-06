package com.orra.app

import android.Manifest
import android.content.ContentProviderOperation
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    var hasReadPermission by remember { mutableStateOf(hasContactPermission(context, Manifest.permission.READ_CONTACTS)) }
    var hasWritePermission by remember { mutableStateOf(hasContactPermission(context, Manifest.permission.WRITE_CONTACTS)) }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<PhoneContact?>(null) }
    var editorName by remember { mutableStateOf("") }
    var editorNumber by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var deletingContact by remember { mutableStateOf<PhoneContact?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val startCall = rememberOrraCallAction()
    var favoriteNumbers by remember {
        mutableStateOf(context.getSharedPreferences("orra_contacts", Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }

    fun openNewContact() {
        editingContact = null
        editorName = ""
        editorNumber = ""
        editorError = null
        showEditor = true
    }
    fun openEditContact(contact: PhoneContact) {
        editingContact = contact
        editorName = contact.name
        editorNumber = contact.number
        editorError = null
        showEditor = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasReadPermission = hasContactPermission(context, Manifest.permission.READ_CONTACTS)
        hasWritePermission = hasContactPermission(context, Manifest.permission.WRITE_CONTACTS)
        permissionDenied = !hasReadPermission || !hasWritePermission
        if (hasReadPermission) refreshKey++
        if (deletingContact != null && hasWritePermission) deleting = true
        if (showEditor && !hasWritePermission) editorError = "ORRA needs Contacts write permission to save changes."
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasReadPermission = hasContactPermission(context, Manifest.permission.READ_CONTACTS)
                hasWritePermission = hasContactPermission(context, Manifest.permission.WRITE_CONTACTS)
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasReadPermission, refreshKey) {
        if (hasReadPermission) {
            loading = true
            contacts = withContext(Dispatchers.IO) { readPhoneContacts(context) }
            loading = false
        } else contacts = emptyList()
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
                    if (hasReadPermission) "${contacts.size} contacts found" else "Connect your phone contacts",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = {
                if (!hasReadPermission || !hasWritePermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                } else openNewContact()
            }) { Icon(Icons.Default.Add, contentDescription = "Add contact") }
        }

        if (!hasReadPermission) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(34.dp))
                    Text("Allow ORRA to access contacts")
                    Text("Contacts are read from your phone and remain in your device account.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) }) {
                        Text("Allow access")
                    }
                    if (permissionDenied) Text("Enable Contacts permissions from Android App permissions if the prompt no longer appears.")
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showFavorites = false }) { Text(if (!showFavorites) "• All contacts" else "All contacts") }
                TextButton(onClick = { showFavorites = true }) { Text(if (showFavorites) "• Favorites" else "Favorites") }
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search contacts") })
            when {
                loading -> Text("Loading contacts…")
                filteredContacts.isEmpty() -> Text(if (showFavorites) "No favorite contacts yet." else "No matching contacts.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredContacts, key = { "${it.contactId}-${it.number}" }) { contact ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(11.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.name, fontWeight = FontWeight.Medium)
                                Text(contact.number, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                            }
                            IconButton(onClick = {
                                val updated = favoriteNumbers.toMutableSet()
                                if (updated.contains(contact.number)) updated.remove(contact.number) else updated.add(contact.number)
                                favoriteNumbers = updated
                                context.getSharedPreferences("orra_contacts", Context.MODE_PRIVATE).edit()
                                    .putStringSet("favorites", updated.toSet()).apply()
                            }) {
                                Icon(if (favoriteNumbers.contains(contact.number)) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Toggle favorite",
                                    tint = if (favoriteNumbers.contains(contact.number)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { openEditContact(contact) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${contact.name}")
                            }
                            IconButton(onClick = { deletingContact = contact }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${contact.name}")
                            }
                            IconButton(onClick = { startCall(contact.name, contact.number) }) {
                                Icon(Icons.Default.Call, contentDescription = "Call ${contact.name}", tint = CallGreen)
                            }
                        }
                    }
                }
            }
        }
    }

    if (deletingContact != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingContact = null },
            title = { Text("Delete contact?") },
            text = { Text("Delete ${deletingContact?.name} from the phone contacts stored on this device? This cannot be undone.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    val target = deletingContact
                    if (target != null && hasWritePermission) {
                        deleting = true
                    } else if (!hasWritePermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS))
                    }
                }) { Text(if (deleting) "Deleting…" else "Delete") }
            },
            dismissButton = {
                OutlinedButton(enabled = !deleting, onClick = { deletingContact = null }) { Text("Cancel") }
            }
        )
        LaunchedEffect(deleting) {
            if (deleting) {
                val target = deletingContact
                val result = withContext(Dispatchers.IO) {
                    runCatching { if (target != null) deletePhoneContact(context, target) }
                }.exceptionOrNull()
                deleting = false
                deletingContact = null
                if (result == null) {
                    favoriteNumbers = favoriteNumbers - listOfNotNull(target?.number).toSet()
                    context.getSharedPreferences("orra_contacts", Context.MODE_PRIVATE).edit()
                        .putStringSet("favorites", favoriteNumbers).apply()
                    refreshKey++
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { if (!saving) showEditor = false },
            title = { Text(if (editingContact == null) "New contact" else "Edit contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editorName, onValueChange = { editorName = it },
                        label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = editorNumber, onValueChange = { editorNumber = it },
                        label = { Text("Phone number") }, singleLine = true)
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (editorName.isBlank() || editorNumber.isBlank()) {
                        editorError = "Enter both a name and a phone number."
                    } else if (!hasWritePermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS))
                    } else {
                        saving = true
                        editorError = null
                    }
                }) { Text(if (saving) "Saving…" else "Save") }
            },
            dismissButton = { OutlinedButton(enabled = !saving, onClick = { showEditor = false }) { Text("Cancel") } }
        )
        LaunchedEffect(saving) {
            if (saving) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val current = editingContact
                        if (current == null) insertPhoneContact(context, editorName.trim(), editorNumber.trim())
                        else updatePhoneContact(context, current, editorName.trim(), editorNumber.trim())
                    }.exceptionOrNull()
                }
                saving = false
                if (result == null) {
                    showEditor = false
                    refreshKey++
                } else editorError = "Could not save contact. Check Contacts permission and try again."
            }
        }
    }
}

private fun hasContactPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun readPhoneContacts(context: Context): List<PhoneContact> {
    val result = mutableListOf<PhoneContact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val lookupIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
            val key = if (lookupIndex >= 0) cursor.getString(lookupIndex).orEmpty() else ""
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
            if (id >= 0 && key.isNotBlank() && name.isNotBlank() && number.isNotBlank())
                result.add(PhoneContact(id, key, name, number))
        }
    }
    return result.distinctBy { "${it.contactId}:${it.number}" }
}

private fun insertPhoneContact(context: Context, name: String, number: String) {
    val ops = arrayListOf(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
    )
    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
}

private fun updatePhoneContact(context: Context, contact: PhoneContact, name: String, number: String) {
    val rawId = context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
        arrayOf(ContactsContract.RawContacts._ID), "${ContactsContract.RawContacts.CONTACT_ID}=?",
        arrayOf(contact.contactId.toString()), null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    } ?: error("Raw contact not found")
    val phoneDataId = context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data._ID),
        "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
        arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE), null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
    val ops = arrayListOf(
        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build()
    )
    if (phoneDataId != null) {
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data._ID}=?", arrayOf(phoneDataId.toString()))
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number).build())
    } else {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
    }
    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
}

private fun deletePhoneContact(context: Context, contact: PhoneContact) {
    val deleted = context.contentResolver.delete(
        ContactsContract.Contacts.CONTENT_URI,
        "${ContactsContract.Contacts._ID}=?",
        arrayOf(contact.contactId.toString())
    )
    if (deleted <= 0) error("Contact was not deleted")
}
