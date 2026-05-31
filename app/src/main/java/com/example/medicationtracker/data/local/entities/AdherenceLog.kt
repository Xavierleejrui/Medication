package com.example.medicationtracker.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adherence_logs")
data class AdherenceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String,
    val scheduledTime: Long,
    val takenTime: Long? = null,
    val verifiedWithPhoto: Boolean = false,
    val similarityScore: Float? = null
)