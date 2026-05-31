package com.example.medicationtracker.ui.main

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.example.medicationtracker.R
import com.example.medicationtracker.utils.CameraHelper
import com.example.medicationtracker.utils.PermissionHelper

class CameraTestActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var flashButton: Button
    private lateinit var capturedImageView: ImageView

    private lateinit var cameraHelper: CameraHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_test)

        // Initialize views (we'll create the layout next)
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        flashButton = findViewById(R.id.flashButton)
        capturedImageView = findViewById(R.id.capturedImageView)

        // Initialize camera helper
        cameraHelper = CameraHelper(this, this, previewView)

        // Check camera permission
        if (cameraHelper.hasPermission()) {
            startCamera()
        } else {
            PermissionHelper.requestPermission(
                this,
                Manifest.permission.CAMERA,
                PermissionHelper.CAMERA_PERMISSION_CODE
            )
        }

        // Capture button click
        captureButton.setOnClickListener {
            captureImage()
        }

        // Flash button click
        flashButton.setOnClickListener {
            toggleFlash()
        }
    }

    private fun startCamera() {
        cameraHelper.startCamera { exception ->
            Toast.makeText(this, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
        }

        // Update flash button visibility
        flashButton.isEnabled = cameraHelper.hasFlash()
    }

    private fun captureImage() {
        cameraHelper.captureImage(
            onImageCaptured = { bitmap ->
                runOnUiThread {
                    // Show captured image
                    capturedImageView.setImageBitmap(bitmap)
                    Toast.makeText(this, "Image captured! ${bitmap.width}x${bitmap.height}", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { exception ->
                runOnUiThread {
                    Toast.makeText(this, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun toggleFlash() {
        val flashOn = cameraHelper.toggleFlash()
        flashButton.text = if (flashOn) "Flash: ON" else "Flash: OFF"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionHelper.CAMERA_PERMISSION_CODE) {
            if (PermissionHelper.isPermissionGranted(grantResults)) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.shutdown()
    }
}