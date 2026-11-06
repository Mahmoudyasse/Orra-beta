package com.orra.app

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

fun isOrraDefaultDialer(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }
}

fun requestOrraDialerRole(activity: Activity) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                activity.startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 410
                )
            }
        } else {
            activity.startActivity(
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    activity.packageName
                )
            )
        }
    } catch (_: Exception) {
        Toast.makeText(activity, "Unable to open default phone app settings", Toast.LENGTH_LONG).show()
    }
}

fun placeOrraCall(context: Context, name: String, number: String): Boolean {
    if (number.isBlank()) return false
    if (!isOrraDefaultDialer(context)) {
        Toast.makeText(context, "Set ORRA as the default phone app in Settings first", Toast.LENGTH_LONG).show()
        return false
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Allow Phone permission before placing a call", Toast.LENGTH_LONG).show()
        return false
    }
    return runCatching {
        recordOrraCall(context, name, number)
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        true
    }.getOrElse {
        Toast.makeText(context, "ORRA could not start this call", Toast.LENGTH_LONG).show()
        false
    }
}
