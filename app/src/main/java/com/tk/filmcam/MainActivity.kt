package com.tk.filmcam

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.Surface
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tk.filmcam.camera.FilmCameraController
import com.tk.filmcam.film.FilmCamera
import com.tk.filmcam.gl.FilmGlPreview
import com.tk.filmcam.ui.FilmPicker
import com.tk.filmcam.ui.FlipButton
import com.tk.filmcam.ui.IconPill
import com.tk.filmcam.ui.ShutterButton
import com.tk.filmcam.ui.Spacer8
import com.tk.filmcam.ui.StatusBar
import com.tk.filmcam.ui.ZoomReadout
import com.tk.filmcam.ui.theme.FilmCamTheme
import kotlinx.coroutines.delay

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
    var glFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var canZoomOut by remember { mutableStateOf(false) }
    var canZoomIn by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(0) }

    val controller = remember {
        FilmCameraController(context, lifecycleOwner)
    }

    val previewHolder = remember { mutableStateOf<FilmGlPreview?>(null) }

    // The look has to reach the GL surface as well as the saved photo, so the
    // viewfinder shows the filter while it is selected.
    LaunchedEffect(selectedFilm) {
        previewHolder.value?.film = selectedFilm
    }

    // Live frame rate, sampled off the GL thread. Cheap enough to poll twice a
    // second and the only way to tell a stalled preview from a slow one.
    LaunchedEffect(previewHolder.value) {
        val view = previewHolder.value ?: return@LaunchedEffect
        while (true) {
            fps = view.fps
            delay(500)
        }
    }

    DisposableEffect(controller) {
        controller.setOnZoomChanged { z ->
            zoom = z
            canZoomOut = z > controller.minZoomRatio + 0.01f
            canZoomIn = z < controller.maxZoomRatio - 0.01f
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

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        if (!permissionGranted) onRequestPermission()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionGranted && !glFailed) {
            key(previewKey) {
                AndroidView(
                    factory = { ctx ->
                        FilmGlPreview(ctx) { reason ->
                            glFailed = true
                            status = "Preview fallback: $reason"
                        }.also { preview ->
                            previewHolder.value = preview
                            preview.film = selectedFilm
                            controller.displayRotationProvider = { preview.display?.rotation ?: Surface.ROTATION_0 }
                            controller.onLensChanged = { info -> preview.lens = info }
                            controller.attachGestures(preview)
                            controller.bindPreview(preview) { error ->
                                if (error.isNotEmpty()) status = error
                            }
                        }
                    },
                    onRelease = { preview ->
                        controller.onLensChanged = null
                        controller.displayRotationProvider = null
                        preview.release()
                        previewHolder.value = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (permissionGranted) {
            // GL preview unavailable: keep the camera working, just ungraded
            key(previewKey) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            controller.displayRotationProvider = { display?.rotation ?: Surface.ROTATION_0 }
                            controller.attachGestures(this)
                            controller.bindPreview(surfaceProvider) { error ->
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

        if (landscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .width(196.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (status.isNotEmpty()) StatusBar(message = status, modifier = Modifier.fillMaxWidth())

                    // Top bar of secondary controls, same order as portrait.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconPill(
                            icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            desc = flashDesc,
                            active = flashOn,
                            onClick = { flashOn = controller.toggleFlash() }
                        )
                        Spacer8()
                        IconPill(
                            icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            desc = torchDesc,
                            active = torchOn,
                            onClick = { torchOn = controller.toggleTorch() }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    FilmPicker(
                        selected = selectedFilm,
                        onSelect = { selectedFilm = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconPill(
                            icon = Icons.Filled.Remove,
                            desc = zoomOutDesc,
                            enabled = canZoomOut,
                            onClick = { controller.stepZoom(-1f) }
                        )
                        Spacer8()
                        ZoomReadout(zoom = zoom)
                        Spacer8()
                        IconPill(
                            icon = Icons.Filled.Add,
                            desc = zoomInDesc,
                            enabled = canZoomIn,
                            onClick = { controller.stepZoom(1f) }
                        )
                    }

                    // Shutter and flip side by side, flip on the outside.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                        Spacer(Modifier.width(12.dp))
                        FlipButton(
                            onClick = {
                                controller.flip { }
                                previewKey++
                                status = ""
                            }
                        )
                    }

                    FpsReadout(fps)
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // No Arrangement.SpaceBetween here. The status line is conditional, so
            // SpaceBetween had either two children (status at the top, cluster at
            // the bottom) or one (cluster anchored to the top, 1800px of bare
            // preview below the shutter). The weight spacer pins the cluster to
            // the bottom whether or not there is a message to show.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                if (status.isNotEmpty()) {
                    StatusBar(message = status)
                }

                // Secondary controls go in a top bar, as on every stock camera
                // app — flash and torch are not shutter-adjacent.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FpsReadout(fps)
                    Spacer(Modifier.weight(1f))
                    IconPill(
                        icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        desc = flashDesc,
                        active = flashOn,
                        onClick = { flashOn = controller.toggleFlash() }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconPill(
                        icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        desc = torchDesc,
                        active = torchOn,
                        onClick = { torchOn = controller.toggleTorch() }
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(modifier = Modifier.fillMaxWidth()) {
                    FilmPicker(
                        selected = selectedFilm,
                        onSelect = { selectedFilm = it }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconPill(
                            icon = Icons.Filled.Remove,
                            desc = zoomOutDesc,
                            enabled = canZoomOut,
                            onClick = { controller.stepZoom(-1f) }
                        )
                        Spacer8()
                        ZoomReadout(zoom = zoom)
                        Spacer8()
                        IconPill(
                            icon = Icons.Filled.Add,
                            desc = zoomInDesc,
                            enabled = canZoomIn,
                            onClick = { controller.stepZoom(1f) }
                        )
                    }

                    // Three equal-weight slots so the shutter stays dead centre
                    // regardless of how wide the buttons either side of it get.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {}
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
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
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
    }
}

@Composable
private fun Text2(text: String) {
    androidx.compose.material3.Text(text = text, color = Color.White)
}

@Composable
private fun FpsReadout(fps: Int) {
    androidx.compose.material3.Text(
        text = "$fps fps",
        color = Color(0xFF8A8A8A),
        fontSize = 10.sp
    )
}