package com.zj.dashcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.core.content.ContextCompat

class Camera2Controller(
    private val context: Context,
    private val logger: Logger
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val backgroundThread = HandlerThread("Camera2Thread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewTexture: SurfaceTexture? = null
    private var cameraId: String? = null
    private var started = false

    /**
     * 设置用于相机预览的SurfaceTexture，必须在start前完成绑定。
     */
    fun setPreviewTexture(texture: SurfaceTexture) {
        logger.d("绑定SurfaceTexture用于预览。")
        previewTexture = texture
    }

    /**
     * 启动相机预览，要求已经授予相机权限并绑定SurfaceTexture。
     */
    fun start() {
        if (started) {
            logger.d("start 已忽略：相机已启动。")
            return
        }
        if (!hasCameraPermission()) {
            logger.w("缺少相机权限，无法启动预览。")
            return
        }
        val texture = previewTexture
        if (texture == null) {
            logger.w("SurfaceTexture未准备好，等待后续绑定。")
            return
        }
        started = true
        cameraId = chooseCameraId()
        if (cameraId == null) {
            logger.e("未找到可用相机。")
            started = false
            return
        }
        try {
            logger.d("打开相机 cameraId=$cameraId")
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
        } catch (exception: CameraAccessException) {
            logger.e("打开相机失败。", exception)
            started = false
        } catch (exception: SecurityException) {
            logger.e("相机权限异常，无法打开相机。", exception)
            started = false
        }
    }

    /**
     * 停止相机预览并释放资源。
     */
    fun stop() {
        if (!started) {
            logger.d("stop 已忽略：相机未启动。")
            return
        }
        started = false
        closeSession()
        cameraDevice?.close()
        cameraDevice = null
        logger.d("相机已关闭。")
    }

    private fun chooseCameraId(): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            cameraIds.firstOrNull()
        } catch (exception: CameraAccessException) {
            logger.e("获取相机列表失败。", exception)
            null
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            logger.d("相机打开成功。")
            cameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            logger.w("相机断开连接。")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            logger.e("相机发生错误 error=$error。")
            camera.close()
            cameraDevice = null
        }
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val texture = previewTexture ?: return
        texture.setDefaultBufferSize(1280, 720)
        val surface = Surface(texture)
        try {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) {
                            logger.w("相机已关闭，忽略预览会话配置。")
                            return
                        }
                        logger.d("预览会话配置完成，开始请求预览帧。")
                        captureSession = session
                        val requestBuilder =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        requestBuilder.addTarget(surface)
                        requestBuilder.set(
                            CaptureRequest.CONTROL_MODE,
                            CaptureRequest.CONTROL_MODE_AUTO
                        )
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        logger.e("预览会话配置失败。")
                    }
                },
                backgroundHandler
            )
        } catch (exception: CameraAccessException) {
            logger.e("创建预览会话失败。", exception)
        }
    }

    private fun closeSession() {
        captureSession?.close()
        captureSession = null
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
}
