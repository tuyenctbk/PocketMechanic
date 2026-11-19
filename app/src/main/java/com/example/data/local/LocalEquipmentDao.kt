package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalEquipmentDao {

    @Query("SELECT * FROM local_equipment WHERE user_id = :userId ORDER BY created_at_millis DESC")
    fun observeForUser(userId: String): Flow<List<LocalEquipmentEntity>>

    @Query("SELECT * FROM local_equipment WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalEquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalEquipmentEntity)

    @Query("DELETE FROM local_equipment WHERE id = :id AND user_id = :userId")
    suspend fun deleteById(id: String, userId: String)
}
