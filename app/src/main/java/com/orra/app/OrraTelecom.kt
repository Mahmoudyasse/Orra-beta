package com.orra.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun isOrraDefaultDialer(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName

fun requestOrraDialerRole(activity: Activity) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = activity.getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true && !manager.isRoleHeld(RoleManager.ROLE_DIALER))
                activity.startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 410)
        } else activity.startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName))
    } catch (_: Exception) {
        Toast.makeText(activity, "Unable to open default phone app settings", Toast.LENGTH_LONG).show()
    }
}

private fun placeOrraCallOnAccount(context: Context, name: String, number: String, account: PhoneAccountHandle?, video: Boolean = false) {
    runCatching {
        recordOrraCall(context, name, number)
        val extras = Bundle()
        if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        if (video) extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
        context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), extras)
    }.onFailure {
        Toast.makeText(context, "ORRA could not start this call. Check SIM and phone permissions.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun rememberOrraCallAction(video: Boolean = false): (String, String) -> Unit {
    val context = LocalContext.current
    val darkMode = isSystemInDarkTheme()
    val neon = Color(0xFF8B7DFF)
    val simShape = RoundedCornerShape(16.dp)
    var pending by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var accounts by remember { mutableStateOf<List<PhoneAccountHandle>>(emptyList()) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    fun loadAccounts(request: Triple<String, String, Boolean>) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val available = runCatching { telecom.callCapablePhoneAccounts }.getOrDefault(emptyList())
        if (available.size > 1) {
            accounts = available
            labels = available.mapIndexed { i, handle ->
                val info = runCatching { telecom.getPhoneAccount(handle) }.getOrNull()
                val label = info?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}"
                val address = info?.address?.schemeSpecificPart?.takeIf { it.isNotBlank() }
                if (address != null) "$label • $address" else label
            }
            pending = request
            showPicker = true
        } else placeOrraCallOnAccount(context, request.first, request.second, available.firstOrNull(), request.third)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val callGranted = grants[Manifest.permission.CALL_PHONE] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = grants[Manifest.permission.READ_PHONE_STATE] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val request = pending
        val videoGranted = !video || (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
        if (!callGranted) {
            pending = null
            Toast.makeText(context, "Allow Phone permission to place calls.", Toast.LENGTH_LONG).show()
        } else if (request != null) {
            pending = null
            if (phoneGranted && videoGranted) loadAccounts(request)
            else Toast.makeText(context, if (!videoGranted) "Camera and microphone permissions are required for video calling." else "Phone state permission is required to choose the SIM safely.", Toast.LENGTH_LONG).show()
        }
    }

    val start: (String, String) -> Unit = { name, number ->
        if (number.isNotBlank()) {
            if (!isOrraDefaultDialer(context)) {
                Toast.makeText(context, "Set ORRA as the default phone app in Settings first.", Toast.LENGTH_LONG).show()
            } else {
                val request = Triple(name, number, video)
                val missing = buildList {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                        add(Manifest.permission.CALL_PHONE)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                        add(Manifest.permission.READ_PHONE_STATE)
                    if (video && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                        add(Manifest.permission.CAMERA)
                    if (video && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        add(Manifest.permission.RECORD_AUDIO)
                }
                if (missing.isEmpty()) loadAccounts(request)
                else {
                    pending = request
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false; pending = null },
            title = { Text("Choose SIM") },
            containerColor = if (darkMode) Color(0xFF11121C) else MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    labels.forEachIndexed { index, label ->
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (darkMode) Modifier
                                    .shadow(8.dp, simShape, ambientColor = neon.copy(alpha = 0.16f), spotColor = neon.copy(alpha = 0.12f))
                                    .clip(simShape)
                                    .background(Color(0xFF191A29))
                                    .border(1.dp, neon.copy(alpha = 0.42f), simShape)
                                    .padding(vertical = 5.dp) else Modifier),
                            onClick = {
                                val request = pending
                                val account = accounts.getOrNull(index)
                                showPicker = false
                                pending = null
                                if (request != null) placeOrraCallOnAccount(context, request.first, request.second, account, request.third)
                            }
                        ) { Text(label, color = if (darkMode) Color(0xFFC9C2FF) else MaterialTheme.colorScheme.primary) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false; pending = null }) { Text("Cancel") } }
        )
    }
    return start
}
