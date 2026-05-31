package com.example.medicationtracker.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val scheduleJson: String,        // JSON: ["08:00", "20:00"]
    val imagePaths: String,          // JSON: ["/path1.jpg", ...]
    val featureVectorsJson: String,  // JSON: [[0.1, 0.2, ...], [...]]
    val caregiverMode: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)