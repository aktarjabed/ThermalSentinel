package com.thermalsentinel.engine.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The single local database for the engine.
 *
 * Migration policy: `fallbackToDestructiveMigration` is deliberately **not**
 * used. Deleting history because a schema version was bumped would destroy the
 * only copy of the user's data, so a version bump requires a hand-written
 * `Migration` plus a test. Schema export is off in version 1 for the same
 * reason it exists in general: there is nothing to export until there is a
 * second version, and the engine does not ship invented schema files.
 */
@Database(
    entities = [
        ThermalSampleEntity::class,
        ThermalAggregateEntity::class,
        ThermalEventEntity::class,
        ChargingSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EngineDatabase : RoomDatabase() {

    abstract fun sampleDao(): ThermalSampleDao
    abstract fun aggregateDao(): ThermalAggregateDao
    abstract fun eventDao(): ThermalEventDao
    abstract fun chargingSessionDao(): ChargingSessionDao

    companion object {
        const val DATABASE_NAME = "thermal_sentinel_engine.db"

        @Volatile
        private var instance: EngineDatabase? = null

        fun get(context: Context): EngineDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): EngineDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EngineDatabase::class.java,
                DATABASE_NAME
            ).build()
    }
}
