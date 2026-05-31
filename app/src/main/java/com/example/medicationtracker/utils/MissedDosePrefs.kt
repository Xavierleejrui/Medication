package com.example.medicationtracker.utils

import android.content.Context

object MissedDosePrefs {

    private const val PREFS_NAME = "missed_doses"

    // Mark a medication as having a missed dose
    fun setMissed(context: Context, medicationId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(medicationId, true)
            .apply()
    }

    // Clear the missed flag (called when user takes it)
    fun clearMissed(context: Context, medicationId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(medicationId)
            .apply()
    }

    // Check if this medication has a missed dose
    fun isMissed(context: Context, medicationId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(medicationId, false)
    }
}