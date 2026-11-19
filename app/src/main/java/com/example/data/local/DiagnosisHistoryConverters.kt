package com.example.data.local

import androidx.room.TypeConverter

class DiagnosisHistoryConverters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return buildString {
            list.forEach { item ->
                append(item.length)
                append(':')
                append(item)
            }
        }
    }

    @TypeConverter
    fun toStringList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var idx = 0
        while (idx < raw.length) {
            val colon = raw.indexOf(':', startIndex = idx)
            if (colon == -1) break
            val length = raw.substring(idx, colon).toIntOrNull() ?: break
            val start = colon + 1
            val end = (start + length).coerceAtMost(raw.length)
            result.add(raw.substring(start, end))
            idx = end
        }
        return result
    }
}
