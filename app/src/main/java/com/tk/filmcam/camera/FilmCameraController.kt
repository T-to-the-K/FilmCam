package com.tk.filmcam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tk.filmcam.film.FilmCamera
import com.tk.filmcam.pipeline.FilmStillProcessor
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Owns the CameraX use cases, the zoom/flash controls, and the still-save path.
 *
 * Capture writes a plain JPEG to a cache file, the film look is applied on the
 * CPU in [FilmStillProcessor], and the graded result is published to
 * MediaStore. The raw capture is deleted after grading — nothing is uploaded,
 * and the app holds no INTERNET permission.
 */
class FilmCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val processingExecutor: Executor = Executors.newSingleThreadExecutor()

    private companion object {
        /** Longest edge we will grade at. ~12 MP, plenty for a phone photo. */
        const val MAX_EDGE = 3500
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null

    private var orientationListener: OrientationEventListener? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var displayManager: DisplayManager? = null
    private var scaleDetector: ScaleGestureDetector? = null

    private var zoomCallback: ((Float) -> Unit)? = null
    private var baseZoom = 1f

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var zoomRatio: Float = 1f
        private set

    var minZoomRatio: Float = 1f
        private set

    var maxZoomRatio: Float = 1f
        private set

    var torchEnabled: Boolean = false
        private set

    var flashEnabled: Boolean = false
        private set

    private var targetRotation: Int = Surface.ROTATION_0

    fun setOnZoomChanged(callback: (Float) -> Unit) {
        zoomCallback = callback
    }

    fun bindPreview(
        view: PreviewView,
        onError: (String) -> Unit
    ) {
        previewView = view
        attachGestures(view)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bind(onError)
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(onError: (String) -> Unit) {
        val provider = cameraProvider ?: return
        val rotation = currentRotation()

        // NOTE: deliberately no setTargetRotation on Preview. PreviewView
        // tracks the display rotation itself and only stands down when a target
        // rotation is set explicitly — setting it here handed the job to our
        // OrientationEventListener, which does not fire on a 180-degree flip.
        val newPreview = Preview.Builder().build().apply {
            previewView?.let { surfaceProvider = it.surfaceProvider }
        }

        val newCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .setFlashMode(
                if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            )
            .build()

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        if (!provider.hasCamera(selector)) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            onError("No camera on this side")
        }

        val activeSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        provider.unbindAll()
        val bound = provider.bindToLifecycle(lifecycleOwner, activeSelector, newPreview, newCapture)

        preview = newPreview
        imageCapture = newCapture
        camera = bound
        zoomRatio = bound.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        minZoomRatio = bound.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        maxZoomRatio = bound.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

        bound.cameraInfo.zoomState.observe(lifecycleOwner) { state: ZoomState ->
            zoomRatio = state.zoomRatio
            zoomCallback?.invoke(state.zoomRatio)
        }

        startOrientationListener()
        applyTorch()
    }

    private fun currentRotation(): Int =
        previewView?.display?.rotation ?: Surface.ROTATION_0

    private fun startOrientationListener() {
        orientationListener?.disable()
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (rotation != targetRotation) {
                    targetRotation = rotation
                    // Only ImageCapture: PreviewView owns the preview rotation.
                    imageCapture?.targetRotation = rotation
                }
            }
        }
        orientationListener = listener
        if (listener.canDetectOrientation()) listener.enable()

        // A 180-degree flip does not always recreate the activity or fire the
        // orientation listener in time, so watch the display too.
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}

            override fun onDisplayChanged(displayId: Int) {
                val r = currentRotation()
                if (r != targetRotation) {
                    targetRotation = r
                    imageCapture?.targetRotation = r
                }
            }
        }
        dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        displayManager = dm
        this.displayListener = displayListener
    }

    private fun attachGestures(view: PreviewView) {
        val detector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cam = camera ?: return false
                    val state = cam.cameraInfo.zoomState.value ?: return false
                    val next = (baseZoom * detector.scaleFactor)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    baseZoom = next
                    cam.cameraControl.setZoomRatio(next)
                    return true
                }
            }
        )
        detector.isQuickScaleEnabled = true
        view.setOnTouchListener { _, event ->
            val handled = detector.onTouchEvent(event)
            if (!handled) view.performClick()
            handled
        }
        scaleDetector = detector
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        baseZoom = clamped
        cam.cameraControl.setZoomRatio(clamped)
    }

    /** One notch along the zoom scale, in either direction. */
    fun stepZoom(direction: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val span = state.maxZoomRatio - state.minZoomRatio
        val step = (span / 20f).coerceAtLeast(0.1f)
        val next = if (direction > 0) {
            (zoomRatio + step).coerceAtMost(state.maxZoomRatio)
        } else {
            (zoomRatio - step).coerceAtLeast(state.minZoomRatio)
        }
        setZoomRatio(next)
    }

    fun resetZoom() {
        setZoomRatio(minZoomRatio)
    }

    fun toggleTorch(): Boolean {
        torchEnabled = !torchEnabled
        applyTorch()
        return torchEnabled
    }

    private fun applyTorch() {
        val cam = camera ?: return
        cam.cameraControl.enableTorch(torchEnabled)
    }

    fun toggleFlash(): Boolean {
        flashEnabled = !flashEnabled
        imageCapture?.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        return flashEnabled
    }

    fun flip(onError: (String) -> Unit) {
        val next = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        torchEnabled = false
        zoomRatio = 1f
        baseZoom = 1f
        lensFacing = next
        bind(onError)
    }

    /**
     * Capture a photo, grade it with [film], and publish it to the gallery.
     * [onResult] is invoked on the main thread with a short status message.
     */
    fun capture(
        film: FilmCamera,
        onResult: (message: String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onResult("Camera not ready")
            return
        }

        capture.targetRotation = currentRotation()

        val tempFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processingExecutor.execute {
                        try {
                            val graded = gradeFile(tempFile, film)
                            val uri = publish(graded, film)
                            graded.recycle()
                            onResult(uri.toString())
                        } catch (e: Throwable) {
                            onResult("Save failed: ${describe(e)}")
                        } finally {
                            tempFile.delete()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult("Capture failed: ${describe(exception)}")
                }
            }
        )
    }

    /**
     * Exception class plus the top few stack frames. A bare `e.message` is how
     * a malformed ColorMatrix hid behind an opaque arraycopy message for three
     * builds; the class name and the frame that actually threw are what make a
     * bug diagnosable from a phone screenshot.
     */
    private fun describe(e: Throwable): String {
        val head = e.javaClass.simpleName
        val msg = e.message?.takeIf { it.isNotBlank() }
        val frame = e.stackTrace.firstOrNull { it.className.startsWith("com.tk.filmcam") }
            ?: e.stackTrace.firstOrNull()
        val where = frame?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }.orEmpty()
        return listOfNotNull(head, msg, where.takeIf { it.isNotBlank() })
            .joinToString(": ")
    }

    /**
     * Decode the capture, downsampling if it is enormous. A 108 MP frame is
     * ~430 MB as ARGB_8888, and the grade holds several full-size buffers at
     * once, so an uncapped decode is an out-of-memory crash waiting to happen.
     */
    private fun gradeFile(file: File, film: FilmCamera): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("Could not read capture bounds (${file.length()} bytes)")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE &&
            bounds.outHeight / (sample * 2) >= MAX_EDGE
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Could not decode capture (${file.length()} bytes, sample=$sample)")

        return FilmStillProcessor.apply(bitmap, film)
    }

    private fun publish(bitmap: Bitmap, film: FilmCamera): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FILMCAM_${film.id}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/FilmCam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                // compress() reports failure by returning false; it does not throw.
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    error("JPEG encode failed for ${bitmap.width}x${bitmap.height}")
                }
            } ?: error("Could not open output stream")
        } catch (e: Throwable) {
            // do not leave a zero-byte orphan in the user's gallery
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    fun release() {
        orientationListener?.disable()
        orientationListener = null
        displayListener?.let { l ->
            displayManager?.unregisterDisplayListener(l)
        }
        displayListener = null
        displayManager = null
        previewView?.setOnTouchListener(null)
        scaleDetector = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        preview = null
        imageCapture = null
    }
}
