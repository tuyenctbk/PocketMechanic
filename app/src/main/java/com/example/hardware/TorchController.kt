package com.example.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.Camera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TorchController(context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isFlashAvailable = MutableStateFlow(false)
    val isFlashAvailable: StateFlow<Boolean> = _isFlashAvailable.asStateFlow()

    private var torchCameraId: String? = null
    private var activeCameraX: Camera? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var desiredTorchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _isTorchOn.value = enabled
            }
        }
    }

    init {
        torchCameraId = findFlashCameraId()
        _isFlashAvailable.value = torchCameraId != null
        try {
            cameraManager?.registerTorchCallback(torchCallback, mainHandler)
        } catch (_: Exception) {
        }
    }

    fun attachCameraX(camera: Camera?) {
        activeCameraX = camera
        if (camera != null) {
            if (camera.cameraInfo.hasFlashUnit()) {
                _isFlashAvailable.value = true
                camera.cameraControl.enableTorch(_isTorchOn.value || desiredTorchOn)
            }
        } else {
            restoreTorchAfterCameraXUnbind()
        }
    }

    fun clearCameraXReference() {
        desiredTorchOn = _isTorchOn.value
        activeCameraX = null
    }

    fun restoreTorchAfterCameraXUnbind() {
        if (!desiredTorchOn) return
        val id = torchCameraId ?: findFlashCameraId().also { torchCameraId = it }
        val mgr = cameraManager
        if (id == null || mgr == null) {
            desiredTorchOn = false
            _isTorchOn.value = false
            return
        }
        try {
            mgr.setTorchMode(id, true)
            _isTorchOn.value = true
        } catch (_: Exception) {
            // Camera2 HAL can take a brief moment to release after CameraX unbindAll()
            mainHandler.postDelayed({
                if (activeCameraX != null || !desiredTorchOn) return@postDelayed
                try {
                    mgr.setTorchMode(id, true)
                    _isTorchOn.value = true
                } catch (_: Exception) {
                    desiredTorchOn = false
                    _isTorchOn.value = false
                }
            }, 140L)
        }
    }

    fun toggleTorch(): Boolean {
        return setTorch(!_isTorchOn.value)
    }

    fun setTorch(enabled: Boolean): Boolean {
        desiredTorchOn = enabled
        val camX = activeCameraX
        if (camX != null && camX.cameraInfo.hasFlashUnit()) {
            camX.cameraControl.enableTorch(enabled)
            _isTorchOn.value = enabled
            return true
        }

        val id = torchCameraId ?: findFlashCameraId().also { torchCameraId = it }
        if (id == null || cameraManager == null) {
            desiredTorchOn = false
            _isFlashAvailable.value = false
            _isTorchOn.value = false
            return false
        }

        return try {
            cameraManager.setTorchMode(id, enabled)
            _isTorchOn.value = enabled
            true
        } catch (_: Exception) {
            desiredTorchOn = _isTorchOn.value
            false
        }
    }

    fun release() {
        desiredTorchOn = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    private fun findFlashCameraId(): String? {
        val manager = cameraManager ?: return null
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facingBack =
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && facingBack
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }
}
