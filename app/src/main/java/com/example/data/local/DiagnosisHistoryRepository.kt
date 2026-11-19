package com.example.data.local

import com.example.model.DiagnosticMode
import com.example.model.DiagnosticResult
import com.example.model.EquipmentCategory
import kotlinx.coroutines.flow.Flow

class DiagnosisHistoryRepository(
    private val dao: DiagnosisHistoryDao
) {
    fun observeHistory(userId: String, searchQuery: String = ""): Flow<List<DiagnosisHistoryEntity>> {
        val clean = searchQuery.trim()
        return if (clean.isEmpty()) {
            dao.observeAllByUser(userId)
        } else {
            dao.searchByUser(userId, clean)
        }
    }

    suspend fun getById(id: Long): DiagnosisHistoryEntity? = dao.getById(id)

    suspend fun recordDiagnosis(
        userId: String,
        result: DiagnosticResult,
        mode: DiagnosticMode,
        category: EquipmentCategory,
        equipmentName: String
    ): Long {
        val entity = DiagnosisHistoryEntity.fromDiagnosticResult(
            userId = userId,
            result = result,
            mode = mode,
            category = category,
            equipmentName = equipmentName
        )
        return dao.insert(entity)
    }

    suspend fun toggleResolved(id: Long, resolved: Boolean) {
        dao.updateResolved(id, resolved)
    }

    suspend fun linkCloudReport(localId: Long, remoteReportId: String) {
        dao.attachRemoteReportId(localId, remoteReportId)
    }

    suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }
}
