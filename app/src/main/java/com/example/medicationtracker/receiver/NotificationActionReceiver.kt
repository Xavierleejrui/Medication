package com.example.medicationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.local.entities.AdherenceLog
import com.example.medicationtracker.data.repository.AdherenceRepository
import com.example.medicationtracker.utils.MissedDosePrefs
import com.example.medicationtracker.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(NotificationHelper.EXTRA_MEDICATION_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {

            // ✅ User confirmed they took it - self report, no camera needed
            NotificationHelper.ACTION_TAKEN -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = MedicationDatabase.getDatabase(context)
                    val adherenceRepository = AdherenceRepository(db.adherenceDao())

                    adherenceRepository.insertLog(
                        AdherenceLog(
                            medicationId = medicationId,
                            scheduledTime = System.currentTimeMillis(),
                            takenTime = System.currentTimeMillis(),
                            verifiedWithPhoto = false,
                            similarityScore = null
                        )
                    )
                }

                // Clear any "missed" flag for this medication
                MissedDosePrefs.clearMissed(context, medicationId)

                // Dismiss notification
                NotificationHelper.dismissNotification(context, notificationId)

                Toast.makeText(
                    context,
                    "✅ Great! Medication marked as taken.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // ❌ User said they haven't taken it yet
            NotificationHelper.ACTION_NOT_YET -> {
                // Mark this medication as having a missed dose
                // Next notification will require camera verification
                MissedDosePrefs.setMissed(context, medicationId)

                // Dismiss current notification
                NotificationHelper.dismissNotification(context, notificationId)

                Toast.makeText(
                    context,
                    "⚠️ Noted. Your next dose will require camera verification.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}