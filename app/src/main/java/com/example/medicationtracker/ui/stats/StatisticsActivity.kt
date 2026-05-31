package com.example.medicationtracker.ui.stats

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.medicationtracker.R
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.repository.AdherenceRepository
import com.example.medicationtracker.data.repository.MedicationRepository
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class StatisticsActivity : AppCompatActivity() {

    private lateinit var adherenceChart: BarChart
    private lateinit var overallPercentageText: TextView
    private lateinit var totalTakenText: TextView
    private lateinit var totalScheduledText: TextView

    private lateinit var medicationRepository: MedicationRepository
    private lateinit var adherenceRepository: AdherenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        adherenceChart = findViewById(R.id.adherenceChart)
        overallPercentageText = findViewById(R.id.overallPercentageText)
        totalTakenText = findViewById(R.id.totalTakenText)
        totalScheduledText = findViewById(R.id.totalScheduledText)

        val db = MedicationDatabase.getDatabase(this)
        medicationRepository = MedicationRepository(db.medicationDao())
        adherenceRepository = AdherenceRepository(db.adherenceDao())

        loadStatistics()
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            val medications = medicationRepository.allMedications.first()

            if (medications.isEmpty()) {
                overallPercentageText.text = "No data yet"
                return@launch
            }

            // Calculate stats for last 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

            var totalTaken = 0
            var totalScheduled = 0
            val entries = mutableListOf<BarEntry>()

            medications.forEachIndexed { index, medication ->
                val taken = adherenceRepository.getAdherencePercentage(medication.id, sevenDaysAgo)
                entries.add(BarEntry(index.toFloat(), taken))

                // Count totals (simplified)
                totalScheduled++
                if (taken > 0) totalTaken++
            }

            // Calculate overall 7-day adherence
            val overallAdherence = if (medications.isNotEmpty()) {
                val calendar = Calendar.getInstance()
                val startTime = sevenDaysAgo
                val allMedAdherence = medications.map { med ->
                    adherenceRepository.getAdherencePercentage(med.id, startTime)
                }
                allMedAdherence.average().toFloat()
            } else 0f

            // Update UI
            overallPercentageText.text = "${String.format("%.1f", overallAdherence)}%"
            totalTakenText.text = "Verified: $totalTaken medications"
            totalScheduledText.text = "Registered: ${medications.size} medications"

            // Setup bar chart
            setupBarChart(entries, medications.map { it.name })
        }
    }

    private fun setupBarChart(entries: List<BarEntry>, labels: List<String>) {
        if (entries.isEmpty()) return

        val dataSet = BarDataSet(entries, "Adherence %").apply {
            color = getColor(android.R.color.holo_blue_light)
            valueTextSize = 12f
        }

        adherenceChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            setFitBars(true)
            animateY(1000)
            invalidate()
        }
    }
}