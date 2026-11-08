package com.orra.app

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Applies "block screenshots" and the optional app lock (device PIN / pattern / fingerprint). */
@Composable
fun OrraSecurityLayer() {
    val context = LocalContext.current
    val activity = context as? Activity
    LaunchedEffect(OrraSecurity.secureScreen.value) {
        if (OrraSecurity.secureScreen.value) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (OrraSecurity.locked.value) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) OrraSecurity.locked.value = false
        }
        fun ask() {
            val intent = OrraSecurity.credentialIntent(context)
            if (intent == null) OrraSecurity.locked.value = false else launcher.launch(intent)
        }
        LaunchedEffect(Unit) { ask() }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = {}), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ORRA", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(tr("app_locked"), color = Color.White)
                Button(onClick = { ask() }) { Text(tr("unlock")) }
            }
        }
    }
}
