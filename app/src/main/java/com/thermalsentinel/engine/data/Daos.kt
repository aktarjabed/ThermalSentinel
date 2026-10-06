package com.thermalsentinel.engine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface ThermalSampleDao {

    @Insert
    suspend fun insert(sample: ThermalSampleEntity): Long

    @Query("SELECT * FROM thermal_samples ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun latest(): ThermalSampleEntity?

    /**
     * Everything from [fromMillis] on, oldest first.
     *
     * The rate detector asks for a *time* window rather than a row limit on
     * purpose — see `ThermalHistoryRepository.detectRateEvent` — so no
     * "newest N rows" query is offered here, and none should be added: a row count
     * means a different amount of elapsed time at every sampling cadence.
     */
    @Query("SELECT * FROM thermal_samples WHERE timestampMillis >= :fromMillis ORDER BY timestampMillis ASC")
    suspend fun since(fromMillis: Long): List<ThermalSampleEntity>

    @Query("SELECT * FROM thermal_samples WHERE timestampMillis < :beforeMillis ORDER BY timestampMillis ASC")
    suspend fun before(beforeMillis: Long): List<ThermalSampleEntity>

    @Query(
        "SELECT * FROM thermal_samples WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis " +
            "ORDER BY timestampMillis ASC"
    )
    suspend fun between(fromMillis: Long, toMillis: Long): List<ThermalSampleEntity>

    @Query("SELECT COUNT(*) FROM thermal_samples")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM thermal_samples WHERE timestampMillis >= :fromMillis")
    suspend fun countSince(fromMillis: Long): Long

    @Query("SELECT MIN(timestampMillis) FROM thermal_samples")
    suspend fun oldestTimestampMillis(): Long?

    @Query("DELETE FROM thermal_samples WHERE timestampMillis < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int
}

@Dao
interface ThermalAggregateDao {

    @Upsert
    suspend fun upsertAll(rows: List<ThermalAggregateEntity>)

    @Query(
        "SELECT * FROM thermal_aggregates WHERE bucketMillis = :bucketMillis AND bucketStartMillis >= :fromMillis " +
            "ORDER BY bucketStartMillis ASC"
    )
    suspend fun since(bucketMillis: Long, fromMillis: Long): List<ThermalAggregateEntity>

    @Query(
        "SELECT * FROM thermal_aggregates WHERE bucketMillis = :bucketMillis AND bucketStartMillis < :beforeMillis " +
            "ORDER BY bucketStartMillis ASC"
    )
    suspend fun before(bucketMillis: Long, beforeMillis: Long): List<ThermalAggregateEntity>

    @Query("SELECT COUNT(*) FROM thermal_aggregates WHERE bucketMillis = :bucketMillis")
    suspend fun count(bucketMillis: Long): Long

    @Query("DELETE FROM thermal_aggregates WHERE bucketMillis = :bucketMillis AND bucketStartMillis < :beforeMillis")
    suspend fun deleteBefore(bucketMillis: Long, beforeMillis: Long): Int
}

@Dao
interface ThermalEventDao {

    @Insert
    suspend fun insert(event: ThermalEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<ThermalEventEntity>)

    @Query("SELECT * FROM thermal_events ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ThermalEventEntity>

    @Query("SELECT * FROM thermal_events WHERE timestampMillis >= :fromMillis ORDER BY timestampMillis DESC")
    suspend fun since(fromMillis: Long): List<ThermalEventEntity>

    @Query("SELECT * FROM thermal_events WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis ORDER BY timestampMillis ASC")
    suspend fun between(fromMillis: Long, toMillis: Long): List<ThermalEventEntity>

    @Query("SELECT COUNT(*) FROM thermal_events")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM thermal_events WHERE timestampMillis >= :fromMillis")
    suspend fun countSince(fromMillis: Long): Long

    @Query("DELETE FROM thermal_events WHERE timestampMillis < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int
}

@Dao
interface ChargingSessionDao {

    @Insert
    suspend fun insert(session: ChargingSessionEntity): Long

    @Update
    suspend fun update(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun openSession(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY startMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE startMillis >= :fromMillis ORDER BY startMillis ASC")
    suspend fun since(fromMillis: Long): List<ChargingSessionEntity>

    @Query("SELECT COUNT(*) FROM charging_sessions")
    suspend fun count(): Long

    @Query("DELETE FROM charging_sessions WHERE endMillis IS NOT NULL AND endMillis < :beforeMillis")
    suspend fun deleteClosedBefore(beforeMillis: Long): Int
}
