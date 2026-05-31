package com.example.medicationtracker.ui.add

import android.Manifest
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medicationtracker.R
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.local.entities.Medication
import com.example.medicationtracker.data.repository.MedicationRepository
import com.example.medicationtracker.ml.PillFeatureExtractor
import com.example.medicationtracker.utils.AlarmScheduler
import com.example.medicationtracker.utils.CameraHelper
import com.example.medicationtracker.utils.PermissionHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AddMedicationActivity : AppCompatActivity() {

    // Camera section
    private lateinit var scrollView: ScrollView
    private lateinit var cameraContainer: View
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var photoCountText: TextView
    private lateinit var thumbnailRecyclerView: RecyclerView
    private lateinit var zoomInBtn: Button
    private lateinit var zoomOutBtn: Button
    private lateinit var zoomLabel: TextView

    // Info section
    private lateinit var infoSection: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var dosageInput: EditText
    private lateinit var addTimeButton: Button
    private lateinit var noTimesText: TextView
    private lateinit var timesChipGroup: ChipGroup
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    // Camera & ML
    private lateinit var cameraHelper: CameraHelper
    private lateinit var featureExtractor: PillFeatureExtractor
    private lateinit var repository: MedicationRepository

    // Data
    private val capturedPhotos = mutableListOf<Bitmap>()
    private val selectedTimes = mutableListOf<String>()
    private lateinit var thumbnailAdapter: ThumbnailAdapter

    companion object {
        private const val MIN_PHOTOS = 3
        private const val MAX_PHOTOS = 5
        private const val MAX_TIMES = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medication)

        initializeViews()
        initializeComponents()
        setupUI()

        if (cameraHelper.hasPermission()) {
            startCamera()
        } else {
            PermissionHelper.requestPermission(
                this,
                Manifest.permission.CAMERA,
                PermissionHelper.CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun initializeViews() {
        scrollView = findViewById(R.id.scrollView)
        cameraContainer = findViewById(R.id.cameraContainer)
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        photoCountText = findViewById(R.id.photoCountText)
        thumbnailRecyclerView = findViewById(R.id.thumbnailRecyclerView)
        infoSection = findViewById(R.id.infoSection)
        nameInput = findViewById(R.id.nameInput)
        dosageInput = findViewById(R.id.dosageInput)
        addTimeButton = findViewById(R.id.addTimeButton)
        noTimesText = findViewById(R.id.noTimesText)
        timesChipGroup = findViewById(R.id.timesChipGroup)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progressBar)
        zoomInBtn = findViewById(R.id.zoomInBtn)
        zoomOutBtn = findViewById(R.id.zoomOutBtn)
        zoomLabel = findViewById(R.id.zoomLabel)
    }

    private fun initializeComponents() {
        cameraHelper = CameraHelper(this, this, previewView)
        featureExtractor = PillFeatureExtractor(this)

        val database = MedicationDatabase.getDatabase(this)
        repository = MedicationRepository(database.medicationDao())

        thumbnailAdapter = ThumbnailAdapter(capturedPhotos) { position ->
            removePhoto(position)
        }
        thumbnailRecyclerView.layoutManager = GridLayoutManager(this, 3)
        thumbnailRecyclerView.adapter = thumbnailAdapter
    }

    private fun setupUI() {
        updatePhotoCount()
        infoSection.visibility = View.GONE

        captureButton.setOnClickListener { capturePhoto() }
        saveButton.setOnClickListener { saveMedication() }
        addTimeButton.setOnClickListener { showTimePicker() }

        // Collapse camera when user taps name or dosage field
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) collapseCameraForTyping()
        }
        nameInput.onFocusChangeListener = focusListener
        dosageInput.onFocusChangeListener = focusListener

        zoomInBtn.setOnClickListener {
            val zoom = cameraHelper.zoomIn()
            zoomLabel.text = "${zoom}x"
        }
        zoomOutBtn.setOnClickListener {
            val zoom = cameraHelper.zoomOut()
            zoomLabel.text = "${zoom}x"
        }
    }

    // ─── TIME PICKER ────────────────────────────────────────────────

    private fun showTimePicker() {
        if (selectedTimes.size >= MAX_TIMES) {
            Toast.makeText(this, "Maximum $MAX_TIMES reminder times allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)

                // Prevent duplicate times
                if (selectedTimes.contains(timeStr)) {
                    Toast.makeText(this, "$timeStr already added", Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }

                selectedTimes.add(timeStr)
                addTimeChip(timeStr)
                updateTimesDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true  // 24-hour format
        ).show()
    }

    private fun addTimeChip(time: String) {
        val chip = Chip(this).apply {
            text = "⏰  $time"
            isCloseIconVisible = true
            isClickable = true
            isCheckable = false
            setOnCloseIconClickListener {
                selectedTimes.remove(time)
                timesChipGroup.removeView(this)
                updateTimesDisplay()
            }
        }
        timesChipGroup.addView(chip)
    }

    private fun updateTimesDisplay() {
        if (selectedTimes.isEmpty()) {
            noTimesText.visibility = View.VISIBLE
            timesChipGroup.visibility = View.GONE
        } else {
            noTimesText.visibility = View.GONE
            timesChipGroup.visibility = View.VISIBLE
        }

        // Update add button text
        addTimeButton.text = if (selectedTimes.size >= MAX_TIMES) {
            "Maximum times reached ($MAX_TIMES)"
        } else {
            "➕ Add Reminder Time (${selectedTimes.size}/$MAX_TIMES)"
        }
        addTimeButton.isEnabled = selectedTimes.size < MAX_TIMES
    }

    // ─── CAMERA ─────────────────────────────────────────────────────

    private fun startCamera() {
        cameraHelper.startCamera { exception ->
            Toast.makeText(this, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collapseCameraForTyping() {
        cameraContainer.visibility = View.GONE
        previewView.visibility = View.GONE

        if (capturedPhotos.size < MAX_PHOTOS) {
            captureButton.text = "📷 Add More Photos (${capturedPhotos.size}/$MAX_PHOTOS)"
        }

        scrollView.post {
            scrollView.smoothScrollTo(0, infoSection.top)
        }
    }

    private fun expandCameraForCapture() {
        cameraContainer.visibility = View.VISIBLE
        previewView.visibility = View.VISIBLE
        captureButton.text = "CAPTURE PHOTO"
    }

    private fun capturePhoto() {
        if (capturedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "Maximum $MAX_PHOTOS photos reached", Toast.LENGTH_SHORT).show()
            return
        }

        expandCameraForCapture()
        captureButton.isEnabled = false

        cameraHelper.captureImage(
            onImageCaptured = { bitmap ->
                runOnUiThread {
                    capturedPhotos.add(bitmap)
                    thumbnailAdapter.notifyItemInserted(capturedPhotos.size - 1)
                    updatePhotoCount()
                    captureButton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Photo ${capturedPhotos.size}/$MAX_PHOTOS captured!",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (capturedPhotos.size >= MIN_PHOTOS) {
                        infoSection.visibility = View.VISIBLE
                    }
                }
            },
            onError = { exception ->
                runOnUiThread {
                    Toast.makeText(this, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    captureButton.isEnabled = true
                }
            }
        )
    }

    private fun removePhoto(position: Int) {
        capturedPhotos.removeAt(position)
        thumbnailAdapter.notifyItemRemoved(position)
        updatePhotoCount()

        if (capturedPhotos.size < MIN_PHOTOS) {
            infoSection.visibility = View.GONE
            expandCameraForCapture()
        }
    }

    private fun updatePhotoCount() {
        photoCountText.text = "Photos: ${capturedPhotos.size}/$MAX_PHOTOS (min: $MIN_PHOTOS)"

        when {
            capturedPhotos.size >= MAX_PHOTOS -> {
                captureButton.text = "MAX PHOTOS REACHED"
                captureButton.isEnabled = false
            }
            cameraContainer.visibility == View.VISIBLE -> {
                captureButton.text = "CAPTURE PHOTO"
                captureButton.isEnabled = true
            }
        }
    }

    // ─── SAVE ────────────────────────────────────────────────────────

    private fun saveMedication() {
        val name = nameInput.text.toString().trim()
        val dosage = dosageInput.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            nameInput.error = "Please enter medication name"
            nameInput.requestFocus()
            return
        }
        if (dosage.isEmpty()) {
            dosageInput.error = "Please enter dosage"
            dosageInput.requestFocus()
            return
        }
        if (selectedTimes.isEmpty()) {
            Toast.makeText(
                this,
                "Please add at least one reminder time",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (capturedPhotos.size < MIN_PHOTOS) {
            Toast.makeText(
                this,
                "Please capture at least $MIN_PHOTOS photos",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Extract features from all photos
                val featureVectors = withContext(Dispatchers.Default) {
                    capturedPhotos.map { bitmap ->
                        featureExtractor.extractCombinedFeatures(bitmap)
                    }
                }

                // Convert to JSON-friendly format
                val gson = Gson()
                val scheduleJson = gson.toJson(selectedTimes)
                val featureVectorsForJson = featureVectors.map { it.toList() }
                val featureVectorsJson = gson.toJson(featureVectorsForJson)

                // Create medication entity
                val medication = Medication(
                    name = name,
                    dosage = dosage,
                    scheduleJson = scheduleJson,
                    imagePaths = "[]",
                    featureVectorsJson = featureVectorsJson,
                    caregiverMode = false
                )

                // Save to database
                repository.insert(medication)

                // Schedule daily alarms
                AlarmScheduler.scheduleMedicationAlarms(
                    this@AddMedicationActivity,
                    medication,
                    selectedTimes
                )

                withContext(Dispatchers.Main) {
                    val timesDisplay = selectedTimes.joinToString(", ")
                    Toast.makeText(
                        this@AddMedicationActivity,
                        "✅ $name registered!\nReminders set for: $timesDisplay",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddMedicationActivity,
                        "Error saving: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    saveButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
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
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.shutdown()
        featureExtractor.close()
    }
}