package com.example

import com.example.model.DiagnosticSeverity
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarning
import com.example.model.SafetyWarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyWarningTest {

    @Test
    fun parsesStructuredModelSafetyWarning() {
        val warning = SafetyWarning.fromRaw(
            index = 0,
            raw = "[DANGER] High Voltage Capacitor: Shut off the 240V breaker and discharge the capacitor before touching wiring.",
            fallbackSeverity = DiagnosticSeverity.HIGH
        )

        assertEquals(SafetyWarningLevel.DANGER, warning.level)
        assertEquals(SafetyHazardType.ELECTRICAL, warning.hazardType)
        assertEquals("High Voltage Capacitor", warning.title)
        assertTrue(warning.detail.contains("240V breaker"))
        assertTrue(warning.actionRequiredBeforeWork)
    }

    @Test
    fun roundTripsEncodedFirestoreString() {
        val original = SafetyWarning(
            id = "warn_1",
            title = "Support Vehicle on Jack Stands",
            detail = "Never work under a vehicle supported only by a hydraulic jack.",
            level = SafetyWarningLevel.DANGER,
            hazardType = SafetyHazardType.STRUCTURAL
        )

        val decoded = SafetyWarning.fromRaw(1, original.encode(), DiagnosticSeverity.MEDIUM)
        assertEquals(original.level, decoded.level)
        assertEquals(original.hazardType, decoded.hazardType)
        assertEquals(original.title, decoded.title)
        assertEquals(original.detail, decoded.detail)
    }

    @Test
    fun infersHazardAndLevelFromPlainText() {
        val warning = SafetyWarning.fromRaw(
            index = 2,
            raw = "Allow hot radiator coolant to cool down completely before opening the pressurized cap.",
            fallbackSeverity = DiagnosticSeverity.MEDIUM
        )

        assertEquals(SafetyWarningLevel.DANGER, warning.level)
        assertEquals(SafetyHazardType.THERMAL, warning.hazardType)
    }
}
