package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisHistoryDao {

    @Query("SELECT * FROM diagnosis_history WHERE user_id = :userId ORDER BY created_at_millis DESC")
    fun observeAllByUser(userId: String): Flow<List<DiagnosisHistoryEntity>>

    @Query(
        """
        SELECT * FROM diagnosis_history
        WHERE user_id = :userId
          AND (
            title LIKE '%' || :query || '%'
            OR equipment_name LIKE '%' || :query || '%'
            OR primary_culprit LIKE '%' || :query || '%'
            OR summary LIKE '%' || :query || '%'
          )
        ORDER BY created_at_millis DESC
        """
    )
    fun searchByUser(userId: String, query: String): Flow<List<DiagnosisHistoryEntity>>

    @Query("SELECT * FROM diagnosis_history WHERE user_id = :userId AND category = :category ORDER BY created_at_millis DESC")
    fun observeByCategory(userId: String, category: String): Flow<List<DiagnosisHistoryEntity>>

    @Query("SELECT * FROM diagnosis_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiagnosisHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiagnosisHistoryEntity): Long

    @Query("UPDATE diagnosis_history SET is_resolved = :resolved WHERE id = :id")
    suspend fun updateResolved(id: Long, resolved: Boolean)

    @Query("UPDATE diagnosis_history SET remote_report_id = :remoteReportId WHERE id = :id")
    suspend fun attachRemoteReportId(id: Long, remoteReportId: String)

    @Query("DELETE FROM diagnosis_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM diagnosis_history WHERE user_id = :userId")
    suspend fun clearAllForUser(userId: String)
}
