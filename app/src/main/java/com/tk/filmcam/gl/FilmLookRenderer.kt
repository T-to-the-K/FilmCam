package com.tk.filmcam.gl

import android.opengl.GLES20
import com.tk.filmcam.film.FilmCamera
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Film look renderer. Draws the camera texture through a film-grade shader
 * chain: tonality (contrast + fade), colour (saturation, gain, warmth),
 * halation bloom, grain, vignette.
 *
 * Grain and halation are animated from [startTimeNanos] so the preview feels
 * like film; stills use [FilmShader.bakeStatic] for a fixed frame.
 */
class FilmLookRenderer(film: FilmCamera) {
    private val program: Int
    private var aPosition = 0
    private var aTexCoord = 0
    
    private var uTex = 0
    private var uResolution = 0
    private var uGain = 0
    private var uSaturation = 0
    private var uContrast = 0
    private var uFade = 0
    private var uGrain = 0
    private var uVignette = 0
    private var uWarmth = 0
    private var uHalation = 0
    private var uSoftness = 0
    private var uTime = 0

    private var film: FilmCamera = film

    private val fullscreenQuad: FloatBuffer = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    private val quadTexCoords: FloatBuffer = floatBufferOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    init {
        program = buildProgram(VERTEX_SHADER, FilmShader.FRAGMENT)
    }

    fun updateFilm(next: FilmCamera) {
        film = next
    }

    fun render(textureId: Int, width: Int, height: Int, timeSeconds: Float) {
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform2f(uResolution, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uTime, timeSeconds)

        GLES20.glUniform3f(uGain, film.rgbGain[0], film.rgbGain[1], film.rgbGain[2])
        GLES20.glUniform1f(uSaturation, film.saturation)
        GLES20.glUniform1f(uContrast, film.contrast)
        GLES20.glUniform1f(uFade, film.fade)
        GLES20.glUniform1f(uGrain, film.grain)
        GLES20.glUniform1f(uVignette, film.vignette)
        GLES20.glUniform1f(uWarmth, film.warmth)
        GLES20.glUniform1f(uHalation, film.halation)
        GLES20.glUniform1f(uSoftness, film.softness)

        fullscreenQuad.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, fullscreenQuad)

        quadTexCoords.position(0)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
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
        uResolution = GLES20.glGetUniformLocation(handle, "uResolution")
        uGain = GLES20.glGetUniformLocation(handle, "uGain")
        uSaturation = GLES20.glGetUniformLocation(handle, "uSaturation")
        uContrast = GLES20.glGetUniformLocation(handle, "uContrast")
        uFade = GLES20.glGetUniformLocation(handle, "uFade")
        uGrain = GLES20.glGetUniformLocation(handle, "uGrain")
        uVignette = GLES20.glGetUniformLocation(handle, "uVignette")
        uWarmth = GLES20.glGetUniformLocation(handle, "uWarmth")
        uHalation = GLES20.glGetUniformLocation(handle, "uHalation")
        uSoftness = GLES20.glGetUniformLocation(handle, "uSoftness")
        uTime = GLES20.glGetUniformLocation(handle, "uTime")

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
        fun floatBufferOf(vararg values: Float): FloatBuffer {
            val buffer = ByteBuffer
                .allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(values)
            buffer.position(0)
            return buffer
        }

        const val VERTEX_SHADER = """
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
    vTexCoord = aTexCoord;
    gl_Position = aPosition;
}
"""
    }
}