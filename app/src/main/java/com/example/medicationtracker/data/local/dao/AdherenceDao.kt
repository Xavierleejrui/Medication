package com.example.medicationtracker.data.local.dao

import androidx.room.*
import com.example.medicationtracker.data.local.entities.AdherenceLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AdherenceDao {

    @Insert
    suspend fun insertLog(log: AdherenceLog)

    @Query("SELECT * FROM adherence_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getLogsForMedication(medicationId: String): Flow<List<AdherenceLog>>

    @Query("SELECT * FROM adherence_logs WHERE scheduledTime >= :startTime AND scheduledTime < :endTime")
    suspend fun getLogsInRange(startTime: Long, endTime: Long): List<AdherenceLog>

    @Query("SELECT COUNT(*) FROM adherence_logs WHERE medicationId = :medicationId AND takenTime IS NOT NULL AND scheduledTime >= :startTime")
    suspend fun countTakenSince(medicationId: String, startTime: Long): Int

    @Query("SELECT COUNT(*) FROM adherence_logs WHERE medicationId = :medicationId AND scheduledTime >= :startTime")
    suspend fun countScheduledSince(medicationId: String, startTime: Long): Int
}