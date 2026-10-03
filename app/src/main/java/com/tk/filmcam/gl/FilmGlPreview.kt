package com.tk.filmcam.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.tk.filmcam.film.FilmCamera
import java.util.concurrent.Executor

/**
 * The graded viewfinder.
 *
 * A [GLSurfaceView] whose external GL texture is fed straight from CameraX, so
 * the selected film look is applied to the live feed rather than only to the
 * saved photo. [FilmGlPreview] is a `SurfaceProvider`: the camera writes into a
 * [SurfaceTexture] we own and the renderer grades every frame.
 *
 * Rendering is on-demand (`RENDERMODE_WHEN_DIRTY`): a frame is drawn when the
 * camera delivers one, when the look changes, or when the rotation changes.
 * A continuously animating grain shader would otherwise burn battery for no
 * visible benefit between shots.
 *
 * @param onFatal called with a reason if GL setup fails, so the caller can fall
 *        back to an ungraded preview instead of showing a black rectangle.
 */
class FilmGlPreview(
    context: Context,
    private val onFatal: (String) -> Unit = {}
) : GLSurfaceView(context), SurfaceTexture.OnFrameAvailableListener, Preview.SurfaceProvider {

    private val renderer = FilmLookRenderer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { mainHandler.post(it) }
    private val retired = mutableListOf<SurfaceTexture>()

    private var pendingRequest: SurfaceRequest? = null
    private var currentSurface: Surface? = null
    private var currentTexture: SurfaceTexture? = null
    private var released = false

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY

        renderer.onSurfaceTextureReady = { texture ->
            mainHandler.post { onTextureAvailable(texture) }
        }
        renderer.setRetiredTextureListener { texture ->
            // GL thread: the texture it just swapped out. CameraX may still be
            // writing into it, so hold it until its completion callback fires.
            mainHandler.post { retired.add(texture) }
        }
    }

    /** The look to draw. Changing it redraws one frame. */
    var film: FilmCamera = FilmCamera.DEFAULT
        set(value) {
            field = value
            renderer.film = value
            requestRender()
        }

    /** Rotation and mirroring of the incoming camera buffer. */
    var lens: FilmLookRenderer.LensInfo = FilmLookRenderer.LensInfo()
        set(value) {
            field = value
            renderer.lens = value
            requestRender()
        }

    override fun onFrameAvailable(st: SurfaceTexture) {
        requestRender()
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        pendingRequest = request
        renderer.requestCameraTexture(request.resolution.width, request.resolution.height)
        // the renderer builds the SurfaceTexture on the next GL frame and calls
        // back through onSurfaceTextureReady
        requestRender()
    }

    private fun onTextureAvailable(texture: SurfaceTexture) {
        val request = pendingRequest ?: return
        if (request.resolution.width != renderer.cameraWidth ||
            request.resolution.height != renderer.cameraHeight
        ) {
            return
        }
        pendingRequest = null

        val surface = Surface(texture)
        val retiring = currentSurface
        currentSurface = surface
        currentTexture = texture
        request.provideSurface(surface, mainExecutor) { result ->
            // CameraX is finished with whatever it was holding.
            retiring?.release()
            if (result.getResultCode() == SurfaceRequest.Result.RESULT_REQUEST_CANCELLED) {
                surface.release()
                if (currentSurface === surface) {
                    currentSurface = null
                    currentTexture = null
                }
            }
            drainRetired()
        }
    }

    /**
     * The renderer creates a new SurfaceTexture before the old one is safe to
     * drop, so a completion callback can hand us one that is already finished
     * with. Anything still pending stays until the next callback.
     */
    private fun drainRetired() {
        val iterator = retired.iterator()
        while (iterator.hasNext()) {
            iterator.next().release()
            iterator.remove()
        }
    }

    fun release() {
        released = true
        pendingRequest?.willNotProvideSurface()
        pendingRequest = null
        currentSurface?.release()
        currentSurface = null
        currentTexture = null
        drainRetired()
        queueEvent { renderer.onSurfaceDestroyed() }
    }
}