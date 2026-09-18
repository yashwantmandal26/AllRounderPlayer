@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player.memc

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * Clean-room real-time motion interpolation renderer.
 *
 * MediaCodec decodes into an external OES texture. Consecutive frames are copied
 * into two RGB textures, then a fragment shader performs a small hierarchical-like
 * local motion search and bidirectional warp for every display refresh. Keeping the
 * implementation in one isolated Surface makes failure/fallback deterministic and
 * leaves ExoPlayer's audio, seeking and demuxing untouched.
 */
class SoftwareMemcView(context: Context) : GLSurfaceView(context) {
    private val memcRenderer = MemcRenderer(
        requestRender = { requestRender() },
        postToView = { action -> post(action) }
    )
    private var attachedPlayer: ExoPlayer? = null
    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            memcRenderer.setVideoSize(videoSize.width, videoSize.height)
        }
    }

    var mode: MemcMode = MemcMode.MEDIUM
        set(value) {
            field = value
            memcRenderer.mode = value
            queueEvent { memcRenderer.reallocateForMode() }
        }

    var onRendererReady: (() -> Unit)? = null
        set(value) {
            field = value
            memcRenderer.onReady = value
        }

    var onRendererError: ((String) -> Unit)? = null
        set(value) {
            field = value
            memcRenderer.onError = value
        }

    init {
        setEGLContextClientVersion(3)
        setPreserveEGLContextOnPause(true)
        setRenderer(memcRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun attachPlayer(player: ExoPlayer) {
        if (attachedPlayer === player) return
        detachPlayer()
        attachedPlayer = player
        Log.i(TAG, "Attaching software MEMC output; surfaceReady=${memcRenderer.outputSurface != null}")
        player.addListener(playerListener)
        player.videoSize.let { memcRenderer.setVideoSize(it.width, it.height) }
        memcRenderer.outputSurface?.let(player::setVideoSurface)
    }

    fun detachPlayer() {
        val player = attachedPlayer ?: return
        Log.i(TAG, "Detaching software MEMC output")
        memcRenderer.outputSurface?.let { player.clearVideoSurface(it) }
        player.removeListener(playerListener)
        attachedPlayer = null
    }

    override fun onDetachedFromWindow() {
        detachPlayer()
        super.onDetachedFromWindow()
    }

    private inner class MemcRenderer(
        private val requestRender: () -> Unit,
        private val postToView: ((() -> Unit)) -> Unit
    ) : Renderer, SurfaceTexture.OnFrameAvailableListener {
        private val framePending = AtomicBoolean(false)
        private val transform = FloatArray(16)
        private var oesTexture = 0
        private var surfaceTexture: SurfaceTexture? = null
        @Volatile var outputSurface: Surface? = null
            private set
        @Volatile var mode: MemcMode = MemcMode.MEDIUM
        @Volatile var onReady: (() -> Unit)? = null
        @Volatile var onError: ((String) -> Unit)? = null
        @Volatile private var sourceWidth = 1920
        @Volatile private var sourceHeight = 1080
        private var targetWidth = 0
        private var targetHeight = 0
        private var viewportWidth = 1
        private var viewportHeight = 1
        private var framebuffer = 0
        private val frameTextures = IntArray(2)
        private var currentIndex = 0
        private var capturedFrames = 0
        private var copyProgram = 0
        private var memcProgram = 0
        private var lastSourceTimestampNs = 0L
        private var sourceIntervalNs = 41_666_667L
        private var frameArrivalNs = 0L
        private val vertices = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
                position(0)
            }

        fun setVideoSize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            sourceWidth = width
            sourceHeight = height
            surfaceTexture?.setDefaultBufferSize(width, height)
            queueEvent { allocateFrameTextures() }
        }

        fun reallocateForMode() {
            allocateFrameTextures()
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            runCatching {
                copyProgram = createProgram(VERTEX_SHADER, COPY_FRAGMENT_SHADER)
                memcProgram = createProgram(VERTEX_SHADER, MEMC_FRAGMENT_SHADER)
                oesTexture = createExternalTexture()
                surfaceTexture = SurfaceTexture(oesTexture).also {
                    it.setDefaultBufferSize(sourceWidth, sourceHeight)
                    it.setOnFrameAvailableListener(this)
                }
                outputSurface = Surface(surfaceTexture)
                val ids = IntArray(1)
                GLES30.glGenFramebuffers(1, ids, 0)
                framebuffer = ids[0]
                postToView {
                    Log.i(TAG, "OpenGL renderer ready; source=${sourceWidth}x$sourceHeight")
                    attachedPlayer?.setVideoSurface(outputSurface)
                    onReady?.invoke()
                }
            }.onFailure { reportError("Software MEMC initialization failed", it) }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = width.coerceAtLeast(1)
            viewportHeight = height.coerceAtLeast(1)
            allocateFrameTextures()
        }

        override fun onDrawFrame(gl: GL10?) {
            runCatching {
                if (framePending.compareAndSet(true, false)) captureDecoderFrame()
                drawInterpolatedFrame()
            }.onFailure { reportError("Software MEMC rendering failed", it) }
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            framePending.set(true)
            requestRender()
        }

        private fun captureDecoderFrame() {
            val texture = surfaceTexture ?: return
            texture.updateTexImage()
            texture.getTransformMatrix(transform)
            val timestampNs = texture.timestamp
            if (lastSourceTimestampNs > 0L && timestampNs > lastSourceTimestampNs) {
                val measured = timestampNs - lastSourceTimestampNs
                if (measured in 8_000_000L..100_000_000L) {
                    sourceIntervalNs = ((sourceIntervalNs * 3L) + measured) / 4L
                }
            }
            lastSourceTimestampNs = timestampNs
            frameArrivalNs = System.nanoTime()

            if (capturedFrames > 0) currentIndex = 1 - currentIndex
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                frameTextures[currentIndex],
                0
            )
            GLES30.glViewport(0, 0, targetWidth, targetHeight)
            GLES30.glUseProgram(copyProgram)
            bindGeometry(copyProgram)
            GLES30.glUniformMatrix4fv(
                GLES30.glGetUniformLocation(copyProgram, "uTransform"), 1, false, transform, 0
            )
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(copyProgram, "uFrame"), 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            capturedFrames = (capturedFrames + 1).coerceAtMost(2)
            if (capturedFrames == 1) {
                Log.i(TAG, "Received first decoder frame; processing=${targetWidth}x$targetHeight")
            }
        }

        private fun drawInterpolatedFrame() {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            if (capturedFrames == 0 || targetWidth == 0 || targetHeight == 0) return

            val previousIndex = if (capturedFrames > 1) 1 - currentIndex else currentIndex
            val elapsed = (System.nanoTime() - frameArrivalNs).coerceAtLeast(0L)
            val phase = (elapsed.toDouble() / sourceIntervalNs.toDouble()).coerceIn(0.0, 1.0).toFloat()
            val radius = when (mode) {
                MemcMode.LOW -> 4f
                MemcMode.MEDIUM -> 8f
                MemcMode.HIGH -> 12f
                MemcMode.OFF -> 0f
            }

            GLES30.glUseProgram(memcProgram)
            bindGeometry(memcProgram)
            GLES30.glUniformMatrix4fv(
                GLES30.glGetUniformLocation(memcProgram, "uTransform"), 1, false, IDENTITY, 0
            )
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTextures[previousIndex])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(memcProgram, "uPrevious"), 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTextures[currentIndex])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(memcProgram, "uCurrent"), 1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(memcProgram, "uPhase"), phase)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(memcProgram, "uRadius"), radius)
            GLES30.glUniform2f(
                GLES30.glGetUniformLocation(memcProgram, "uTexel"),
                1f / targetWidth.toFloat(),
                1f / targetHeight.toFloat()
            )
            val videoAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
            val outputAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
            val scaleX = if (outputAspect > videoAspect) outputAspect / videoAspect else 1f
            val scaleY = if (outputAspect < videoAspect) videoAspect / outputAspect else 1f
            GLES30.glUniform2f(GLES30.glGetUniformLocation(memcProgram, "uUvScale"), scaleX, scaleY)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun allocateFrameTextures() {
            if (viewportWidth <= 1 || viewportHeight <= 1) return
            val maxWidth = when (mode) {
                MemcMode.LOW -> 640
                MemcMode.MEDIUM -> 960
                MemcMode.HIGH -> 1280
                MemcMode.OFF -> 640
            }
            targetWidth = sourceWidth.coerceAtMost(maxWidth).coerceAtLeast(2)
            targetHeight = ((targetWidth.toFloat() * sourceHeight / sourceWidth).roundToInt())
                .coerceAtLeast(2)
                .and(-2)
            if (frameTextures[0] != 0) GLES30.glDeleteTextures(2, frameTextures, 0)
            GLES30.glGenTextures(2, frameTextures, 0)
            frameTextures.forEach { id ->
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, targetWidth, targetHeight,
                    0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
                )
            }
            capturedFrames = 0
        }

        private fun bindGeometry(program: Int) {
            val position = GLES30.glGetAttribLocation(program, "aPosition")
            val texCoord = GLES30.glGetAttribLocation(program, "aTexCoord")
            vertices.position(0)
            GLES30.glEnableVertexAttribArray(position)
            GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 16, vertices)
            vertices.position(2)
            GLES30.glEnableVertexAttribArray(texCoord)
            GLES30.glVertexAttribPointer(texCoord, 2, GLES30.GL_FLOAT, false, 16, vertices)
        }

        private fun reportError(prefix: String, error: Throwable) {
            Log.e(TAG, prefix, error)
            postToView { onError?.invoke("$prefix: ${error.message ?: error.javaClass.simpleName}") }
        }

        private fun createExternalTexture(): Int {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun createProgram(vertex: String, fragment: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES30.glCreateShader(type)
                GLES30.glShaderSource(shader, source)
                GLES30.glCompileShader(shader)
                val status = IntArray(1)
                GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
                check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) }
                return shader
            }
            val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
            val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
            val program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vs)
            GLES30.glAttachShader(program, fs)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(program) }
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return program
        }
    }

    private companion object {
        const val TAG = "SoftwareMEMC"
        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f
        )

        const val VERTEX_SHADER = """#version 300 es
            in vec2 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uTransform;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = (uTransform * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        const val COPY_FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uFrame;
            in vec2 vTexCoord;
            out vec4 outColor;
            void main() { outColor = texture(uFrame, vTexCoord); }
        """

        const val MEMC_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            uniform sampler2D uPrevious;
            uniform sampler2D uCurrent;
            uniform vec2 uTexel;
            uniform vec2 uUvScale;
            uniform float uPhase;
            uniform float uRadius;
            in vec2 vTexCoord;
            out vec4 outColor;

            float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
            float differenceAt(vec2 uv, vec2 motion) {
                vec2 dx = vec2(uTexel.x * 2.0, 0.0);
                vec2 dy = vec2(0.0, uTexel.y * 2.0);
                float d = abs(luma(texture(uPrevious, uv).rgb) - luma(texture(uCurrent, uv + motion).rgb));
                d += abs(luma(texture(uPrevious, uv + dx).rgb) - luma(texture(uCurrent, uv + motion + dx).rgb));
                d += abs(luma(texture(uPrevious, uv - dx).rgb) - luma(texture(uCurrent, uv + motion - dx).rgb));
                d += abs(luma(texture(uPrevious, uv + dy).rgb) - luma(texture(uCurrent, uv + motion + dy).rgb));
                d += abs(luma(texture(uPrevious, uv - dy).rgb) - luma(texture(uCurrent, uv + motion - dy).rgb));
                return d * 0.2;
            }

            void main() {
                vec2 uv = (vTexCoord - 0.5) * uUvScale + 0.5;
                if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
                    outColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                vec2 bestMotion = vec2(0.0);
                float bestError = differenceAt(uv, bestMotion);
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        vec2 candidate = vec2(float(x), float(y)) * uTexel * uRadius;
                        float error = differenceAt(uv, candidate);
                        if (error < bestError) {
                            bestError = error;
                            bestMotion = candidate;
                        }
                    }
                }
                vec3 previous = texture(uPrevious, uv - bestMotion * uPhase).rgb;
                vec3 current = texture(uCurrent, uv + bestMotion * (1.0 - uPhase)).rgb;
                if (bestError > 0.16) {
                    outColor = vec4(uPhase < 0.5 ? previous : current, 1.0);
                } else {
                    float confidence = 1.0 - smoothstep(0.04, 0.16, bestError);
                    vec3 warped = mix(previous, current, uPhase);
                    vec3 fallback = mix(texture(uPrevious, uv).rgb, texture(uCurrent, uv).rgb, uPhase);
                    outColor = vec4(mix(fallback, warped, confidence), 1.0);
                }
            }
        """
    }
}
