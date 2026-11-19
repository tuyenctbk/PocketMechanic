package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EquipmentCategory(val displayName: String) {
    VEHICLE("Vehicle / Engine"),
    APPLIANCE("Appliance"),
    PLUMBING("Plumbing"),
    HVAC("HVAC / Heating"),
    YARD_TOOL("Yard & Power Tools"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): EquipmentCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class DiagnosticMode(val displayName: String) {
    AUDIO("Audio Probe"),
    VISION("Visual Scan"),
    VOICE("Hands-Free Voice"),
    TEXT("Code / Symptom");

    companion object {
        fun fromRaw(raw: String?): DiagnosticMode =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class DiagnosticSeverity(val label: String) {
    LOW("Minor / Monitor"),
    MEDIUM("DIY Fixable"),
    HIGH("Urgent Attention"),
    CRITICAL("Safety Hazard");

    companion object {
        fun fromRaw(raw: String?): DiagnosticSeverity =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class SafetyWarningLevel(val badgeText: String, val priority: Int) {
    DANGER("DANGER • STOP FIRST", 3),
    WARNING("WARNING • HAZARD", 2),
    CAUTION("CAUTION • PRECAUTION", 1);

    companion object {
        fun fromRaw(raw: String?): SafetyWarningLevel {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { normalized.contains(it.name) } ?: WARNING
        }
    }
}

enum class SafetyHazardType(val label: String) {
    ELECTRICAL("Electrical / Power"),
    THERMAL("Hot Surface / Burn"),
    PRESSURE("Pressurized / Steam"),
    MECHANICAL("Moving Parts / Pinch"),
    CHEMICAL("Fuel / Fumes / Coolant"),
    STRUCTURAL("Lift / Support Stand"),
    GENERAL("General Safety");

    companion object {
        fun inferFromText(text: String): SafetyHazardType {
            val lower = text.lowercase()
            return when {
                listOf("power", "breaker", "unplug", "volt", "shock", "battery", "fuse", "capacitor", "electrical", "outlet", "terminal").any { it in lower } -> ELECTRICAL
                listOf("hot", "cool", "exhaust", "manifold", "radiator", "burn", "heat", "scald", "temperature").any { it in lower } -> THERMAL
                listOf("pressure", "pressurized", "valve", "shutoff", "steam", "refrigerant", "psi", "bleed", "water supply").any { it in lower } -> PRESSURE
                listOf("jack", "stand", "chock", "lift", "support", "crush", "wheel").any { it in lower } -> STRUCTURAL
                listOf("fuel", "gas", "fume", "toxic", "vent", "chemical", "coolant", "acid", "oil", "refrigerant", "leak").any { it in lower } -> CHEMICAL
                listOf("belt", "pulley", "blade", "fan", "spin", "pinch", "rotating", "spark plug", "gear").any { it in lower } -> MECHANICAL
                else -> GENERAL
            }
        }
    }
}

data class SafetyWarning(
    val id: String,
    val title: String,
    val detail: String,
    val level: SafetyWarningLevel = SafetyWarningLevel.WARNING,
    val hazardType: SafetyHazardType = SafetyHazardType.inferFromText("$title $detail"),
    val actionRequiredBeforeWork: Boolean = true
) {
    fun encode(): String =
        "${level.name}|${hazardType.name}|${title.replace("|", "/").take(70)}|${detail.replace("|", "/").take(200)}"

    companion object {
        fun fromRaw(
            index: Int,
            raw: String,
            fallbackSeverity: DiagnosticSeverity = DiagnosticSeverity.MEDIUM
        ): SafetyWarning {
            val trimmed = raw.trim()
            val encodedParts = trimmed.split("|", limit = 4)
            if (encodedParts.size == 4 && SafetyWarningLevel.entries.any { it.name == encodedParts[0] }) {
                val lvl = SafetyWarningLevel.fromRaw(encodedParts[0])
                val hz = SafetyHazardType.entries.firstOrNull { it.name == encodedParts[1] }
                    ?: SafetyHazardType.inferFromText("${encodedParts[2]} ${encodedParts[3]}")
                val parsedTitle = encodedParts[2].trim().ifBlank { hz.label }
                val parsedDetail = encodedParts[3].trim().ifBlank { parsedTitle }
                return SafetyWarning(
                    id = "warn_$index",
                    title = parsedTitle,
                    detail = parsedDetail,
                    level = lvl,
                    hazardType = hz,
                    actionRequiredBeforeWork = lvl != SafetyWarningLevel.CAUTION
                )
            }

            var working = trimmed
            var detectedLevel: SafetyWarningLevel? = null
            val bracketMatch = Regex("^\\[(DANGER|WARNING|CAUTION)]\\s*", RegexOption.IGNORE_CASE).find(working)
            if (bracketMatch != null) {
                detectedLevel = SafetyWarningLevel.fromRaw(bracketMatch.groupValues[1])
                working = working.removeRange(bracketMatch.range).trim()
            } else {
                val prefixMatch = Regex("^(DANGER|WARNING|CAUTION)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE).find(working)
                if (prefixMatch != null) {
                    detectedLevel = SafetyWarningLevel.fromRaw(prefixMatch.groupValues[1])
                    working = working.removeRange(prefixMatch.range).trim()
                }
            }

            val hazard = SafetyHazardType.inferFromText(working)
            val inferredLevel = detectedLevel ?: when {
                fallbackSeverity == DiagnosticSeverity.CRITICAL -> SafetyWarningLevel.DANGER
                listOf(
                    "high voltage", "capacitor", "gas leak", "jack stand", "carbon monoxide",
                    "fire", "explosion", "pressurized", "scald", "breaker", "disconnect power"
                ).any { it in working.lowercase() } -> SafetyWarningLevel.DANGER
                fallbackSeverity == DiagnosticSeverity.HIGH -> SafetyWarningLevel.WARNING
                else -> SafetyWarningLevel.CAUTION
            }

            val colonSplit = working.split(":", limit = 2)
            val (parsedTitle, parsedDetail) = if (colonSplit.size == 2 && colonSplit[0].trim().length in 4..55) {
                colonSplit[0].trim() to colonSplit[1].trim()
            } else {
                hazard.label to working
            }

            return SafetyWarning(
                id = "warn_$index",
                title = parsedTitle,
                detail = parsedDetail.ifBlank { working },
                level = inferredLevel,
                hazardType = hazard,
                actionRequiredBeforeWork = inferredLevel != SafetyWarningLevel.CAUTION
            )
        }
    }
}

data class GroundedSource(
    val title: String,
    val uri: String
) {
    fun encode(): String = "${title.take(80)}||${uri.take(260)}"

    companion object {
        fun decode(raw: String): GroundedSource? {
            val parts = raw.split("||", limit = 2)
            val uri = parts.getOrNull(1)?.trim().orEmpty()
            if (uri.isBlank()) return null
            val title = parts.getOrNull(0)?.trim().orEmpty().ifBlank { uri }
            return GroundedSource(title = title, uri = uri)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Equipment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = EquipmentCategory.OTHER.name,
    val makeModel: String = "",
    val yearOrSerial: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): EquipmentCategory = EquipmentCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class DiagnosticReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = EquipmentCategory.OTHER.name,
    val mode: String = DiagnosticMode.TEXT.name,
    val equipmentName: String = "",
    val severity: String = DiagnosticSeverity.MEDIUM.name,
    val primaryCulprit: String = "",
    val confidencePercent: Long = 0L,
    val summary: String = "",
    val safetyWarnings: List<String> = emptyList(),
    val diySteps: List<String> = emptyList(),
    val partsTools: List<String> = emptyList(),
    val sourceEntries: List<String> = emptyList(),
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): EquipmentCategory = EquipmentCategory.fromRaw(category)
    fun modeEnum(): DiagnosticMode = DiagnosticMode.fromRaw(mode)
    fun severityEnum(): DiagnosticSeverity = DiagnosticSeverity.fromRaw(severity)
    fun parsedSources(): List<GroundedSource> = sourceEntries.mapNotNull { GroundedSource.decode(it) }
    fun parsedSafetyWarnings(): List<SafetyWarning> =
        safetyWarnings.mapIndexed { idx, raw -> SafetyWarning.fromRaw(idx, raw, severityEnum()) }
            .sortedByDescending { it.level.priority }
}

data class DiagnosticResult(
    val title: String,
    val primaryCulprit: String,
    val confidencePercent: Int,
    val severity: DiagnosticSeverity,
    val summary: String,
    val safetyWarnings: List<SafetyWarning>,
    val diySteps: List<String>,
    val partsTools: List<String>,
    val sources: List<GroundedSource>,
    val searchEntryPointHtml: String? = null
)

data class VoiceTurn(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val sources: List<GroundedSource> = emptyList()
)
