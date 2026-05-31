package com.example.medicationtracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.repository.MedicationRepository
import com.example.medicationtracker.receiver.AlarmReceiver
import com.example.medicationtracker.ui.add.AddMedicationActivity
import com.example.medicationtracker.ui.main.MedicationListActivity
import com.example.medicationtracker.ui.stats.StatisticsActivity
import com.example.medicationtracker.ui.verify.VerificationActivity
import com.example.medicationtracker.utils.MissedDosePrefs
import com.example.medicationtracker.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var verificationBanner: CardView
    private lateinit var verificationBannerText: TextView
    private lateinit var verifyNowButton: Button
    private lateinit var repository: MedicationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        // Initialize repository
        val db = MedicationDatabase.getDatabase(this)
        repository = MedicationRepository(db.medicationDao())

        // Setup views
        verificationBanner = findViewById(R.id.verificationBanner)
        verificationBannerText = findViewById(R.id.verificationBannerText)
        verifyNowButton = findViewById(R.id.verifyNowButton)

        // Navigation buttons
        findViewById<Button>(R.id.addMedicationButton).setOnClickListener {
            startActivity(Intent(this, AddMedicationActivity::class.java))
        }
        findViewById<Button>(R.id.verifyButton).setOnClickListener {
            startActivity(Intent(this, VerificationActivity::class.java))
        }
        findViewById<Button>(R.id.medicationListButton).setOnClickListener {
            startActivity(Intent(this, MedicationListActivity::class.java))
        }
        findViewById<Button>(R.id.statisticsButton).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        // Verify now button - goes to verification
        verifyNowButton.setOnClickListener {
            startActivity(Intent(this, VerificationActivity::class.java))
        }

        // 🧪 TEST BUTTON - fires alarm in 10 seconds
        findViewById<Button>(R.id.testAlarmButton).setOnClickListener {
            scheduleTestAlarm()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh banner every time user comes back to main screen
        checkPendingVerifications()
    }

    private fun checkPendingVerifications() {
        lifecycleScope.launch {
            val medications = repository.allMedications.first()

            // Find medications with missed doses
            val missedMeds = medications.filter { med ->
                MissedDosePrefs.isMissed(this@MainActivity, med.id)
            }

            if (missedMeds.isNotEmpty()) {
                // Show banner
                verificationBanner.visibility = View.VISIBLE

                val names = missedMeds.joinToString(", ") { it.name }
                verificationBannerText.text =
                    "You missed a dose of: $names\n" +
                            "Camera verification required for your next dose."
            } else {
                verificationBanner.visibility = View.GONE
            }
        }
    }

    // 🧪 TEST FUNCTION - schedules alarm 10 seconds from now
    private fun scheduleTestAlarm() {
        lifecycleScope.launch {
            val medications = repository.allMedications.first()

            if (medications.isEmpty()) {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Register a medication first!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Use first registered medication for test
            val testMed = medications.first()
            val testNotifId = 99999

            val intent = Intent(this@MainActivity, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, testMed.id)
                putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, testMed.name)
                putExtra(AlarmReceiver.EXTRA_DOSAGE, testMed.dosage)
                putExtra(AlarmReceiver.EXTRA_NOTIFICATION_ID, testNotifId)
                putExtra(AlarmReceiver.EXTRA_HOUR, 8)
                putExtra(AlarmReceiver.EXTRA_MINUTE, 0)
                putExtra(AlarmReceiver.EXTRA_TIME_INDEX, 0)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this@MainActivity,
                testNotifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(AlarmManager::class.java)
            val triggerTime = System.currentTimeMillis() + 10_000  // 10 seconds

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )

            android.widget.Toast.makeText(
                this@MainActivity,
                "🧪 Test notification in 10 seconds!\nUsing: ${testMed.name}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}