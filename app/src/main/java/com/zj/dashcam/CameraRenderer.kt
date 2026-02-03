package com.zj.dashcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer(
    private val logger: Logger
) : GLSurfaceView.Renderer {
    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = 0
    private var overlayTextureId = 0
    private val transformMatrix = FloatArray(16)
    private var previewProgram = 0
    private var overlayProgram = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var overlayWidth = 0
    private var overlayHeight = 0
    private var lastTimestampSec: Long = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val quadVertices = floatArrayOf(
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f,
        -1f, 1f, 0f, 0f,
        1f, 1f, 1f, 0f
    )
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadVertices)
            position(0)
        }

    private val overlayVertices = FloatArray(16)
    private val overlayBuffer: FloatBuffer = ByteBuffer.allocateDirect(overlayVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var surfaceTextureListener: ((SurfaceTexture) -> Unit)? = null

    /**
     * 注册SurfaceTexture回调，通知外部启动Camera2预览。
     */
    fun setSurfaceTextureListener(listener: (SurfaceTexture) -> Unit) {
        surfaceTextureListener = listener
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        logger.d("OpenGL Surface创建完成。")
        textureId = GlUtil.createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener {
                // 通过GLSurfaceView的连续渲染刷新画面
            }
        }
        previewProgram = GlUtil.createProgram(
            GlUtil.VERTEX_SHADER,
            GlUtil.FRAGMENT_SHADER_OES
        )
        overlayProgram = GlUtil.createProgram(
            GlUtil.VERTEX_SHADER,
            GlUtil.FRAGMENT_SHADER_2D
        )
        overlayTextureId = GlUtil.create2DTexture()
        Matrix.setIdentityM(transformMatrix, 0)
        surfaceTextureListener?.invoke(surfaceTexture!!)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
        logger.d("OpenGL Surface大小改变 width=$width height=$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        val texture = surfaceTexture ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        texture.updateTexImage()
        texture.getTransformMatrix(transformMatrix)
        drawCameraFrame()
        updateTimestampOverlayIfNeeded()
        drawOverlay()
    }

    private fun drawCameraFrame() {
        GLES20.glUseProgram(previewProgram)
        val positionHandle = GLES20.glGetAttribLocation(previewProgram, "aPosition")
        val textureHandle = GLES20.glGetAttribLocation(previewProgram, "aTexCoord")
        val matrixHandle = GLES20.glGetUniformLocation(previewProgram, "uTexMatrix")
        val samplerHandle = GLES20.glGetUniformLocation(previewProgram, "sTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(samplerHandle, 0)
        quadBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        quadBuffer.position(2)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)
        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, transformMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun updateTimestampOverlayIfNeeded() {
        val nowSec = System.currentTimeMillis() / 1000
        if (nowSec == lastTimestampSec) {
            return
        }
        lastTimestampSec = nowSec
        val text = dateFormat.format(Date())
        val padding = 24f
        val textWidth = paint.measureText(text)
        val textHeight = paint.fontMetrics.run { bottom - top }
        val bitmapWidth = (textWidth + padding * 2).toInt().coerceAtLeast(1)
        val bitmapHeight = (textHeight + padding * 2).toInt().coerceAtLeast(1)
        overlayWidth = bitmapWidth
        overlayHeight = bitmapHeight
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val baseline = padding - paint.fontMetrics.top
        canvas.drawText(text, padding, baseline, paint)
        GlUtil.update2DTexture(overlayTextureId, bitmap)
        bitmap.recycle()
        updateOverlayVertices()
        logger.d("更新时间戳纹理：$text")
    }

    private fun updateOverlayVertices() {
        if (viewWidth == 0 || viewHeight == 0) {
            return
        }
        val widthNdc = 2f * overlayWidth / viewWidth
        val heightNdc = 2f * overlayHeight / viewHeight
        val left = -1f + 0.02f
        val top = 1f - 0.02f
        val right = left + widthNdc
        val bottom = top - heightNdc
        val data = floatArrayOf(
            left, bottom, 0f, 1f,
            right, bottom, 1f, 1f,
            left, top, 0f, 0f,
            right, top, 1f, 0f
        )
        overlayBuffer.clear()
        overlayBuffer.put(data)
        overlayBuffer.position(0)
    }

    private fun drawOverlay() {
        if (overlayWidth == 0 || overlayHeight == 0) {
            return
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(overlayProgram)
        val positionHandle = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        val textureHandle = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")
        val samplerHandle = GLES20.glGetUniformLocation(overlayProgram, "sTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(samplerHandle, 0)
        overlayBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, overlayBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        overlayBuffer.position(2)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 16, overlayBuffer)
        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
