package com.zj.dashcam

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val logger = Logger("MainActivity")
    private lateinit var previewView: CameraPreviewView
    private val loopRecordingManager: LoopRecordingManager by lazy {
        LoopRecordingManager(
            context = this,
            segmentDurationMs = 30_000L,
            maxSegments = 6
        )
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                logger.d("相机权限已授予，启动预览。")
                previewView.startPreview()
            } else {
                logger.w("相机权限被拒绝，无法启动预览。")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.preview_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        logger.d("onCreate: loop recording manager initialized.")
    }

    override fun onStart() {
        super.onStart()
        logger.d("onStart: starting loop recording.")
        loopRecordingManager.start()
        startCameraPreviewWithPermission()
    }

    override fun onStop() {
        logger.d("onStop: stopping loop recording.")
        previewView.stopPreview()
        loopRecordingManager.stop()
        super.onStop()
    }

    override fun onDestroy() {
        logger.d("onDestroy: releasing loop recording resources.")
        loopRecordingManager.release()
        super.onDestroy()
    }

    private fun startCameraPreviewWithPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            logger.d("已具备相机权限，直接启动预览。")
            previewView.startPreview()
        } else {
            logger.d("请求相机权限。")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
