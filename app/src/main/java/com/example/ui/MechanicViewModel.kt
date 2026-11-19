package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAccessRepository
import com.example.ai.AiAccessState
import com.example.ai.AiEngineSource
import com.example.ai.AiExecutionPlan
import com.example.ai.AiProviderMode
import com.example.ai.MechanicAiService
import com.example.ai.OfflineDiagnosticReference
import com.example.data.FirestoreRepository
import com.example.data.local.DiagnosisHistoryEntity
import com.example.data.local.DiagnosisHistoryRepository
import com.example.data.local.LocalEquipmentRepository
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticReport
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.example.model.GroundedSource
import com.example.model.VoiceTurn
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

sealed interface DiagnosisUiState {
    data object Idle : DiagnosisUiState
    data object Analyzing : DiagnosisUiState
    data class Ready(
        val result: DiagnosticResult,
        val mode: DiagnosticMode,
        val category: EquipmentCategory,
        val equipmentName: String,
        val savedReportId: String? = null,
        val localHistoryId: Long? = null,
        val engineSource: AiEngineSource = AiEngineSource.FREE_SHARED_FLASH
    ) : DiagnosisUiState
    data class QuotaChoice(
        val usedToday: Int,
        val dailyLimit: Int
    ) : DiagnosisUiState
    data class Error(val message: String) : DiagnosisUiState
}

sealed interface ByokVerificationUiState {
    data object Idle : ByokVerificationUiState
    data object Testing : ByokVerificationUiState
    data class Valid(val message: String) : ByokVerificationUiState
    data class Invalid(val message: String) : ByokVerificationUiState
}

data class FollowUpMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val sources: List<GroundedSource> = emptyList()
)

interface WorkshopEventHooks {
    fun onDiagnosisStarted(mode: DiagnosticMode) {}
    fun onDiagnosisCompleted(mode: DiagnosticMode, severity: DiagnosticSeverity, confidence: Int) {}
    fun onDiagnosisFailed(reason: String) {}
    fun onReportSaved() {}
    fun onRepairResolved() {}
    fun onFollowUpCompleted() {}
    fun onHandsFreeTurnCompleted() {}
    fun onEquipmentSaved(isNew: Boolean) {}

    companion object {
        val NoOp = object : WorkshopEventHooks {}
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MechanicViewModel(
    private val repository: FirestoreRepository,
    private val historyRepository: DiagnosisHistoryRepository,
    private val aiService: MechanicAiService,
    private val currentUserId: String,
    private val localEquipmentRepository: LocalEquipmentRepository? = null,
    val isCloudAccount: Boolean = repository.isCloudAvailable,
    private val aiAccessRepository: AiAccessRepository? = null,
    isActiveSupporterFlow: Flow<Boolean> = flowOf(false),
    private val eventHooks: WorkshopEventHooks = WorkshopEventHooks.NoOp
) : ViewModel() {

    private val useCloudEquipment: Boolean
        get() = isCloudAccount && repository.isCloudAvailable

    private val isFirebaseAiReady: Boolean
        get() = runCatching { FirebaseApp.getInstance() }.isSuccess

    val aiAccessState: StateFlow<AiAccessState> =
        (aiAccessRepository?.stateFlow ?: flowOf(AiAccessState()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = AiAccessState()
            )

    val isActiveSupporter: StateFlow<Boolean> = isActiveSupporterFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = false
    )

    private val _byokVerification = MutableStateFlow<ByokVerificationUiState>(ByokVerificationUiState.Idle)
    val byokVerification: StateFlow<ByokVerificationUiState> = _byokVerification.asStateFlow()

    val equipmentState: StateFlow<StreamUiState<List<Equipment>>> =
        (if (useCloudEquipment) {
            repository.observeEquipment(currentUserId)
        } else {
            localEquipmentRepository?.observeEquipment(currentUserId) ?: flowOf(emptyList())
        })
            .map<List<Equipment>, StreamUiState<List<Equipment>>> { StreamUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing equipment", rootError)
                emit(StreamUiState.Error(rootError.message ?: "Failed to load garage equipment."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = StreamUiState.Loading
            )

    val diagnosticsState: StateFlow<StreamUiState<List<DiagnosticReport>>> =
        (if (useCloudEquipment) {
            repository.observeDiagnostics(currentUserId)
        } else {
            flowOf(emptyList())
        })
            .map<List<DiagnosticReport>, StreamUiState<List<DiagnosticReport>>> { StreamUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing diagnostics", rootError)
                emit(StreamUiState.Error(rootError.message ?: "Failed to load repair logs."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = StreamUiState.Loading
            )

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    val localHistoryState: StateFlow<List<DiagnosisHistoryEntity>> =
        _historySearchQuery
            .flatMapLatest { query ->
                historyRepository.observeHistory(currentUserId, query)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _selectedCategory = MutableStateFlow(EquipmentCategory.VEHICLE)
    val selectedCategory: StateFlow<EquipmentCategory> = _selectedCategory.asStateFlow()

    private val _equipmentInput = MutableStateFlow("")
    val equipmentInput: StateFlow<String> = _equipmentInput.asStateFlow()

    private val _symptomInput = MutableStateFlow("")
    val symptomInput: StateFlow<String> = _symptomInput.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _diagnosisState = MutableStateFlow<DiagnosisUiState>(DiagnosisUiState.Idle)
    val diagnosisState: StateFlow<DiagnosisUiState> = _diagnosisState.asStateFlow()

    private val _followUpMessages = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val followUpMessages: StateFlow<List<FollowUpMessage>> = _followUpMessages.asStateFlow()

    private val _isFollowUpLoading = MutableStateFlow(false)
    val isFollowUpLoading: StateFlow<Boolean> = _isFollowUpLoading.asStateFlow()

    private val _voiceTurns = MutableStateFlow<List<VoiceTurn>>(emptyList())
    val voiceTurns: StateFlow<List<VoiceTurn>> = _voiceTurns.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var diagnosisJob: Job? = null
    private var followUpJob: Job? = null
    private var handsFreeJob: Job? = null
    private var isSavingCurrentDiagnosis = false
    private val syncingLocalHistoryIds = mutableSetOf<Long>()

    fun setCategory(category: EquipmentCategory) {
        _selectedCategory.value = category
    }

    fun setEquipmentInput(value: String) {
        _equipmentInput.value = value.take(100)
    }

    fun selectEquipment(equipment: Equipment) {
        _selectedCategory.value = equipment.categoryEnum()
        _equipmentInput.value = listOf(equipment.name, equipment.makeModel)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" - ")
            .take(100)
    }

    fun setSymptomInput(value: String) {
        _symptomInput.value = value
    }

    fun appendSymptomChip(chipText: String) {
        val current = _symptomInput.value.trim()
        _symptomInput.value = if (current.isEmpty()) chipText else "$current; $chipText"
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun resetDiagnosis() {
        diagnosisJob?.cancel()
        followUpJob?.cancel()
        _isFollowUpLoading.value = false
        _diagnosisState.value = DiagnosisUiState.Idle
        _followUpMessages.value = emptyList()
    }

    fun setAiProviderMode(mode: AiProviderMode) {
        viewModelScope.launch {
            aiAccessRepository?.setPreferredMode(mode)
            _statusBanner.value = "AI engine set to ${mode.label}."
        }
    }

    fun clearByokVerification() {
        _byokVerification.value = ByokVerificationUiState.Idle
    }

    fun verifyAndSaveByokKey(apiKey: String, modelName: String) {
        viewModelScope.launch {
            _byokVerification.value = ByokVerificationUiState.Testing
            val check = aiService.verifyByokKey(apiKey, modelName)
            if (check.isValid) {
                aiAccessRepository?.saveByokConfig(
                    apiKey = apiKey.trim(),
                    modelName = check.resolvedModel,
                    switchToByokMode = true
                )
                _byokVerification.value = ByokVerificationUiState.Valid(check.message)
                _statusBanner.value = "Personal Gemini BYOK key saved (unlimited)."
            } else {
                _byokVerification.value = ByokVerificationUiState.Invalid(check.message)
            }
        }
    }

    fun saveByokKeyWithoutTest(apiKey: String, modelName: String) {
        val clean = apiKey.trim()
        if (clean.length < 20) {
            _byokVerification.value = ByokVerificationUiState.Invalid("Paste a valid Gemini API key first.")
            return
        }
        viewModelScope.launch {
            aiAccessRepository?.saveByokConfig(
                apiKey = clean,
                modelName = modelName,
                switchToByokMode = true
            )
            _byokVerification.value = ByokVerificationUiState.Valid("Personal key saved locally on device.")
            _statusBanner.value = "Personal Gemini BYOK key saved."
        }
    }

    fun removeByokKey() {
        viewModelScope.launch {
            aiAccessRepository?.clearByokKey()
            _byokVerification.value = ByokVerificationUiState.Idle
            _statusBanner.value = "Personal API key removed from device."
        }
    }

    fun runOfflineReferenceFallback(
        mode: DiagnosticMode,
        hasAudio: Boolean = false,
        hasImage: Boolean = false,
        noticeMessage: String? = null
    ) {
        val category = _selectedCategory.value
        val equipName = _equipmentInput.value.trim()
        val notes = _symptomInput.value.trim()
        val offlineResult = OfflineDiagnosticReference.lookup(
            category = category,
            equipmentName = equipName,
            symptomNotes = notes,
            hasAudio = hasAudio,
            hasImage = hasImage
        )
        followUpJob?.cancel()
        _isFollowUpLoading.value = false
        _followUpMessages.value = emptyList()
        viewModelScope.launch {
            val localId = runCatching {
                historyRepository.recordDiagnosis(
                    userId = currentUserId,
                    result = offlineResult,
                    mode = mode,
                    category = category,
                    equipmentName = equipName
                )
            }.getOrNull()

            _diagnosisState.value = DiagnosisUiState.Ready(
                result = offlineResult,
                mode = mode,
                category = category,
                equipmentName = equipName,
                localHistoryId = localId,
                engineSource = AiEngineSource.OFFLINE_REFERENCE
            )
            if (!noticeMessage.isNullOrBlank()) {
                _statusBanner.value = noticeMessage
            }
            eventHooks.onDiagnosisCompleted(
                mode = mode,
                severity = offlineResult.severity,
                confidence = offlineResult.confidencePercent
            )
        }
    }

    fun runDiagnosis(
        mode: DiagnosticMode,
        audioWavBytes: ByteArray?,
        includeCapturedPhoto: Boolean = true
    ) {
        val bitmap = if (includeCapturedPhoto) _capturedBitmap.value else null
        val notes = _symptomInput.value.trim()
        val equipName = _equipmentInput.value.trim()
        val category = _selectedCategory.value

        if (audioWavBytes == null && bitmap == null && notes.isEmpty()) {
            _statusBanner.value = "Record a sound, capture/pick a photo, or enter symptoms first."
            return
        }

        val resolvedMode = when {
            audioWavBytes != null -> DiagnosticMode.AUDIO
            bitmap != null -> DiagnosticMode.VISION
            else -> DiagnosticMode.TEXT
        }

        diagnosisJob?.cancel()
        diagnosisJob = viewModelScope.launch {
            val access = aiAccessRepository?.currentState() ?: aiAccessState.value
            val plan = AiAccessRepository.resolveExecutionPlan(
                state = access,
                isActiveSupporter = isActiveSupporter.value
            )

            when (plan) {
                is AiExecutionPlan.QuotaReachedNeedChoice -> {
                    _diagnosisState.value = DiagnosisUiState.QuotaChoice(
                        usedToday = plan.usedToday,
                        dailyLimit = plan.dailyLimit
                    )
                    return@launch
                }

                AiExecutionPlan.UseOfflineReference -> {
                    runOfflineReferenceFallback(
                        mode = resolvedMode,
                        hasAudio = audioWavBytes != null,
                        hasImage = bitmap != null
                    )
                    return@launch
                }

                is AiExecutionPlan.UseFreeShared -> {
                    if (!isFirebaseAiReady && access.preferredMode == AiProviderMode.AUTO_SMART) {
                        runOfflineReferenceFallback(
                            mode = resolvedMode,
                            hasAudio = audioWavBytes != null,
                            hasImage = bitmap != null,
                            noticeMessage = "Offline Reference used — add a personal BYOK Gemini key in AI Engine for cloud AI."
                        )
                        return@launch
                    }
                }

                is AiExecutionPlan.UseByok -> Unit
            }

            _diagnosisState.value = DiagnosisUiState.Analyzing
            _followUpMessages.value = emptyList()
            eventHooks.onDiagnosisStarted(resolvedMode)
            try {
                val byokKey = (plan as? AiExecutionPlan.UseByok)?.apiKey
                val byokModel = (plan as? AiExecutionPlan.UseByok)?.modelName
                    ?: AiAccessState.DEFAULT_BYOK_MODEL

                val outcome = aiService.diagnose(
                    category = category,
                    equipmentName = equipName,
                    symptomNotes = notes,
                    audioWavBytes = audioWavBytes,
                    imageBitmap = bitmap,
                    byokApiKey = byokKey,
                    byokModelName = byokModel
                )

                if (plan is AiExecutionPlan.UseFreeShared) {
                    aiAccessRepository?.recordFreeSharedCall()
                }

                val localId = runCatching {
                    historyRepository.recordDiagnosis(
                        userId = currentUserId,
                        result = outcome.result,
                        mode = resolvedMode,
                        category = category,
                        equipmentName = equipName
                    )
                }.getOrNull()

                _diagnosisState.value = DiagnosisUiState.Ready(
                    result = outcome.result,
                    mode = resolvedMode,
                    category = category,
                    equipmentName = equipName,
                    localHistoryId = localId,
                    engineSource = outcome.engineSource
                )
                eventHooks.onDiagnosisCompleted(
                    mode = resolvedMode,
                    severity = outcome.result.severity,
                    confidence = outcome.result.confidencePercent
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Diagnosis timed out", e)
                eventHooks.onDiagnosisFailed("timeout")
                _diagnosisState.value = DiagnosisUiState.Error(
                    "Diagnosis timed out after 60 seconds. Retry, use your personal BYOK key, or run Offline Reference."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Diagnosis failed", e)
                eventHooks.onDiagnosisFailed(e.localizedMessage ?: "error")
                _diagnosisState.value = DiagnosisUiState.Error(
                    e.localizedMessage ?: "Failed to complete diagnosis."
                )
            }
        }
    }

    fun sendFollowUp(question: String) {
        val clean = question.trim()
        val current = _diagnosisState.value as? DiagnosisUiState.Ready ?: return
        if (clean.isEmpty() || _isFollowUpLoading.value) return

        followUpJob?.cancel()
        followUpJob = viewModelScope.launch {
            _isFollowUpLoading.value = true
            try {
                val access = aiAccessRepository?.currentState() ?: aiAccessState.value
                val plan = AiAccessRepository.resolveExecutionPlan(access, isActiveSupporter.value)

                if (plan is AiExecutionPlan.QuotaReachedNeedChoice) {
                    _statusBanner.value = "Daily free AI quota reached. Add your free BYOK Gemini key or unlock Supporter."
                    return@launch
                }
                if (plan is AiExecutionPlan.UseOfflineReference) {
                    _statusBanner.value = "Follow-up chat requires Free Shared AI or your personal BYOK Gemini key."
                    return@launch
                }

                val userMessage = FollowUpMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = clean
                )
                _followUpMessages.value = _followUpMessages.value + userMessage

                val byokKey = (plan as? AiExecutionPlan.UseByok)?.apiKey
                val byokModel = (plan as? AiExecutionPlan.UseByok)?.modelName
                    ?: AiAccessState.DEFAULT_BYOK_MODEL

                val (reply, sources) = aiService.askFollowUp(
                    reportContext = current.result,
                    equipmentName = current.equipmentName,
                    question = clean,
                    byokApiKey = byokKey,
                    byokModelName = byokModel
                )
                if (plan is AiExecutionPlan.UseFreeShared) {
                    aiAccessRepository?.recordFreeSharedCall()
                }
                _followUpMessages.value = _followUpMessages.value + FollowUpMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = reply,
                    sources = sources
                )
                eventHooks.onFollowUpCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Follow-up failed", e)
                _statusBanner.value = e.localizedMessage ?: "Follow-up question failed."
            } finally {
                _isFollowUpLoading.value = false
            }
        }
    }

    fun saveCurrentDiagnosis() {
        val current = _diagnosisState.value as? DiagnosisUiState.Ready ?: return
        if (current.savedReportId != null || isSavingCurrentDiagnosis) return

        if (!useCloudEquipment) {
            _statusBanner.value = "Saved in Device History. Sign in with Google to sync to Cloud Repair Log."
            return
        }

        isSavingCurrentDiagnosis = true
        viewModelScope.launch {
            try {
                val res = repository.saveDiagnostic(
                    result = current.result,
                    mode = current.mode,
                    category = current.category,
                    equipmentName = current.equipmentName
                )
                res.onSuccess { reportId ->
                    current.localHistoryId?.let { localId ->
                        runCatching { historyRepository.linkCloudReport(localId, reportId) }
                    }
                    val latest = _diagnosisState.value
                    if (latest is DiagnosisUiState.Ready &&
                        latest.localHistoryId == current.localHistoryId &&
                        latest.result == current.result
                    ) {
                        _diagnosisState.value = latest.copy(savedReportId = reportId)
                    }
                    _statusBanner.value = "Saved to your Cloud Repair Log."
                    eventHooks.onReportSaved()
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not save repair log."
                }
            } finally {
                isSavingCurrentDiagnosis = false
            }
        }
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun toggleLocalHistoryResolved(localId: Long, resolved: Boolean, remoteReportId: String? = null) {
        viewModelScope.launch {
            historyRepository.toggleResolved(localId, resolved)
            if (!remoteReportId.isNullOrBlank()) {
                repository.toggleDiagnosticResolved(remoteReportId, resolved)
            }
            if (resolved) eventHooks.onRepairResolved()
        }
    }

    fun deleteLocalHistoryEntry(localId: Long) {
        viewModelScope.launch {
            historyRepository.deleteEntry(localId)
        }
    }

    fun syncLocalHistoryToCloud(entry: DiagnosisHistoryEntity) {
        if (!entry.remoteReportId.isNullOrBlank()) return
        if (!syncingLocalHistoryIds.add(entry.id)) return
        viewModelScope.launch {
            try {
                val res = repository.saveDiagnostic(
                    result = entry.toDiagnosticResult(),
                    mode = entry.modeEnum(),
                    category = entry.categoryEnum(),
                    equipmentName = entry.equipmentName
                )
                res.onSuccess { reportId ->
                    historyRepository.linkCloudReport(entry.id, reportId)
                    if (entry.isResolved) {
                        repository.toggleDiagnosticResolved(reportId, true)
                    }
                    _statusBanner.value = "Synced diagnosis to Cloud Repair Log."
                    eventHooks.onReportSaved()
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not sync to cloud."
                }
            } finally {
                syncingLocalHistoryIds.remove(entry.id)
            }
        }
    }

    fun sendHandsFreeTurn(
        spokenText: String?,
        audioWavBytes: ByteArray?,
        onReplyReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUserText = spokenText?.trim().orEmpty()
        if (cleanUserText.isEmpty() && (audioWavBytes == null || audioWavBytes.isEmpty())) {
            onError("No voice or mechanical sound captured.")
            return
        }

        val displayUserLine = cleanUserText.ifBlank { "[Audio Clip Sent to PocketMechanic]" }

        handsFreeJob?.cancel()
        handsFreeJob = viewModelScope.launch {
            try {
                val access = aiAccessRepository?.currentState() ?: aiAccessState.value
                val plan = AiAccessRepository.resolveExecutionPlan(access, isActiveSupporter.value)
                if (plan is AiExecutionPlan.QuotaReachedNeedChoice) {
                    onError("Daily free AI allowance reached (${plan.usedToday}/${plan.dailyLimit}). Add your free Gemini BYOK key in AI Engine settings for unlimited voice help.")
                    return@launch
                }

                // Snapshot prior turns BEFORE appending current turn so current input isn't duplicated in prompt
                val priorHistoryLines = _voiceTurns.value.takeLast(6).map { turn ->
                    if (turn.isUser) "User: ${turn.text}" else "PocketMechanic: ${turn.text}"
                }

                _voiceTurns.value = _voiceTurns.value + VoiceTurn(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = displayUserLine
                )

                if (plan is AiExecutionPlan.UseOfflineReference ||
                    (plan is AiExecutionPlan.UseFreeShared && !isFirebaseAiReady && access.preferredMode == AiProviderMode.AUTO_SMART)
                ) {
                    val offline = OfflineDiagnosticReference.lookup(
                        category = _selectedCategory.value,
                        equipmentName = _equipmentInput.value,
                        symptomNotes = cleanUserText,
                        hasAudio = audioWavBytes != null
                    )
                    val spokenOffline = "${offline.title}. ${offline.primaryCulprit} ${offline.diySteps.firstOrNull().orEmpty()}"
                    _voiceTurns.value = _voiceTurns.value + VoiceTurn(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = spokenOffline
                    )
                    eventHooks.onHandsFreeTurnCompleted()
                    onReplyReady(spokenOffline)
                    return@launch
                }

                val byokKey = (plan as? AiExecutionPlan.UseByok)?.apiKey
                val byokModel = (plan as? AiExecutionPlan.UseByok)?.modelName
                    ?: AiAccessState.DEFAULT_BYOK_MODEL

                val (reply, sources) = aiService.handsFreeTurn(
                    equipmentContext = _equipmentInput.value,
                    historyLines = priorHistoryLines,
                    spokenText = cleanUserText.ifBlank { null },
                    audioWavBytes = audioWavBytes,
                    byokApiKey = byokKey,
                    byokModelName = byokModel
                )
                if (plan is AiExecutionPlan.UseFreeShared) {
                    aiAccessRepository?.recordFreeSharedCall()
                }
                _voiceTurns.value = _voiceTurns.value + VoiceTurn(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = reply,
                    sources = sources
                )
                eventHooks.onHandsFreeTurnCompleted()
                onReplyReady(reply)
            } catch (e: TimeoutCancellationException) {
                onError("Assistant timed out. Tap to speak again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hands-free turn failed", e)
                onError(e.localizedMessage ?: "Hands-free request failed.")
            }
        }
    }

    fun cancelHandsFreeTurn() {
        handsFreeJob?.cancel()
        handsFreeJob = null
    }

    fun clearVoiceTurns() {
        cancelHandsFreeTurn()
        _voiceTurns.value = emptyList()
    }

    fun toggleDiagnosticResolved(diagnosticId: String, resolved: Boolean) {
        viewModelScope.launch {
            repository.toggleDiagnosticResolved(diagnosticId, resolved)
                .onSuccess { if (resolved) eventHooks.onRepairResolved() }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Update failed." }
        }
    }

    fun deleteDiagnostic(diagnosticId: String) {
        viewModelScope.launch {
            repository.deleteDiagnostic(diagnosticId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    fun saveEquipment(
        editingId: String?,
        name: String,
        category: EquipmentCategory,
        makeModel: String,
        yearOrSerial: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = if (useCloudEquipment) {
                if (editingId == null) {
                    repository.addEquipment(name, category, makeModel, yearOrSerial, notes).map { }
                } else {
                    repository.updateEquipment(editingId, name, category, makeModel, yearOrSerial, notes)
                }
            } else {
                val localRepo = localEquipmentRepository
                if (localRepo == null) {
                    Result.failure(IllegalStateException("Local garage storage unavailable."))
                } else if (editingId == null) {
                    localRepo.addEquipment(currentUserId, name, category, makeModel, yearOrSerial, notes).map { }
                } else {
                    localRepo.updateEquipment(currentUserId, editingId, name, category, makeModel, yearOrSerial, notes)
                }
            }
            result.onSuccess {
                eventHooks.onEquipmentSaved(editingId == null)
                _statusBanner.value = if (editingId == null) "Added to Garage." else "Updated equipment."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save equipment."
            }
        }
    }

    fun deleteEquipment(equipmentId: String) {
        viewModelScope.launch {
            val result = if (useCloudEquipment) {
                repository.deleteEquipment(equipmentId)
            } else {
                localEquipmentRepository?.deleteEquipment(currentUserId, equipmentId)
                    ?: Result.failure(IllegalStateException("Local garage storage unavailable."))
            }
            result.onFailure { _statusBanner.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    private companion object {
        const val TAG = "MechanicVM"
    }
}
