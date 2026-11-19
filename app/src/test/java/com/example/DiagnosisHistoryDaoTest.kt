package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DiagnosisHistoryDao
import com.example.data.local.DiagnosisHistoryRepository
import com.example.data.local.LocalEquipmentRepository
import com.example.data.local.PocketMechanicDatabase
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.GroundedSource
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarning
import com.example.model.SafetyWarningLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosisHistoryDaoTest {

    private lateinit var database: PocketMechanicDatabase
    private lateinit var dao: DiagnosisHistoryDao
    private lateinit var repository: DiagnosisHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PocketMechanicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.diagnosisHistoryDao()
        repository = DiagnosisHistoryRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordsQueriesUpdatesAndDeletesDiagnosisHistory() = runBlocking {
        val sampleResult = DiagnosticResult(
            title = "Loose Serpentine Belt Tensioner",
            primaryCulprit = "Worn serpentine belt tensioner spring causing cold-start chirp.",
            confidencePercent = 91,
            severity = DiagnosticSeverity.HIGH,
            summary = "Rhythmic high-pitched chirp synced to crankshaft RPM points to belt slip.",
            safetyWarnings = listOf(
                SafetyWarning(
                    id = "warn_0",
                    title = "Engine Off & Key Removed",
                    detail = "Ensure engine is off and keys are out before inspecting pulleys.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.MECHANICAL
                )
            ),
            diySteps = listOf("Inspect belt ribs for glazing", "Check tensioner pulley bearing play"),
            partsTools = listOf("3/8-inch breaker bar", "OEM serpentine belt"),
            sources = listOf(GroundedSource("Honda Service Bulletin", "https://example.com/civic-belt"))
        )

        val id = repository.recordDiagnosis(
            userId = "user_1",
            result = sampleResult,
            mode = DiagnosticMode.AUDIO,
            category = EquipmentCategory.VEHICLE,
            equipmentName = "2018 Honda Civic"
        )

        val all = repository.observeHistory("user_1").first()
        assertEquals(1, all.size)
        val stored = all.first()
        assertEquals("Loose Serpentine Belt Tensioner", stored.title)
        assertEquals(2, stored.diySteps.size)

        val reconstructed = stored.toDiagnosticResult()
        assertEquals(SafetyWarningLevel.DANGER, reconstructed.safetyWarnings.first().level)
        assertEquals("Honda Service Bulletin", reconstructed.sources.first().title)

        val matched = repository.observeHistory("user_1", "tensioner").first()
        assertEquals(1, matched.size)

        repository.toggleResolved(id, true)
        repository.linkCloudReport(id, "cloud_abc")
        val updated = repository.getById(id)
        assertNotNull(updated)
        assertTrue(updated!!.isResolved)
        assertEquals("cloud_abc", updated.remoteReportId)

        repository.deleteEntry(id)
        assertTrue(repository.observeHistory("user_1").first().isEmpty())
    }

    @Test
    fun managesLocalEquipmentCrudInOfflineWorkshopMode() = runBlocking {
        val equipmentRepo = LocalEquipmentRepository(database.localEquipmentDao())
        val addedId = equipmentRepo.addEquipment(
            userId = "local_user",
            name = "Family Civic",
            category = EquipmentCategory.VEHICLE,
            makeModel = "2018 Honda Civic EX",
            yearOrSerial = "2018",
            notes = "0W-20 oil"
        ).getOrThrow()

        val afterAdd = equipmentRepo.observeEquipment("local_user").first()
        assertEquals(1, afterAdd.size)
        assertEquals("Family Civic", afterAdd.first().name)
        assertEquals(EquipmentCategory.VEHICLE, afterAdd.first().categoryEnum())

        equipmentRepo.updateEquipment(
            userId = "local_user",
            equipmentId = addedId,
            name = "Daily Civic",
            category = EquipmentCategory.VEHICLE,
            makeModel = "2018 Honda Civic EX",
            yearOrSerial = "2018",
            notes = "New belt installed"
        ).getOrThrow()

        val afterUpdate = equipmentRepo.observeEquipment("local_user").first()
        assertEquals("Daily Civic", afterUpdate.first().name)
        assertEquals("New belt installed", afterUpdate.first().notes)

        equipmentRepo.deleteEquipment("local_user", addedId).getOrThrow()
        assertTrue(equipmentRepo.observeEquipment("local_user").first().isEmpty())
    }
}
