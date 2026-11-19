package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.google.firebase.Timestamp
import java.util.Date

@Entity(
    tableName = "local_equipment",
    indices = [
        Index(value = ["user_id", "created_at_millis"])
    ]
)
data class LocalEquipmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "make_model")
    val makeModel: String,
    @ColumnInfo(name = "year_or_serial")
    val yearOrSerial: String,
    @ColumnInfo(name = "notes")
    val notes: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toEquipment(): Equipment = Equipment(
        id = id,
        userId = userId,
        name = name,
        category = category,
        makeModel = makeModel,
        yearOrSerial = yearOrSerial,
        notes = notes,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun create(
            id: String,
            userId: String,
            name: String,
            category: EquipmentCategory,
            makeModel: String,
            yearOrSerial: String,
            notes: String,
            nowMillis: Long = System.currentTimeMillis()
        ): LocalEquipmentEntity = LocalEquipmentEntity(
            id = id,
            userId = userId,
            name = name.trim().take(80),
            category = category.name,
            makeModel = makeModel.trim().take(100),
            yearOrSerial = yearOrSerial.trim().take(60),
            notes = notes.trim().take(300),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
    }
}
