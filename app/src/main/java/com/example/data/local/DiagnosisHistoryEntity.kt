package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.GroundedSource
import com.example.model.SafetyWarning

@Entity(
    tableName = "diagnosis_history",
    indices = [
        Index(value = ["user_id", "created_at_millis"]),
        Index(value = ["category"])
    ]
)
data class DiagnosisHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "remote_report_id")
    val remoteReportId: String? = null,
    val title: String,
    val category: String,
    val mode: String,
    @ColumnInfo(name = "equipment_name")
    val equipmentName: String,
    val severity: String,
    @ColumnInfo(name = "primary_culprit")
    val primaryCulprit: String,
    @ColumnInfo(name = "confidence_percent")
    val confidencePercent: Int,
    val summary: String,
    @ColumnInfo(name = "safety_warnings")
    val safetyWarnings: List<String> = emptyList(),
    @ColumnInfo(name = "diy_steps")
    val diySteps: List<String> = emptyList(),
    @ColumnInfo(name = "parts_tools")
    val partsTools: List<String> = emptyList(),
    @ColumnInfo(name = "source_entries")
    val sourceEntries: List<String> = emptyList(),
    @ColumnInfo(name = "is_resolved")
    val isResolved: Boolean = false,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun categoryEnum(): EquipmentCategory = EquipmentCategory.fromRaw(category)
    fun modeEnum(): DiagnosticMode = DiagnosticMode.fromRaw(mode)
    fun severityEnum(): DiagnosticSeverity = DiagnosticSeverity.fromRaw(severity)

    fun toDiagnosticResult(): DiagnosticResult = DiagnosticResult(
        title = title,
        primaryCulprit = primaryCulprit,
        confidencePercent = confidencePercent,
        severity = severityEnum(),
        summary = summary,
        safetyWarnings = safetyWarnings.mapIndexed { idx, raw ->
            SafetyWarning.fromRaw(idx, raw, severityEnum())
        }.sortedByDescending { it.level.priority },
        diySteps = diySteps,
        partsTools = partsTools,
        sources = sourceEntries.mapNotNull { GroundedSource.decode(it) }
    )

    companion object {
        fun fromDiagnosticResult(
            userId: String,
            result: DiagnosticResult,
            mode: DiagnosticMode,
            category: EquipmentCategory,
            equipmentName: String,
            remoteReportId: String? = null
        ): DiagnosisHistoryEntity = DiagnosisHistoryEntity(
            userId = userId,
            remoteReportId = remoteReportId,
            title = result.title.trim().ifBlank { "Diagnostic Inspection" },
            category = category.name,
            mode = mode.name,
            equipmentName = equipmentName.trim(),
            severity = result.severity.name,
            primaryCulprit = result.primaryCulprit.trim(),
            confidencePercent = result.confidencePercent.coerceIn(0, 100),
            summary = result.summary.trim(),
            safetyWarnings = result.safetyWarnings.map { it.encode() },
            diySteps = result.diySteps,
            partsTools = result.partsTools,
            sourceEntries = result.sources.map { it.encode() },
            isResolved = false,
            createdAtMillis = System.currentTimeMillis()
        )
    }
}
