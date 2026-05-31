package com.example.medicationtracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.medicationtracker.data.local.dao.AdherenceDao
import com.example.medicationtracker.data.local.dao.MedicationDao
import com.example.medicationtracker.data.local.entities.AdherenceLog
import com.example.medicationtracker.data.local.entities.Medication

@Database(
    entities = [Medication::class, AdherenceLog::class],
    version = 1,
    exportSchema = false
)
abstract class MedicationDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun adherenceDao(): AdherenceDao

    companion object {
        @Volatile
        private var INSTANCE: MedicationDatabase? = null

        fun getDatabase(context: Context): MedicationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicationDatabase::class.java,
                    "medication_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}