package com.example.data.local

import com.example.model.Equipment
import com.example.model.EquipmentCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalEquipmentRepository(
    private val dao: LocalEquipmentDao
) {
    fun observeEquipment(userId: String): Flow<List<Equipment>> =
        dao.observeForUser(userId).map { rows -> rows.map { it.toEquipment() } }

    suspend fun addEquipment(
        userId: String,
        name: String,
        category: EquipmentCategory,
        makeModel: String,
        yearOrSerial: String,
        notes: String
    ): Result<String> = runCatching {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Equipment name cannot be blank." }
        val id = "local_${UUID.randomUUID()}"
        dao.upsert(
            LocalEquipmentEntity.create(
                id = id,
                userId = userId,
                name = cleanName,
                category = category,
                makeModel = makeModel,
                yearOrSerial = yearOrSerial,
                notes = notes
            )
        )
        id
    }

    suspend fun updateEquipment(
        userId: String,
        equipmentId: String,
        name: String,
        category: EquipmentCategory,
        makeModel: String,
        yearOrSerial: String,
        notes: String
    ): Result<Unit> = runCatching {
        val cleanName = name.trim().take(80)
        require(equipmentId.isNotBlank()) { "Equipment ID is required." }
        require(cleanName.isNotEmpty()) { "Equipment name cannot be blank." }
        val existing = dao.getById(equipmentId)
        val now = System.currentTimeMillis()
        dao.upsert(
            LocalEquipmentEntity(
                id = equipmentId,
                userId = userId,
                name = cleanName,
                category = category.name,
                makeModel = makeModel.trim().take(100),
                yearOrSerial = yearOrSerial.trim().take(60),
                notes = notes.trim().take(300),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
        )
    }

    suspend fun deleteEquipment(userId: String, equipmentId: String): Result<Unit> = runCatching {
        dao.deleteById(equipmentId, userId)
    }
}
