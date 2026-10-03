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
 * The [SurfaceTexture.OnFrameAvailableListener] is what drives it, so every new
 * camera buffer must have its listener registered or the viewfinder freezes.
 * Drawing stops when there is nothing to draw instead of spinning the GL thread
 * on an animated grain shader.
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
        renderer.onGlError = { reason ->
            // GL thread. The fallback swaps this view out for a PreviewView, and
            // Compose state may only be written from the main thread.
            mainHandler.post { onFatal(reason) }
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

    /** Measured viewfinder frame rate. See [FilmLookRenderer.fps]. */
    val fps: Int get() = renderer.fps

    /**
     * Cap the surface the GL thread actually renders into.
     *
     * The grade is a per-pixel fragment shader over an external texture, so the
     * cost scales with the surface area. A 1080x2400 view is 2.6M shaded pixels
     * a frame, which a mid-range phone cannot hold at 30 fps with any multi-tap
     * pass. Rendering at 720p and letting SurfaceFlinger scale the result up is
     * what the compositor is for; the viewfinder stays sharp because it is
     * scaled, not resampled in the shader.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (released || w <= 0 || h <= 0) return
        // Never resize the holder from inside the layout pass. setFixedSize
        // changes the surface, which re-enters layout on some OEM builds, and
        // doing that synchronously from onSizeChanged is how you get a view
        // that kills the activity the moment the permission is granted.
        mainHandler.post { applyRenderScale(w, h) }
    }

    private fun applyRenderScale(w: Int, h: Int) {
        if (released || w <= 0 || h <= 0) return
        val longEdge = maxOf(w, h)
        runCatching {
            if (longEdge <= MAX_RENDER_EDGE) {
                holder.setSizeFromLayout()
            } else {
                val scale = MAX_RENDER_EDGE.toFloat() / longEdge
                holder.setFixedSize(
                    maxOf(1, (w * scale).toInt()),
                    maxOf(1, (h * scale).toInt())
                )
            }
        }
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
        // Every texture the renderer creates comes through here, including the
        // replacements it swaps in on a resolution change. Register before the
        // early returns below, or a swapped-in texture never gets redraws and
        // the viewfinder stalls on a stale (or empty) buffer.
        texture.setOnFrameAvailableListener(this, mainHandler)

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
        runCatching { holder.setSizeFromLayout() }
    }

    private companion object {
        /** Longest edge of the surface we actually shade, in pixels. */
        const val MAX_RENDER_EDGE = 1280
    }
}