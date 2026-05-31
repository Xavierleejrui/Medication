package com.example.medicationtracker.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.medicationtracker.data.local.entities.Medication
import com.example.medicationtracker.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    fun scheduleMedicationAlarms(
        context: Context,
        medication: Medication,
        times: List<String>
    ) {
        times.forEachIndexed { index, time ->
            val timePair = parseTime(time) ?: return@forEachIndexed
            scheduleExactAlarm(context, medication, timePair.first, timePair.second, index)
        }
    }

    fun scheduleExactAlarm(
        context: Context,
        medication: Medication,
        hour: Int,
        minute: Int,
        timeIndex: Int
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val notificationId = Math.abs(medication.id.hashCode() + timeIndex) % 100000

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medication.id)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medication.name)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, medication.dosage)
            putExtra(AlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(AlarmReceiver.EXTRA_HOUR, hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
            putExtra(AlarmReceiver.EXTRA_TIME_INDEX, timeIndex)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use exact alarm - fires precisely at the scheduled time
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        println("✅ Exact alarm set for ${medication.name} at $hour:$minute")
    }

    fun cancelMedicationAlarms(
        context: Context,
        medication: Medication,
        times: List<String>
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        times.forEachIndexed { index, _ ->
            val notificationId = Math.abs(medication.id.hashCode() + index) % 100000

            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun parseTime(time: String): Pair<Int, Int>? {
        return try {
            val parts = time.trim().split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
        } catch (e: Exception) {
            null
        }
    }
}