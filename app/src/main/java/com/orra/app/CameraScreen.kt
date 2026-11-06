package com.orra.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var frontCamera by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf("") }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var requestPermissionAfterRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        cameraError = if (granted) "" else "Camera access was not granted. Enable Camera in Android app permissions to use the preview."
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 58.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("ORRA Video", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Private camera preview. The camera runs only while this screen is open and permission is granted.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall
        )

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (hasPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also {
                            it.scaleType = PreviewView.ScaleType.FILL_CENTER
                            it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView = it
                        }
                    },
                    update = { previewView = it }
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission is required to show your preview.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
            }
        }

        if (cameraError.isNotBlank()) {
            Text(cameraError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = hasPermission,
                onClick = { frontCamera = !frontCamera }
            ) { Text(if (frontCamera) "Switch to rear" else "Switch to front") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { if (hasPermission) cameraError = "" else permissionLauncher.launch(Manifest.permission.CAMERA) }
            ) { Text(if (hasPermission) "Camera ready" else "Enable camera") }
        }
        Text(
            "This is the local camera preview only. Live video calls between ORRA users require a signaling server and WebRTC setup; they are not enabled yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
    }

    DisposableEffect(hasPermission, frontCamera, previewView, lifecycleOwner) {
        if (hasPermission && previewView != null) {
            val future = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView!!.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    cameraError = ""
                }.onFailure {
                    cameraError = "Camera could not start on this device. Check that another app is not using it."
                }
            }, executor)
            onDispose {
                runCatching { if (future.isDone) future.get().unbindAll() }
            }
        } else onDispose { }
    }
}
