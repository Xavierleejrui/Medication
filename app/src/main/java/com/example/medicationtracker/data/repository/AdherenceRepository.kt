package com.example.medicationtracker.data.repository

import com.example.medicationtracker.data.local.dao.AdherenceDao
import com.example.medicationtracker.data.local.entities.AdherenceLog
import kotlinx.coroutines.flow.Flow

class AdherenceRepository(private val adherenceDao: AdherenceDao) {

    fun getLogsForMedication(medicationId: String): Flow<List<AdherenceLog>> {
        return adherenceDao.getLogsForMedication(medicationId)
    }

    suspend fun insertLog(log: AdherenceLog) {
        adherenceDao.insertLog(log)
    }

    suspend fun getLogsInRange(startTime: Long, endTime: Long): List<AdherenceLog> {
        return adherenceDao.getLogsInRange(startTime, endTime)
    }

    suspend fun getAdherencePercentage(medicationId: String, startTime: Long): Float {
        val taken = adherenceDao.countTakenSince(medicationId, startTime)
        val scheduled = adherenceDao.countScheduledSince(medicationId, startTime)

        return if (scheduled > 0) {
            (taken.toFloat() / scheduled.toFloat()) * 100f
        } else {
            0f
        }
    }
}