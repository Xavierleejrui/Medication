package com.example.medicationtracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.medicationtracker.receiver.NotificationActionReceiver
import com.example.medicationtracker.ui.verify.VerificationActivity

object NotificationHelper {

    const val CHANNEL_ID = "medication_reminders"
    const val CHANNEL_NAME = "Medication Reminders"

    // Actions
    const val ACTION_TAKEN = "com.example.medicationtracker.ACTION_TAKEN"
    const val ACTION_NOT_YET = "com.example.medicationtracker.ACTION_NOT_YET"

    // Extras
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_MEDICATION_NAME = "medication_name"
    const val EXTRA_DOSAGE = "dosage"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_VERIFY_REQUIRED = "verify_required"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders to take your medications"
            enableVibration(true)
            enableLights(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // ─── NORMAL NOTIFICATION (first reminder) ───────────────────────
    fun showMedicationReminder(
        context: Context,
        medicationId: String,
        medicationName: String,
        dosage: String,
        notificationId: Int,
        verifyRequired: Boolean = false  // true if previous dose was missed
    ) {
        if (verifyRequired) {
            showVerificationRequiredNotification(
                context, medicationId, medicationName, dosage, notificationId
            )
            return
        }

        // "I've taken it" → self-report
        val takenPendingIntent = buildActionPendingIntent(
            context = context,
            action = ACTION_TAKEN,
            medicationId = medicationId,
            notificationId = notificationId,
            requestCode = notificationId * 10
        )

        // "Not yet" → log missed, dismiss
        val notYetPendingIntent = buildActionPendingIntent(
            context = context,
            action = ACTION_NOT_YET,
            medicationId = medicationId,
            notificationId = notificationId,
            requestCode = notificationId * 10 + 1
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💊 Time for your medication!")
            .setContentText("Have you taken $medicationName ($dosage)?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "It's time to take $medicationName ($dosage).\n\n" +
                                "Tap 'I've taken it' to confirm.\n" +
                                "Tap 'Not yet' if you haven't taken it — " +
                                "your next dose will require camera verification."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)  // Stays until user responds
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_agenda,
                "✅ I've taken it",
                takenPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "❌ Not yet",
                notYetPendingIntent
            )
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    // ─── VERIFICATION REQUIRED NOTIFICATION ─────────────────────────
    private fun showVerificationRequiredNotification(
        context: Context,
        medicationId: String,
        medicationName: String,
        dosage: String,
        notificationId: Int
    ) {
        // Only option: verify with camera
        val verifyIntent = Intent(context, VerificationActivity::class.java).apply {
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val verifyPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 2,
            verifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Verification required!")
            .setContentText("You missed your last $medicationName dose. Verify with camera.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "You missed your last dose of $medicationName ($dosage).\n\n" +
                                "You must verify this dose with the camera for it to count."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)  // Cannot be swiped away
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_camera,
                "📷 Verify pill now",
                verifyPendingIntent
            )
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    // ─── HELPER ──────────────────────────────────────────────────────
    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        medicationId: String,
        notificationId: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
    }
}