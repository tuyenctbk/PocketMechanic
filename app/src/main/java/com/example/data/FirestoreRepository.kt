package com.example.data

import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticReport
import com.example.model.DiagnosticResult
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access your repair logs.")
    }

    fun observeEquipment(userId: String): Flow<List<Equipment>> = flow {
        val path = "users/$userId/equipment"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("equipment")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Equipment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDiagnostics(userId: String): Flow<List<DiagnosticReport>> = flow {
        val path = "users/$userId/diagnostics"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("diagnostics")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DiagnosticReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addEquipment(
        name: String,
        category: EquipmentCategory,
        makeModel: String,
        yearOrSerial: String,
        notes: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/equipment"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Equipment name cannot be blank." }

            val docRef = db.collection("users").document(uid).collection("equipment").document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "name" to cleanName,
                "category" to category.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanMakeModel = makeModel.trim().take(100)
            if (cleanMakeModel.isNotEmpty()) payload["makeModel"] = cleanMakeModel
            val cleanYear = yearOrSerial.trim().take(60)
            if (cleanYear.isNotEmpty()) payload["yearOrSerial"] = cleanYear
            val cleanNotes = notes.trim().take(300)
            if (cleanNotes.isNotEmpty()) payload["notes"] = cleanNotes

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEquipment(
        equipmentId: String,
        name: String,
        category: EquipmentCategory,
        makeModel: String,
        yearOrSerial: String,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/equipment/$equipmentId"
        return try {
            val uid = requireUserId()
            require(equipmentId.isNotBlank()) { "Equipment ID is required." }
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Equipment name cannot be blank." }

            val docRef = db.collection("users").document(uid).collection("equipment").document(equipmentId)
            docPath = docRef.path

            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "category" to category.name,
                    "makeModel" to makeModel.trim().take(100),
                    "yearOrSerial" to yearOrSerial.trim().take(60),
                    "notes" to notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEquipment(equipmentId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/equipment/$equipmentId"
        return try {
            val uid = requireUserId()
            require(equipmentId.isNotBlank()) { "Equipment ID is required." }
            val docRef = db.collection("users").document(uid).collection("equipment").document(equipmentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDiagnostic(
        result: DiagnosticResult,
        mode: DiagnosticMode,
        category: EquipmentCategory,
        equipmentName: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/diagnostics"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("diagnostics").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to result.title.trim().ifBlank { "Diagnostic Report" }.take(120),
                "category" to category.name,
                "mode" to mode.name,
                "equipmentName" to equipmentName.trim().take(100),
                "severity" to result.severity.name,
                "primaryCulprit" to result.primaryCulprit.trim().ifBlank { "Mechanical Inspection" }.take(200),
                "confidencePercent" to result.confidencePercent.coerceIn(0, 100).toLong(),
                "summary" to result.summary.trim().ifBlank { "Inspection summary recorded." }.take(2000),
                "safetyWarnings" to result.safetyWarnings.map { it.encode().take(300) }.filter { it.isNotEmpty() }.take(10),
                "diySteps" to result.diySteps.map { it.trim().take(400) }.filter { it.isNotEmpty() }.take(15),
                "partsTools" to result.partsTools.map { it.trim().take(120) }.filter { it.isNotEmpty() }.take(15),
                "sourceEntries" to result.sources.map { it.encode() }.take(10),
                "resolved" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleDiagnosticResolved(diagnosticId: String, resolved: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/diagnostics/$diagnosticId"
        return try {
            val uid = requireUserId()
            require(diagnosticId.isNotBlank()) { "Diagnostic ID is required." }
            val docRef = db.collection("users").document(uid).collection("diagnostics").document(diagnosticId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "resolved" to resolved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDiagnostic(diagnosticId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/diagnostics/$diagnosticId"
        return try {
            val uid = requireUserId()
            require(diagnosticId.isNotBlank()) { "Diagnostic ID is required." }
            val docRef = db.collection("users").document(uid).collection("diagnostics").document(diagnosticId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
