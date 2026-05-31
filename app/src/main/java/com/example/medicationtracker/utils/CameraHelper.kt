package com.example.medicationtracker.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "CameraHelper"
        const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        const val MIN_ZOOM = 1.0f
        const val MAX_ZOOM = 5.0f
        const val ZOOM_STEP = 0.5f
    }

    private var currentZoom = 1.0f

    /**
     * Increase zoom by one step (for small pills)
     */
    fun zoomIn(): Float {
        currentZoom = (currentZoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM)
        applyZoom()
        return currentZoom
    }

    /**
     * Decrease zoom by one step
     */
    fun zoomOut(): Float {
        currentZoom = (currentZoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM)
        applyZoom()
        return currentZoom
    }

    fun getCurrentZoom() = currentZoom

    private fun applyZoom() {
        camera?.cameraControl?.setZoomRatio(currentZoom)
    }
    /**
     * Check if camera permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start camera preview
     */
    fun startCamera(onError: (Exception) -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Bind camera use cases (preview + image capture)
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        // Select back camera as default
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            Log.d(TAG, "Camera bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    /**
     * Capture image and return as Bitmap
     */
    fun captureImage(onImageCaptured: (Bitmap) -> Unit, onError: (Exception) -> Unit) {
        val imageCapture = imageCapture ?: run {
            onError(IllegalStateException("Camera not initialized"))
            return
        }

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        onImageCaptured(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting image", e)
                        onError(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    onError(exception)
                }
            }
        )
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate bitmap if needed based on image rotation
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            bitmap = rotateBitmap(bitmap, rotationDegrees.toFloat())
        }

        return bitmap
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    /**
     * Toggle flash on/off
     */
    fun toggleFlash(): Boolean {
        val camera = camera ?: return false

        return if (camera.cameraInfo.hasFlashUnit()) {
            val currentFlashMode = imageCapture?.flashMode
            val newFlashMode = if (currentFlashMode == ImageCapture.FLASH_MODE_ON) {
                ImageCapture.FLASH_MODE_OFF
            } else {
                ImageCapture.FLASH_MODE_ON
            }
            imageCapture?.flashMode = newFlashMode
            newFlashMode == ImageCapture.FLASH_MODE_ON
        } else {
            false
        }
    }

    /**
     * Check if device has flash
     */
    fun hasFlash(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    /**
     * Stop camera and release resources
     */
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}