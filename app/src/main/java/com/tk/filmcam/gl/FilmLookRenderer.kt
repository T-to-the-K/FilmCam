package com.tk.filmcam.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES20
import com.tk.filmcam.film.FilmCamera
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws the camera's SurfaceTexture through the film grade and onto the screen.
 *
 * Owns the external GL texture and the [SurfaceTexture] the camera writes into.
 * Both live on the GL thread: `updateTexImage` and `release` must not be split
 * across threads, and `release` must happen while the context is still alive.
 *
 * The buffer arrives in sensor orientation with no rotation applied. Mirror,
 * rotation and centre-crop live in [FilmShader.transform], driven by what
 * CameraX reports for the live [androidx.camera.core.SurfaceRequest].
 */
class FilmLookRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTex = 0
    private var uTransform = 0
    private var uTexSize = 0
    private var uViewSize = 0
    private var uTime = 0
    private var uGain = 0
    private var uSaturation = 0
    private var uContrast = 0
    private var uFade = 0
    private var uWarmth = 0
    private var uShadowTint = 0
    private var uHighlightTint = 0
    private var uSplit = 0
    private var uGrain = 0
    private var uVignette = 0
    private var uHalation = 0
    private var uSoftness = 0

    private var externalTexture = 0
    private var viewWidth = 1
    private var viewHeight = 1
    private var startNanos = 0L

    /** Input side of the pipeline. Read on the GL thread. */
    @Volatile var film: FilmCamera = FilmCamera.DEFAULT

    /** Output side of the pipeline. Read on the GL thread. */
    @Volatile var lens: LensInfo = LensInfo()

    /** Called on the GL thread once a SurfaceTexture of the requested size exists. */
    @Volatile var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    /**
     * Called on the GL thread when GL setup fails, instead of throwing.
     *
     * This was `error(...)`, which is an uncaught exception on the GL thread and
     * takes the process down with it. A phone that cannot compile the grade
     * shader should show an ungraded viewfinder and a line of text, not refuse
     * to open — v0.5.1 refused to open.
     */
    @Volatile var onGlError: ((String) -> Unit)? = null

    @Volatile private var pendingWidth = 0
    @Volatile private var pendingHeight = 0

    /** Size of the live camera SurfaceTexture, or null before the first frame. */
    @Volatile var cameraTexture: SurfaceTexture? = null
        private set

    @Volatile var cameraWidth = 0
        private set

    @Volatile var cameraHeight = 0
        private set

    private var textureListener: ((SurfaceTexture) -> Unit)? = null

    private var framesInWindow = 0
    private var windowStartNanos = 0L
    private var fatalReported = false
    private var lastRect: FloatArray? = null

    /**
     * Frames drawn per second, sampled on the GL thread over one-second windows.
     *
     * Read from the UI thread. It is here because the viewfinder's frame rate is
     * the one number that cannot be checked off-device: without a device
     * attached, "the preview feels slow" is the only symptom available, and the
     * difference between a missing frame listener and a fill-rate problem is
     * invisible in a screenshot.
     */
    @Volatile var fps: Int = 0
        private set

    data class LensInfo(
        val sensorOrientation: Int = 90,
        val displayRotation: Int = 0,
        val mirror: Boolean = false
    )

    /**
     * Orientation as CameraX itself reports it, from
     * [androidx.camera.core.SurfaceRequest.TransformationInfo].
     *
     * Null until the first `SurfaceRequest` delivers one, which is when
     * [lens] is still doing the work. Preferring this over [lens] is the whole
     * point: CameraX already knows how this particular sensor sits in the
     * device and how the lens facing affects the result, and re-deriving it
     * from `SENSOR_ORIENTATION` is what got the viewfinder mirrored.
     */
    data class Orientation(val rotationDegrees: Int, val mirror: Boolean)

    /** Set on the GL thread; read there too, so it needs no volatile handoff. */
    @Volatile var orientation: Orientation? = null

    /**
     * Width over height of the frame, in the orientation the capture is saved
     * in — the sensor's, so 4:3 on this pipeline.
     *
     * Fixed rather than chosen: the use cases are bound 4:3 and
     * [FilmCameraController.ASPECT_STRATEGY] documents why nothing else is
     * honestly available. The frame is then *drawn* as a centred inset instead of
     * being cropped to a requested shape, so the viewfinder shows the same field
     * of view the saved photo contains. Rotating the frame from sensor
     * orientation to screen happens once, in [frameAspect].
     */
    private val contentAspect = 4f / 3f

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        try {
            program = buildProgramWithPrecisionFallback()
            createExternalTexture()
        } catch (e: Throwable) {
            // GLES20 raises no exceptions on its own, so this is our own guard:
            // a failed compile or link has to become a message the UI can show.
            reportFatal("GL setup failed: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        startNanos = System.nanoTime()
        Log.i(TAG, "surfaceCreated program=$program tex=$externalTexture")
    }

    private fun createExternalTexture() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        externalTexture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexture)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun reportFatal(reason: String) {
        Log.e(TAG, "FATAL $reason")
        if (fatalReported) return
        fatalReported = true
        program = 0
        onGlError?.invoke(reason)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = maxOf(1, width)
        viewHeight = maxOf(1, height)
        // The viewport has to follow every surface size, including the ones
        // FilmGlPreview.applyRenderScale imposes after the view is laid out.
        //
        // EGL establishes the viewport from the surface dimensions when the
        // context is first made current, and nothing updates it afterwards --
        // GLSurfaceView does not do it either. The first surface here is the
        // view's own 1080x2400; applyRenderScale then shrinks the buffer to
        // 576x1280, leaving the viewport describing a surface four times the
        // buffer's area. NDC then maps into that stale space and the frame is
        // clipped by the real buffer: it kept the right size, sat off-centre,
        // and ran off the top and right edges. Nothing in the geometry code was
        // wrong, which is why frameRect tested clean and the screen was not.
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        Log.i(TAG, "surfaceChanged ${width}x$height viewport=${glViewportParams()}")
    }

    /** Ask for a camera buffer of this size. Handled on the next GL frame. */
    fun requestCameraTexture(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (cameraWidth == width && cameraHeight == height && cameraTexture != null) return
        pendingWidth = width
        pendingHeight = height
    }

    override fun onDrawFrame(gl: GL10) {
        try {
            // Re-assert the viewport every frame. A surface resize can land
            // between onSurfaceChanged and the draw that follows it, and one
            // stale viewport costs the whole frame.
            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            maybeCreateSurfaceTexture()
            sampleFps()

            // No SurfaceTexture transform here: updateTexImage copies the buffer
            // in its native sensor orientation, and every rotation, mirror and
            // crop is applied by the shader's uTransform. setTransform is not
            // public API anyway, so this is the only correct route.
            cameraTexture?.updateTexImage()
            draw()
        } catch (e: Throwable) {
            // Last line of defence. Anything escaping onDrawFrame is an
            // uncaught exception on the GL thread, and that ends the process
            // rather than the viewfinder.
            reportFatal("Draw failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun sampleFps() {
        val now = System.nanoTime()
        if (windowStartNanos == 0L) {
            windowStartNanos = now
        }
        framesInWindow++
        val elapsed = now - windowStartNanos
        if (elapsed < 1_000_000_000L) return
        fps = (framesInWindow * 1_000_000_000L / elapsed).toInt()
        framesInWindow = 0
        windowStartNanos = now
    }

    /**
     * Swap in a SurfaceTexture sized for the new camera stream. The old one is
     * handed to [textureListener] rather than released here: CameraX may still
     * be writing into it until its completion callback runs.
     */
    private fun maybeCreateSurfaceTexture() {
        val w = pendingWidth
        val h = pendingHeight
        if (w <= 0 || h <= 0) return
        if (cameraWidth == w && cameraHeight == h && cameraTexture != null) return
        pendingWidth = 0
        pendingHeight = 0
        Log.i(TAG, "creating SurfaceTexture ${w}x$h")

        val previous = cameraTexture
        val created = SurfaceTexture(externalTexture)
        created.setDefaultBufferSize(w, h)
        cameraTexture = created
        cameraWidth = w
        cameraHeight = h

        if (previous != null) textureListener?.invoke(previous)
        onSurfaceTextureReady?.invoke(created)
    }

    /** The previous SurfaceTexture is safe to drop once CameraX lets go of it. */
    fun setRetiredTextureListener(listener: ((SurfaceTexture) -> Unit)?) {
        textureListener = listener
    }

    private fun currentTransform(): FloatArray {
        val w = maxOf(1, cameraWidth)
        val h = maxOf(1, cameraHeight)
        // CameraX's answer once it has one, and the sensor-derived fallback only
        // for the frames drawn before the first SurfaceRequest reports.
        val o = orientation
        val degrees = o?.rotationDegrees
            ?: FilmShader.rotationDegrees(lens.sensorOrientation, lens.displayRotation, lens.mirror)
        val mirror = o?.mirror ?: lens.mirror
        return FilmShader.transform(
            degrees, mirror, w, h, frameAspect(degrees)
        )
    }

    /**
     * [contentAspect] turned from sensor orientation into screen orientation.
     *
     * A 4:3 photo is 4:3 in the file however it is held, so on a portrait screen
     * the frame it draws is 3:4. The swap belongs here, once, because the quad
     * and [uTransform] have to agree on it and two independent swaps is how they
     * would come to disagree.
     */
    private fun frameAspect(degrees: Int): Float {
        // 4:3 in the file however it is held, so on a portrait screen the frame
        // it draws is 3:4. The swap belongs here, once, because the quad and
        // [uTransform] have to agree on it and two independent swaps is how they
        // would come to disagree.
        return if (degrees % 180 != 0) 1f / contentAspect else contentAspect
    }

    /**
     * The frame's NDC rect: a small centred box at the sensor's aspect.
     *
     * The film apps all present the viewfinder as a picture rather than a window
     * — a framed shot sitting in the middle of the screen with the interface
     * around it, instead of the camera bleeding edge to edge. That is what this
     * draws, and it is why the frame has to come from the quad: [uTransform] only
     * changes which part of the buffer is sampled, and every fragment still
     * samples something, so shrinking the sampled region magnifies the frame back
     * out to the full surface. There is no texture-space way to leave a gap.
     *
     * Nothing outside the quad is drawn, so the surface keeps its clear colour and
     * the grade never runs over the surround. That matters, because this shader is
     * the reason the preview size is pinned.
     *
     * Sized on the view's long edge so the box is the same visual size whichever
     * way the phone is held: a fraction of the long edge, capped so the frame
     * cannot grow past the view on the other axis.
     */
    private fun frameRect(frameAspect: Float): FloatArray {
        if (frameAspect <= 0f || viewWidth <= 0 || viewHeight <= 0) {
            return floatArrayOf(-1f, -1f, 1f, 1f)
        }
        val viewAspect = viewWidth.toFloat() / viewHeight

        // NDC half-extents. The frame keeps its aspect, so whichever axis is
        // constrained takes the whole allowance and the other is derived from it.
        val halfWidth: Float
        val halfHeight: Float
        if (frameAspect > viewAspect) {
            // Frame is relatively wider than the view: full width, bars top/bottom.
            halfWidth = FRAME_FILL
            halfHeight = (viewAspect / frameAspect) * FRAME_FILL
        } else {
            // Frame is relatively taller: full height, bars at the sides.
            halfHeight = FRAME_FILL
            halfWidth = (frameAspect / viewAspect) * FRAME_FILL
        }
        // Lift the centre so the frame clears the controls along the bottom edge.
        // Applied to the centre rather than to one edge, so the lift does not
        // change the frame's size or its aspect -- only where it sits. In NDC y
        // grows upward, so the centre moves up and both edges follow.
        val centreY = FRAME_LIFT.coerceAtMost(1f - halfHeight)
        return floatArrayOf(
            -halfWidth,
            centreY - halfHeight,
            halfWidth,
            centreY + halfHeight
        )
    }

    /**
     * Rewrite [QUAD] for [rect], if it has moved.
     *
     * The buffer is static so the draw path can hand the same one to
     * `glVertexAttribPointer` every frame, but a ratio change or a rotation moves
     * the rect, and a stale quad would keep drawing the old frame while the
     * transform cropped to the new one.
     */
    private fun updateQuad(rect: FloatArray) {
        if (lastRect != null && rect.contentEquals(lastRect!!)) return
        val left = rect[0]
        val bottom = rect[1]
        val right = rect[2]
        val top = rect[3]
        // Same vertex order as TEX_COORDS: bottom-left, bottom-right, top-left,
        // top-right, as a triangle strip.
        QUAD.put(0, left).put(1, bottom)
        QUAD.put(2, right).put(3, bottom)
        QUAD.put(4, left).put(5, top)
        QUAD.put(6, right).put(7, top)
        QUAD.position(0)
        lastRect = rect.copyOf()
    }

    private fun draw() {
        GLES20.glUseProgram(program)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // No program means setup already failed and the view is being swapped
        // for an ungraded PreviewView. Clear and stop; every glUniform* below
        // with an unset location is at best a no-op and at worst an error the
        // driver decides to treat fatally.
        if (program == 0 || externalTexture == 0) {
            GLES20.glUseProgram(0)
            return
        }

        val texture = cameraTexture
        if (texture == null) {
            GLES20.glUseProgram(0)
            return
        }

        val look = film
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexture)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glUniformMatrix3fv(uTransform, 1, false, currentTransform(), 0)
        GLES20.glUniform2f(uTexSize, cameraWidth.toFloat(), cameraHeight.toFloat())
        GLES20.glUniform2f(uViewSize, viewWidth.toFloat(), viewHeight.toFloat())
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f)

        GLES20.glUniform3f(uGain, look.rgbGain[0], look.rgbGain[1], look.rgbGain[2])
        GLES20.glUniform1f(uSaturation, look.saturation)
        GLES20.glUniform1f(uContrast, look.contrast)
        GLES20.glUniform1f(uFade, look.fade)
        GLES20.glUniform1f(uWarmth, look.warmth)
        GLES20.glUniform3f(
            uShadowTint,
            look.shadowTint[0], look.shadowTint[1], look.shadowTint[2]
        )
        GLES20.glUniform3f(
            uHighlightTint,
            look.highlightTint[0], look.highlightTint[1], look.highlightTint[2]
        )
        GLES20.glUniform1f(uSplit, look.split)
        GLES20.glUniform1f(uGrain, look.grain)
        GLES20.glUniform1f(uVignette, look.vignette)
        GLES20.glUniform1f(uHalation, look.halation)
        GLES20.glUniform1f(uSoftness, look.softness)

        // Shrink the quad to the frame before pointing at it, so a ratio change
        // takes effect on this frame rather than the next one.
        val orientation = orientation
        val degrees = orientation?.rotationDegrees
            ?: FilmShader.rotationDegrees(
                lens.sensorOrientation, lens.displayRotation, lens.mirror
            )
        val rect = frameRect(frameAspect(degrees))
        updateQuad(rect)

        QUAD.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, QUAD)

        TEX_COORDS.position(0)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, TEX_COORDS)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
    }

    private fun glViewportParams(): String {
        val buf = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, buf, 0)
        return buf.joinToString(",")
    }

    fun onSurfaceDestroyed() {
        cameraTexture?.release()
        cameraTexture = null
        cameraWidth = 0
        cameraHeight = 0
        if (externalTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(externalTexture), 0)
            externalTexture = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    /**
     * Compiles the grade program, trying the fragment precision this driver
     * claims to support first and the other one second.
     *
     * The probe is a real compile rather than a query of
     * `GL_FRAGMENT_PRECISION_HIGH`, because that macro lies on at least one
     * driver: it accepts the probe and then rejects the full shader with the
     * macro name spliced into a precision qualifier. If both attempts fail, both
     * driver logs are reported, since one of them is usually the real cause.
     */
    private fun buildProgramWithPrecisionFallback(): Int {
        val probed = if (fragmentHighPrecision()) "highp" else "mediump"
        val order = listOf(probed, if (probed == "highp") "mediump" else "highp")
        val failures = StringBuilder()
        for (precision in order) {
            try {
                return buildProgram(FilmShader.VERTEX, FilmShader.fragment(precision))
            } catch (e: Throwable) {
                failures.append(precision).append(": ")
                    .append(e.message ?: e.javaClass.simpleName).append('\n')
            }
        }
        error("no usable fragment precision\n$failures")
    }

    private fun fragmentHighPrecision(): Boolean {
        val shader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        if (shader == 0) return false
        return try {
            GLES20.glShaderSource(shader, HIGH_PRECISION_PROBE)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            status[0] != 0
        } finally {
            GLES20.glDeleteShader(shader)
        }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val handle = GLES20.glCreateProgram()
        check(handle != 0) { "Could not create GL program" }
        GLES20.glAttachShader(handle, vertexShader)
        GLES20.glAttachShader(handle, fragmentShader)
        GLES20.glLinkProgram(handle)

        val status = IntArray(1)
        GLES20.glGetProgramiv(handle, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(handle)
            GLES20.glDeleteProgram(handle)
            error("Could not link GL program: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        aPosition = GLES20.glGetAttribLocation(handle, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(handle, "aTexCoord")
        uTex = GLES20.glGetUniformLocation(handle, "uTex")
        uTransform = GLES20.glGetUniformLocation(handle, "uTransform")
        uTexSize = GLES20.glGetUniformLocation(handle, "uTexSize")
        uViewSize = GLES20.glGetUniformLocation(handle, "uViewSize")
        uTime = GLES20.glGetUniformLocation(handle, "uTime")
        uGain = GLES20.glGetUniformLocation(handle, "uGain")
        uSaturation = GLES20.glGetUniformLocation(handle, "uSaturation")
        uContrast = GLES20.glGetUniformLocation(handle, "uContrast")
        uFade = GLES20.glGetUniformLocation(handle, "uFade")
        uWarmth = GLES20.glGetUniformLocation(handle, "uWarmth")
        uShadowTint = GLES20.glGetUniformLocation(handle, "uShadowTint")
        uHighlightTint = GLES20.glGetUniformLocation(handle, "uHighlightTint")
        uSplit = GLES20.glGetUniformLocation(handle, "uSplit")
        uGrain = GLES20.glGetUniformLocation(handle, "uGrain")
        uVignette = GLES20.glGetUniformLocation(handle, "uVignette")
        uHalation = GLES20.glGetUniformLocation(handle, "uHalation")
        uSoftness = GLES20.glGetUniformLocation(handle, "uSoftness")
        return handle
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create GL shader of type $type" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Could not compile GL shader: $log")
        }
        return shader
    }

    private companion object {
        const val TAG = "FilmLookRenderer"

        /**
         * How much of the view's long edge the viewfinder frame occupies, 0..1.
         *
         * This is the whole of the old aspect-ratio feature reduced to one
         * number. The frame is centred and inset by the remainder, so the
         * viewfinder reads as a picture sitting in the interface rather than a
         * window onto the camera.
         *
         * 0.86 leaves a visible border on every side in both orientations while
         * keeping the frame large enough to judge focus and exposure from.
         */
        const val FRAME_FILL = 0.86f

        /**
         * How far to raise the frame above centre, as a fraction of the view.
         *
         * The filter row, zoom pills and shutter cluster are pinned to the bottom
         * of the screen, so a frame centred in the full view sits low and crowds
         * them. Lifting it puts the breathing room at the bottom, where the
         * controls are, at the cost of more at the top -- which is empty.
         *
         * Positive is up, in NDC, so it applies the same way in both orientations
         * without a sign flip. 0.07 is about 90px on a 2400px-tall view: enough to
         * read as separate, small enough that the frame stays optically centred.
         */
        const val FRAME_LIFT = 0.07f

        const val HIGH_PRECISION_PROBE =
            "precision highp float;\nvoid main() { gl_FragColor = vec4(0.0); }\n"

        val QUAD: FloatBuffer = floatBufferOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
        )
        val TEX_COORDS: FloatBuffer = floatBufferOf(
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f
        )

        fun floatBufferOf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }
    }
}