package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PersonTrackRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonTrackingDao {

    @Query("SELECT * FROM person_track_records ORDER BY timestamp DESC")
    fun getAllTrackRecords(): Flow<List<PersonTrackRecord>>

    @Query("SELECT * FROM person_track_records WHERE dateKey = :dateKey ORDER BY timestamp DESC")
    fun getRecordsForDate(dateKey: String): Flow<List<PersonTrackRecord>>

    @Query("SELECT * FROM person_track_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): PersonTrackRecord?

    @Query("SELECT SUM(steps) FROM person_track_records WHERE dateKey = :dateKey")
    fun getTodayTotalSteps(dateKey: String): Flow<Int?>

    @Query("SELECT SUM(distanceMeters) FROM person_track_records WHERE dateKey = :dateKey")
    fun getTodayTotalDistance(dateKey: String): Flow<Double?>

    @Query("SELECT SUM(caloriesBurned) FROM person_track_records WHERE dateKey = :dateKey")
    fun getTodayTotalCalories(dateKey: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PersonTrackRecord): Long

    @Delete
    suspend fun deleteRecord(record: PersonTrackRecord)

    @Query("DELETE FROM person_track_records")
    suspend fun clearAll()
}
