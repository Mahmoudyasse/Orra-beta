package com.orra.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Camera ready") }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var videoRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginOrraVideoRecording(context, videoCapture, { active ->
                videoRecording = active
                isRecordingVideo = true
            }, { message ->
                videoRecording = null
                isRecordingVideo = false
                status = message
            })
        } else cameraError = "Microphone permission is needed to record video with sound."
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        cameraError = if (granted) "" else "Camera access was not granted. Enable Camera in Android app permissions."
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 58.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ORRA Camera", style = MaterialTheme.typography.headlineMedium)
        Text("Photos and videos are captured on this device.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall)
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
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("Allow camera access to start the preview.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            }
        }

        if (cameraError.isNotBlank()) Text(cameraError, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        else Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), enabled = hasPermission && !isRecordingVideo,
                onClick = { frontCamera = !frontCamera; flashEnabled = false; zoom = 1f }) {
                Icon(Icons.Default.SwitchCamera, contentDescription = null)
                Text(" Flip")
            }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = hasPermission && camera?.cameraInfo?.hasFlashUnit() == true && !frontCamera,
                onClick = {
                    flashEnabled = !flashEnabled
                    camera?.cameraControl?.enableTorch(flashEnabled)
                }) {
                Icon(if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = null)
                Text(if (flashEnabled) " Flash on" else " Flash")
            }
            Button(modifier = Modifier.weight(1f), enabled = hasPermission && imageCapture != null && !isRecordingVideo,
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val name = "ORRA_${System.currentTimeMillis()}.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ORRA")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val output = ImageCapture.OutputFileOptions.Builder(
                        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ).build()
                    capture.takePicture(output, ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && result.savedUri != null) {
                                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                                    context.contentResolver.update(result.savedUri!!, done, null, null)
                                }
                                status = "Photo saved to Pictures/ORRA"
                                cameraError = ""
                            }
                            override fun onError(exception: ImageCaptureException) {
                                cameraError = "Could not save photo. Try again."
                            }
                        })
                }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Text(" Snap")
            }
        }

        Button(modifier = Modifier.fillMaxWidth(), enabled = hasPermission && videoCapture != null,
            onClick = {
                if (isRecordingVideo) {
                    videoRecording?.stop()
                    status = "Saving video…"
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    beginOrraVideoRecording(context, videoCapture, { active ->
                        videoRecording = active
                        isRecordingVideo = true
                        status = "Recording video…"
                    }, { message ->
                        videoRecording = null
                        isRecordingVideo = false
                        status = message
                    })
                } else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (isRecordingVideo) "Stop video" else "Record video with sound") }

        if (maxZoom > 1f && hasPermission) {
            Text("Zoom  ${String.format("%.1f", zoom)}×", style = MaterialTheme.typography.labelMedium)
            Slider(value = zoom, onValueChange = {
                zoom = it
                camera?.cameraControl?.setZoomRatio(it)
            }, valueRange = 1f..maxZoom)
        }
        Text("Photos: Pictures/ORRA. Videos: Movies/ORRA. Camera and microphone are used only after permission.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
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
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD)))
                        .build()
                    val video = VideoCapture.withOutput(recorder)
                    provider.unbindAll()
                    val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                    camera = bound
                    imageCapture = capture
                    videoCapture = video
                    maxZoom = bound.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    zoom = 1f
                    bound.cameraControl.enableTorch(false)
                    cameraError = ""
                    status = if (frontCamera) "Front camera active" else "Rear camera active"
                }.onFailure {
                    cameraError = "Camera could not start. Check device camera access and close other camera apps."
                }
            }, executor)
            onDispose {
                videoRecording?.stop()
                runCatching { if (future.isDone) future.get().unbindAll() }
                camera = null
                imageCapture = null
                videoCapture = null
            }
        } else onDispose { videoRecording?.stop(); camera = null; imageCapture = null; videoCapture = null }
    }
}

private fun beginOrraVideoRecording(
    context: Context,
    capture: VideoCapture<Recorder>?,
    onStarted: (Recording) -> Unit,
    onFinished: (String) -> Unit
) {
    val video = capture ?: run { onFinished("Video capture is not ready."); return }
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "ORRA_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ORRA")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(values).build()
    runCatching {
        video.output.prepareRecording(context, output).withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.outputResults.outputUri != Uri.EMPTY) {
                            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                            context.contentResolver.update(event.outputResults.outputUri, done, null, null)
                        }
                        onFinished("Video saved to Movies/ORRA")
                    } else onFinished("Video recording failed. Check camera and microphone access.")
                }
            }.also(onStarted)
    }.onFailure { onFinished("Could not start video recording. Check microphone permission and device support.") }
}
