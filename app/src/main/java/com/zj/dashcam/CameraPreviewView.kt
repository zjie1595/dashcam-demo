package com.zj.dashcam

import android.content.Context
import android.util.AttributeSet
import android.opengl.GLSurfaceView

class CameraPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    private val logger = Logger("CameraPreviewView")
    private val renderer: CameraRenderer
    private val cameraController = Camera2Controller(context, Logger("Camera2Controller"))
    @Volatile
    private var surfaceReady = false

    init {
        setEGLContextClientVersion(2)
        renderer = CameraRenderer(Logger("CameraRenderer"))
        renderer.setSurfaceTextureListener { texture ->
            logger.d("收到相机预览SurfaceTexture，准备启动相机。")
            surfaceReady = true
            cameraController.setPreviewTexture(texture)
            if (isStarted) {
                cameraController.start()
            }
        }
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private var isStarted = false

    /**
     * 启动相机预览，要求外部先完成相机权限校验。
     */
    fun startPreview() {
        if (isStarted) {
            logger.d("startPreview 已忽略：当前已处于预览状态。")
            return
        }
        isStarted = true
        if (surfaceReady) {
            logger.d("开始相机预览。")
            cameraController.start()
        } else {
            logger.d("等待SurfaceTexture准备完成后启动相机预览。")
        }
    }

    /**
     * 停止相机预览并释放占用的资源。
     */
    fun stopPreview() {
        if (!isStarted) {
            logger.d("stopPreview 已忽略：当前未处于预览状态。")
            return
        }
        isStarted = false
        logger.d("停止相机预览。")
        cameraController.stop()
    }

}
