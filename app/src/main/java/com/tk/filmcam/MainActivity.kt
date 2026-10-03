package com.tk.filmcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tk.filmcam.camera.FilmCameraController
import com.tk.filmcam.film.FilmCamera
import com.tk.filmcam.ui.FilmPicker
import com.tk.filmcam.ui.FlipButton
import com.tk.filmcam.ui.IconPill
import com.tk.filmcam.ui.ShutterButton
import com.tk.filmcam.ui.Spacer8
import com.tk.filmcam.ui.StatusBar
import com.tk.filmcam.ui.ZoomReadout
import com.tk.filmcam.ui.theme.FilmCamTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
        }

    private var permissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            FilmCamTheme {
                FilmCamScreen(
                    permissionGranted = permissionGranted,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}

@Composable
private fun FilmCamScreen(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedFilm by remember { mutableStateOf(FilmCamera.DEFAULT) }
    var status by remember { mutableStateOf("") }
    var previewKey by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var canZoomOut by remember { mutableStateOf(false) }
    var canZoomIn by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }

    val controller = remember {
        FilmCameraController(context, lifecycleOwner)
    }

    DisposableEffect(controller) {
        controller.setOnZoomChanged { ratio ->
            zoom = ratio
            canZoomOut = ratio > controller.minZoomRatio + 0.01f
            canZoomIn = ratio < controller.maxZoomRatio - 0.01f
        }
        onDispose { controller.release() }
    }

    val savedMessage = stringResource(R.string.saved_to_gallery)
    val permissionMessage = stringResource(R.string.camera_permission_required)
    val savingMessage = stringResource(R.string.saving)
    val zoomInDesc = stringResource(R.string.cd_zoom_in)
    val zoomOutDesc = stringResource(R.string.cd_zoom_out)
    val flashDesc = stringResource(R.string.cd_flash)
    val torchDesc = stringResource(R.string.cd_torch)

    LaunchedEffect(Unit) {
        if (!permissionGranted) onRequestPermission()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionGranted) {
            key(previewKey) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            controller.bindPreview(this) { error ->
                                if (error.isNotEmpty()) status = error
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                Text2(permissionMessage)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (status.isNotEmpty()) {
                StatusBar(message = status)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                FilmPicker(
                    selected = selectedFilm,
                    onSelect = { selectedFilm = it }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconPill(
                        label = "−",
                        desc = zoomOutDesc,
                        enabled = canZoomOut,
                        onClick = { controller.stepZoom(-1f) }
                    )
                    Spacer8()
                    ZoomReadout(zoom = zoom)
                    Spacer8()
                    IconPill(
                        label = "+",
                        desc = zoomInDesc,
                        enabled = canZoomIn,
                        onClick = { controller.stepZoom(1f) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconPill(
                            label = "⚡",
                            desc = flashDesc,
                            active = flashOn,
                            onClick = { flashOn = controller.toggleFlash() }
                        )
                        Spacer8()
                        IconPill(
                            label = "☀",
                            desc = torchDesc,
                            active = torchOn,
                            onClick = { torchOn = controller.toggleTorch() }
                        )
                    }
                    ShutterButton(
                        enabled = permissionGranted && !saving,
                        onClick = {
                            saving = true
                            status = savingMessage
                            controller.capture(selectedFilm) { result ->
                                saving = false
                                status = if (result.startsWith("http") || result.contains("/")) {
                                    savedMessage
                                } else {
                                    result
                                }
                            }
                        }
                    )
                    FlipButton(
                        onClick = {
                            controller.flip { }
                            previewKey++
                            status = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Text2(text: String) {
    androidx.compose.material3.Text(text = text, color = Color.White)
}