package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AppEventEntity): Long

    @Query(
        "SELECT * FROM app_events WHERE user_id = :userId ORDER BY created_at_millis DESC LIMIT :limit"
    )
    fun observeRecentEvents(userId: String, limit: Int = 50): Flow<List<AppEventEntity>>

    @Query("SELECT COUNT(*) FROM app_events WHERE user_id = :userId AND event_name = :eventName")
    suspend fun countByName(userId: String, eventName: String): Int

    @Query("SELECT COUNT(*) FROM app_events WHERE user_id = :userId")
    fun observeTotalCount(userId: String): Flow<Int>

    @Query(
        """
        DELETE FROM app_events
        WHERE user_id = :userId
          AND id NOT IN (
            SELECT id FROM app_events
            WHERE user_id = :userId
            ORDER BY created_at_millis DESC
            LIMIT :keepLatest
          )
        """
    )
    suspend fun pruneExcessEvents(userId: String, keepLatest: Int = 300)
}
