package com.example.medicationtracker.data.repository

import com.example.medicationtracker.data.local.dao.MedicationDao
import com.example.medicationtracker.data.local.entities.Medication
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val medicationDao: MedicationDao) {

    val allMedications: Flow<List<Medication>> = medicationDao.getAllMedications()

    suspend fun getMedicationById(id: String): Medication? {
        return medicationDao.getMedicationById(id)
    }

    suspend fun insert(medication: Medication) {
        medicationDao.insert(medication)
    }

    suspend fun update(medication: Medication) {
        medicationDao.update(medication)
    }

    suspend fun delete(medication: Medication) {
        medicationDao.delete(medication)
    }
}