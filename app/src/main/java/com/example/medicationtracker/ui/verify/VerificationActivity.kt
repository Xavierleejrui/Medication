package com.example.medicationtracker.ui.verify

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.example.medicationtracker.R
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.local.entities.AdherenceLog
import com.example.medicationtracker.data.repository.AdherenceRepository
import com.example.medicationtracker.data.repository.MedicationRepository
import com.example.medicationtracker.ml.PillFeatureExtractor
import com.example.medicationtracker.ml.PillVerifier
import com.example.medicationtracker.utils.CameraHelper
import com.example.medicationtracker.utils.MissedDosePrefs
import com.example.medicationtracker.utils.NotificationHelper
import com.example.medicationtracker.utils.PermissionHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VerificationActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var verifyButton: Button
    private lateinit var resultText: TextView
    private lateinit var medicationNameText: TextView
    private lateinit var similarityText: TextView
    private lateinit var statusIcon: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private lateinit var cameraHelper: CameraHelper
    private lateinit var featureExtractor: PillFeatureExtractor
    private val verifier = PillVerifier()
    private lateinit var medicationRepository: MedicationRepository
    private lateinit var adherenceRepository: AdherenceRepository
    private lateinit var verifyZoomInBtn: Button
    private lateinit var verifyZoomOutBtn: Button
    private lateinit var verifyZoomLabel: TextView

    companion object {
        // MobileNetV3 features: first 576 dimensions of combined vector
        private const val MOBILENET_DIMS = 576

        // Separate thresholds for dual-gate
        private const val MOBILENET_THRESHOLD = 0.72f  // Texture/shape match
        private const val COLOR_THRESHOLD = 0.70f       // Color match
        // Both must pass for verification to succeed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        initializeViews()
        initializeComponents()
        setInitialState()

        if (cameraHelper.hasPermission()) {
            cameraHelper.startCamera {}
        } else {
            PermissionHelper.requestPermission(
                this,
                Manifest.permission.CAMERA,
                PermissionHelper.CAMERA_PERMISSION_CODE
            )
        }

        verifyZoomInBtn.setOnClickListener {
            val zoom = cameraHelper.zoomIn()
            verifyZoomLabel.text = "${zoom}x"
        }
        verifyZoomOutBtn.setOnClickListener {
            val zoom = cameraHelper.zoomOut()
            verifyZoomLabel.text = "${zoom}x"
        }

        verifyButton.setOnClickListener { verifyPill() }
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        verifyButton = findViewById(R.id.verifyButton)
        resultText = findViewById(R.id.resultText)
        medicationNameText = findViewById(R.id.medicationNameText)
        similarityText = findViewById(R.id.similarityText)
        statusIcon = findViewById(R.id.statusIcon)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        verifyZoomInBtn = findViewById(R.id.verifyZoomInBtn)
        verifyZoomOutBtn = findViewById(R.id.verifyZoomOutBtn)
        verifyZoomLabel = findViewById(R.id.verifyZoomLabel)
    }

    private fun initializeComponents() {
        cameraHelper = CameraHelper(this, this, previewView)
        featureExtractor = PillFeatureExtractor(this)
        val db = MedicationDatabase.getDatabase(this)
        medicationRepository = MedicationRepository(db.medicationDao())
        adherenceRepository = AdherenceRepository(db.adherenceDao())
    }

    private fun setInitialState() {
        statusIcon.text = "📷"
        resultText.text = "Place pill inside the box"
        resultText.setTextColor(getColor(android.R.color.black))
        medicationNameText.text = "Then tap VERIFY PILL"
        similarityText.text = ""
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
    }

    private fun verifyPill() {
        verifyButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = "Capturing..."
        statusIcon.text = "🔍"
        resultText.text = "Analyzing..."
        resultText.setTextColor(getColor(android.R.color.black))
        medicationNameText.text = ""
        similarityText.text = ""

        cameraHelper.captureImage(
            onImageCaptured = { bitmap ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            progressText.text = "Extracting features..."
                        }

                        // Extract COMBINED features (MobileNetV3 + Color = 704-dim)
                        val combinedFeatures = withContext(Dispatchers.Default) {
                            featureExtractor.extractCombinedFeatures(bitmap)
                        }

                        println("✅ Combined feature size: ${combinedFeatures.size}")

                        // Split into MobileNetV3 and Color parts
                        val mobilenetQuery = combinedFeatures.take(MOBILENET_DIMS).toFloatArray()
                        val colorQuery = combinedFeatures.drop(MOBILENET_DIMS).toFloatArray()

                        withContext(Dispatchers.Main) {
                            progressText.text = "Matching against medications..."
                        }

                        val medications = medicationRepository.allMedications.first()

                        if (medications.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                showResult(false, "No medications registered!", "", 0f, 0f, 0f)
                                resetUI()
                            }
                            return@launch
                        }

                        val gson = Gson()
                        var bestMatch: String? = null
                        var bestMobilenetScore = 0f
                        var bestColorScore = 0f
                        var bestMedId = ""

                        for (medication in medications) {
                            val type = object : TypeToken<List<List<Float>>>() {}.type
                            val storedVectorsList: List<List<Float>> = gson.fromJson(
                                medication.featureVectorsJson, type
                            )
                            val storedCombined = storedVectorsList.map { it.toFloatArray() }

                            if (storedCombined.isEmpty()) continue

                            // Check stored vector dimensions
                            val storedDim = storedCombined.first().size
                            println("Stored vector dim for ${medication.name}: $storedDim")

                            if (storedDim != combinedFeatures.size) {
                                // Dimension mismatch - medication registered with old format
                                println("⚠️ ${medication.name}: dimension mismatch! Stored=$storedDim, Query=${combinedFeatures.size}")
                                println("⚠️ Please delete and re-register this medication!")
                                continue
                            }

                            // Split stored vectors into MobileNetV3 and Color parts
                            val mobilenetStored = storedCombined.map {
                                it.take(MOBILENET_DIMS).toFloatArray()
                            }
                            val colorStored = storedCombined.map {
                                it.drop(MOBILENET_DIMS).toFloatArray()
                            }

                            // Score each gate separately
                            val mobilenetScore = verifier.calculateAverageSimilarity(
                                mobilenetQuery, mobilenetStored
                            )
                            val colorScore = verifier.calculateAverageSimilarity(
                                colorQuery, colorStored
                            )

                            println("${medication.name}: MobileNet=${String.format("%.3f", mobilenetScore)} Color=${String.format("%.3f", colorScore)}")

                            // Track best overall match (for display purposes)
                            val combinedScore = (mobilenetScore + colorScore) / 2f
                            val bestPrevCombined = (bestMobilenetScore + bestColorScore) / 2f

                            if (combinedScore > bestPrevCombined) {
                                bestMobilenetScore = mobilenetScore
                                bestColorScore = colorScore
                                bestMatch = "${medication.name} (${medication.dosage})"
                                bestMedId = medication.id
                            }
                        }

                        // DUAL GATE: BOTH must pass independently
                        val mobilenetPass = bestMobilenetScore >= MOBILENET_THRESHOLD
                        val colorPass = bestColorScore >= COLOR_THRESHOLD
                        val isMatch = mobilenetPass && colorPass

                        println("RESULT: MobileNet=${String.format("%.3f", bestMobilenetScore)} (pass=$mobilenetPass) | Color=${String.format("%.3f", bestColorScore)} (pass=$colorPass) | Match=$isMatch")

                        if (isMatch && bestMedId.isNotEmpty()) {
                            adherenceRepository.insertLog(
                                AdherenceLog(
                                    medicationId = bestMedId,
                                    scheduledTime = System.currentTimeMillis(),
                                    takenTime = System.currentTimeMillis(),
                                    verifiedWithPhoto = true,
                                    similarityScore = bestMobilenetScore
                                )
                            )
                            MissedDosePrefs.clearMissed(this@VerificationActivity, bestMedId)

                            val notificationId = intent.getIntExtra(
                                NotificationHelper.EXTRA_NOTIFICATION_ID, -1
                            )
                            if (notificationId != -1) {
                                NotificationHelper.dismissNotification(
                                    this@VerificationActivity, notificationId
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            showResult(
                                isMatch,
                                bestMatch ?: "Unknown",
                                bestMedId,
                                bestMobilenetScore,
                                bestColorScore,
                                mobilenetPass,
                                colorPass
                            )
                            resetUI()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            resultText.text = "Error: ${e.message}"
                            println("❌ Verification error: ${e.message}")
                            e.printStackTrace()
                            resetUI()
                        }
                    }
                }
            },
            onError = { exception ->
                runOnUiThread {
                    resultText.text = "Capture failed: ${exception.message}"
                    resetUI()
                }
            }
        )
    }

    private fun showResult(
        isMatch: Boolean,
        medicationName: String,
        medId: String,
        mobilenetScore: Float,
        colorScore: Float,
        mobilenetPass: Boolean = true,
        colorPass: Boolean = true
    ) {
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE

        if (isMatch) {
            statusIcon.text = "✅"
            resultText.text = "CORRECT MEDICATION"
            resultText.setTextColor(getColor(android.R.color.holo_green_dark))
            medicationNameText.text = medicationName
            similarityText.text = "Shape: ${String.format("%.0f", mobilenetScore * 100)}%  " +
                    "Color: ${String.format("%.0f", colorScore * 100)}%"
        } else {
            statusIcon.text = "❌"
            resultText.text = "MEDICATION MISMATCH"
            resultText.setTextColor(getColor(android.R.color.holo_red_dark))

            // Tell user WHICH gate failed
            val reason = when {
                !mobilenetPass && !colorPass -> "Shape and color do not match"
                !mobilenetPass -> "Shape/texture does not match (${String.format("%.0f", mobilenetScore * 100)}%)"
                !colorPass -> "Color does not match (${String.format("%.0f", colorScore * 100)}%)"
                else -> "No match found"
            }
            medicationNameText.text = reason
            similarityText.text = "Shape: ${String.format("%.0f", mobilenetScore * 100)}% " +
                    "(need ${(MOBILENET_THRESHOLD * 100).toInt()}%)  " +
                    "Color: ${String.format("%.0f", colorScore * 100)}% " +
                    "(need ${(COLOR_THRESHOLD * 100).toInt()}%)"
        }
    }

    // Overload for simple calls without gate info
    private fun showResult(
        isMatch: Boolean,
        medicationName: String,
        medId: String,
        score: Float,
        colorScore: Float,
        score2: Float
    ) {
        showResult(isMatch, medicationName, medId, score, colorScore)
    }

    private fun resetUI() {
        verifyButton.isEnabled = true
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.CAMERA_PERMISSION_CODE) {
            if (PermissionHelper.isPermissionGranted(grantResults)) {
                cameraHelper.startCamera {}
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