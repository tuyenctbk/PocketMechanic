package com.example.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraXManager(
    private val context: Context,
    private val torchController: TorchController
) {
    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null

    @Volatile
    private var isReleased: Boolean = false

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: () -> Unit = {},
        onError: (String) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        val mainExecutor = ContextCompat.getMainExecutor(appContext)

        providerFuture.addListener({
            if (isReleased) return@addListener
            try {
                val provider = providerFuture.get()
                if (isReleased) return@addListener
                cameraProvider = provider

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy(
                            AspectRatio.RATIO_4_3,
                            AspectRatioStrategy.FALLBACK_RULE_AUTO
                        )
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolutionSelector)
                    .build()
                imageCapture = capture

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                boundCamera = camera
                torchController.attachCameraX(camera)
                onBound()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                onError(e.localizedMessage ?: "Camera initialization failed.")
            }
        }, mainExecutor)
    }

    fun focusAtPoint(previewView: PreviewView, x: Float, y: Float) {
        val camera = boundCamera ?: return
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            camera.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.w(TAG, "Tap-to-focus unavailable: ${e.message}")
        }
    }

    fun setLinearZoom(linearZoom: Float) {
        val camera = boundCamera ?: return
        try {
            camera.cameraControl.setLinearZoom(linearZoom.coerceIn(0f, 1f))
        } catch (e: Exception) {
            Log.w(TAG, "Zoom adjustment unavailable: ${e.message}")
        }
    }

    fun capturePhoto(
        onCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera is still warming up. Please try again.")
            return
        }

        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotated = imageProxyToRotatedBitmap(image)
                        mainExecutor.execute { onCaptured(rotated) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Bitmap conversion failed", e)
                        mainExecutor.execute {
                            onError(e.localizedMessage ?: "Failed to process captured image.")
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "ImageCapture failed", exception)
                    mainExecutor.execute {
                        onError(exception.message ?: "Photo capture failed.")
                    }
                }
            }
        )
    }

    fun release() {
        isReleased = true
        torchController.clearCameraXReference()
        boundCamera = null
        imageCapture = null
        runCatching { cameraProvider?.unbindAll() }
        torchController.restoreTorchAfterCameraXUnbind()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }

    private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    private companion object {
        const val TAG = "CameraXManager"
    }
}
