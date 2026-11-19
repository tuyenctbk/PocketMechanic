package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_events",
    indices = [
        Index(value = ["user_id", "created_at_millis"]),
        Index(value = ["event_name"])
    ]
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "event_name")
    val eventName: String,
    val category: String,
    @ColumnInfo(name = "metadata_json")
    val metadataJson: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
)
