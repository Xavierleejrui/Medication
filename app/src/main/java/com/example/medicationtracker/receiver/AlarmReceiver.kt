package com.example.medicationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.utils.AlarmScheduler
import com.example.medicationtracker.utils.MissedDosePrefs
import com.example.medicationtracker.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_DOSAGE = "dosage"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_TIME_INDEX = "time_index"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medication"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val hour = intent.getIntExtra(EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val timeIndex = intent.getIntExtra(EXTRA_TIME_INDEX, 0)

        // Check if previous dose was missed - require camera verification
        val verifyRequired = MissedDosePrefs.isMissed(context, medicationId)

        // Show the notification
        NotificationHelper.showMedicationReminder(
            context = context,
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            notificationId = notificationId,
            verifyRequired = verifyRequired
        )

        // Reschedule for tomorrow (exact alarms only fire once)
        CoroutineScope(Dispatchers.IO).launch {
            val db = MedicationDatabase.getDatabase(context)
            val medication = db.medicationDao().getMedicationById(medicationId) ?: return@launch

            AlarmScheduler.scheduleExactAlarm(
                context = context,
                medication = medication,
                hour = hour,
                minute = minute,
                timeIndex = timeIndex
            )
        }
    }
}