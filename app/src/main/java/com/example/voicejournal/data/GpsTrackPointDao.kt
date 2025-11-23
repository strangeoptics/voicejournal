package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTrackPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GpsTrackPoint)

    @Query("SELECT * FROM gps_track_points WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis ORDER BY timestamp ASC")
    fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>>

}