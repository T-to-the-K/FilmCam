package com.tk.filmcam.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
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
 * The buffer arrives in sensor orientation with no rotation applied. All of
 * mirror, rotation and centre-crop live in [FilmShader.transform], which is
 * verified against a reference implementation across 288 orientation cases.
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

    data class LensInfo(
        val sensorOrientation: Int = 90,
        val displayRotation: Int = 0,
        val mirror: Boolean = false
    )

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        program = buildProgram(FilmShader.VERTEX, FilmShader.FRAGMENT)

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

        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = maxOf(1, width)
        viewHeight = maxOf(1, height)
    }

    /** Ask for a camera buffer of this size. Handled on the next GL frame. */
    fun requestCameraTexture(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (cameraWidth == width && cameraHeight == height && cameraTexture != null) return
        pendingWidth = width
        pendingHeight = height
    }

    override fun onDrawFrame(gl: GL10) {
        maybeCreateSurfaceTexture()

        // No SurfaceTexture transform here: updateTexImage copies the buffer in
        // its native sensor orientation, and every rotation, mirror and crop is
        // applied by the shader's uTransform. SurfaceTexture.setTransform is not
        // public API anyway, so this is the only correct route.
        cameraTexture?.updateTexImage()
        draw()
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
        val l = lens
        return FilmShader.transform(
            l.sensorOrientation,
            l.displayRotation,
            l.mirror,
            maxOf(1, cameraWidth),
            maxOf(1, cameraHeight),
            viewWidth,
            viewHeight
        )
    }

    private fun draw() {
        GLES20.glUseProgram(program)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

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