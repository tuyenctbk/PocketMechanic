package com.example

import com.example.ai.AiAccessRepository
import com.example.ai.AiAccessState
import com.example.ai.AiExecutionPlan
import com.example.ai.AiProviderMode
import com.example.ai.OfflineDiagnosticReference
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAccessAndFallbackTest {

    private val day100Millis = 100L * AiAccessState.DAY_MS + 1_000L
    private val day101Millis = 101L * AiAccessState.DAY_MS + 1_000L

    @Test
    fun freeUserUnderDailyLimitGetsFreeSharedPlan() {
        val state = AiAccessState(
            preferredMode = AiProviderMode.AUTO_SMART,
            dailyFreeUsedCount = 2,
            dailyFreeEpochDay = 100L
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = false,
            nowMillis = day100Millis
        )
        assertEquals(AiExecutionPlan.UseFreeShared(remainingBeforeCall = 3, dailyLimit = 5), plan)
    }

    @Test
    fun freeUserAtDailyLimitGetsQuotaChoice() {
        val state = AiAccessState(
            preferredMode = AiProviderMode.AUTO_SMART,
            dailyFreeUsedCount = 5,
            dailyFreeEpochDay = 100L
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = false,
            nowMillis = day100Millis
        )
        assertEquals(AiExecutionPlan.QuotaReachedNeedChoice(usedToday = 5, dailyLimit = 5), plan)
    }

    @Test
    fun utcDayRolloverResetsFreeQuotaAutomatically() {
        val state = AiAccessState(
            preferredMode = AiProviderMode.AUTO_SMART,
            dailyFreeUsedCount = 5,
            dailyFreeEpochDay = 100L
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = false,
            nowMillis = day101Millis
        )
        assertEquals(AiExecutionPlan.UseFreeShared(remainingBeforeCall = 5, dailyLimit = 5), plan)
    }

    @Test
    fun activeSupporterGetsBoostedDailyLimit() {
        val state = AiAccessState(
            preferredMode = AiProviderMode.FREE_SHARED,
            dailyFreeUsedCount = 5,
            dailyFreeEpochDay = 100L
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = true,
            nowMillis = day100Millis
        )
        assertEquals(AiExecutionPlan.UseFreeShared(remainingBeforeCall = 20, dailyLimit = 25), plan)
    }

    @Test
    fun configuredByokKeyRoutesUnlimitedEvenWhenFreeQuotaExhausted() {
        val sampleKey = "AIzaSyDemoTestKeyValue1234567890"
        val state = AiAccessState(
            preferredMode = AiProviderMode.AUTO_SMART,
            byokApiKey = sampleKey,
            byokModelName = AiAccessState.LITE_BYOK_MODEL,
            dailyFreeUsedCount = 25,
            dailyFreeEpochDay = 100L
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = false,
            nowMillis = day100Millis
        )
        assertEquals(
            AiExecutionPlan.UseByok(apiKey = sampleKey, modelName = AiAccessState.LITE_BYOK_MODEL),
            plan
        )
    }

    @Test
    fun offlineModeAlwaysRoutesToOfflineReference() {
        val state = AiAccessState(
            preferredMode = AiProviderMode.OFFLINE_ONLY,
            byokApiKey = "AIzaSyDemoTestKeyValue1234567890"
        )
        val plan = AiAccessRepository.resolveExecutionPlan(
            state = state,
            isActiveSupporter = true,
            nowMillis = day100Millis
        )
        assertEquals(AiExecutionPlan.UseOfflineReference, plan)
    }

    @Test
    fun maskApiKeyRedactsMiddleCharacters() {
        val masked = AiAccessRepository.maskApiKey("AIzaSyDemoTestKeyValue1234567890")
        assertTrue(masked.startsWith("AIzaS"))
        assertTrue(masked.endsWith("7890"))
        assertTrue(masked.contains("••••"))
    }

    @Test
    fun offlineReferenceMatchesObdMisfireAndHonestyLabelsResult() {
        val result = OfflineDiagnosticReference.lookup(
            category = EquipmentCategory.VEHICLE,
            equipmentName = "2018 Honda Civic",
            symptomNotes = "Check engine light flashing OBD code P0301 rough idle"
        )
        assertTrue(result.title.startsWith("Offline Ref:"))
        assertEquals(DiagnosticSeverity.HIGH, result.severity)
        assertTrue(result.diySteps.isNotEmpty())
        assertTrue(result.partsTools.isNotEmpty())
    }

    @Test
    fun offlineReferenceMatchesPlumbingWaterHammerAndWasherDrainCode() {
        val plumbing = OfflineDiagnosticReference.lookup(
            category = EquipmentCategory.PLUMBING,
            equipmentName = "Kitchen Sink",
            symptomNotes = "Loud pipe bang when washer valve shuts off"
        )
        assertTrue(plumbing.title.contains("Water Hammer", ignoreCase = true))

        val washer = OfflineDiagnosticReference.lookup(
            category = EquipmentCategory.APPLIANCE,
            equipmentName = "LG Washer",
            symptomNotes = "Flashing OE drain error code"
        )
        assertTrue(washer.title.contains("Drain", ignoreCase = true))
    }

    @Test
    fun offlineReferenceReturnsGenericChecklistWhenNoKeywordMatches() {
        val fallback = OfflineDiagnosticReference.lookup(
            category = EquipmentCategory.HVAC,
            equipmentName = "Carrier Unit",
            symptomNotes = "Unusual rattle"
        )
        assertTrue(fallback.title.startsWith("Offline Ref:"))
        assertTrue(fallback.summary.contains("Offline Quick-Reference", ignoreCase = true))
        assertTrue(fallback.diySteps.size >= 3)
    }

    @Test
    fun diagnosticParserHandlesMarkdownFormattedHeadersCleanly() {
        val raw = """
            **TITLE:** Serpentine Belt Tensioner Bearing
            ## **CULPRIT:** Worn idler pulley bearing causing rhythmic chirp
            **CONFIDENCE:** 91%
            **SEVERITY:** HIGH
            ### SUMMARY:
            High-pitched chirping increases with RPM and indicates bearing play.
            **SAFETY_WARNINGS:**
            - DANGER | THERMAL | Hot Engine | Let engine cool before touching pulleys.
            **DIY_STEPS:**
            1. Release belt tension using a 14mm breaker bar.
            2. Spin each pulley by hand to feel for grit or wobble.
            **PARTS_TOOLS:**
            - 14mm socket wrench
            - Replacement tensioner pulley
        """.trimIndent()

        val parsed = com.example.ai.MechanicAiService.parseDiagnosticResponseText(raw)
        assertEquals("Serpentine Belt Tensioner Bearing", parsed.title)
        assertEquals("Worn idler pulley bearing causing rhythmic chirp", parsed.primaryCulprit)
        assertEquals(91, parsed.confidencePercent)
        assertEquals(DiagnosticSeverity.HIGH, parsed.severity)
        assertEquals(1, parsed.safetyWarnings.size)
        assertEquals(2, parsed.diySteps.size)
        assertEquals(2, parsed.partsTools.size)
    }
}
