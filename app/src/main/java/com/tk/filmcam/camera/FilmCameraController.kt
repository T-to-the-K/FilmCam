package com.tk.filmcam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tk.filmcam.film.FilmCamera
import com.tk.filmcam.pipeline.FilmStillProcessor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Owns the CameraX use cases and the still-save path.
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

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    val isVideo: Boolean get() = false

    fun bindPreview(
        previewView: androidx.camera.view.PreviewView,
        onError: (String) -> Unit
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    capture
                )
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun flip(onError: (String) -> Unit) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        onError("")
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
                            onResult(uri.toString())
                        } catch (e: Exception) {
                            onResult("Save failed: ${e.message}")
                        } finally {
                            tempFile.delete()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult("Capture failed: ${exception.message}")
                }
            }
        )
    }

    private fun gradeFile(file: File, film: FilmCamera): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Could not decode capture")
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

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        val bytes = stream.toByteArray()

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Could not open output stream")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    fun release() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}