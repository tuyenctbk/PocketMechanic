package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.hardware.AcousticSignalQuality
import com.example.hardware.AudioRecorderManager
import com.example.hardware.TorchController
import com.example.model.DiagnosticMode
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.example.ui.DiagnosisUiState
import com.example.ui.FollowUpMessage
import com.example.ui.MechanicViewModel
import com.example.ui.StreamUiState
import com.example.ui.components.AcousticCaptureProfile
import com.example.ui.components.AcousticProbeConsoleCard
import com.example.ui.components.AiQuotaChoiceCard
import com.example.ui.components.DiagnosticResultCard
import com.example.ui.components.GroundingSourcesSection
import com.example.ui.components.InlinePostDiagnosisSupportCard
import com.example.ui.components.InspectionFlashlightToggleCard
import com.example.ui.components.workshopFocusRing
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.PocketMechanicTheme
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusOkGreen

@Composable
fun AudioCaptureScreen(
    viewModel: MechanicViewModel,
    audioRecorder: AudioRecorderManager,
    torchController: TorchController? = null,
    modifier: Modifier = Modifier,
    showInlineSupportBanner: Boolean = false,
    onOpenSupportSheet: () -> Unit = {},
    onDismissSupportBanner: () -> Unit = {},
    onShareReport: ((String) -> Unit)? = null,
    onTorchToggled: (Boolean) -> Unit = {},
    onOpenAiSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRecording by audioRecorder.isRecording.collectAsStateWithLifecycle()
    val isPlaying by audioRecorder.isPlaying.collectAsStateWithLifecycle()
    val elapsedMillis by audioRecorder.elapsedMillis.collectAsStateWithLifecycle()
    val currentDb by audioRecorder.currentDb.collectAsStateWithLifecycle()
    val signalQuality by audioRecorder.signalQuality.collectAsStateWithLifecycle()
    val waveformSamples by audioRecorder.waveformSamples.collectAsStateWithLifecycle()
    val playbackProgress by audioRecorder.playbackProgress.collectAsStateWithLifecycle()
    val recordedWav by audioRecorder.recordedWavBytes.collectAsStateWithLifecycle()
    val isTorchOn by (torchController?.isTorchOn?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val isFlashAvailable by (torchController?.isFlashAvailable?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val equipmentInput by viewModel.equipmentInput.collectAsStateWithLifecycle()
    val symptomInput by viewModel.symptomInput.collectAsStateWithLifecycle()
    val diagnosisState by viewModel.diagnosisState.collectAsStateWithLifecycle()
    val followUpMessages by viewModel.followUpMessages.collectAsStateWithLifecycle()
    val isFollowUpLoading by viewModel.isFollowUpLoading.collectAsStateWithLifecycle()
    val equipmentStream by viewModel.equipmentState.collectAsStateWithLifecycle()

    var permissionError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.stopPlayback()
            if (audioRecorder.isRecording.value) {
                audioRecorder.stopRecording()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionError = null
            audioRecorder.startRecording(
                scope = scope,
                onError = { msg -> permissionError = msg }
            )
        } else {
            permissionError = context.getString(R.string.permission_mic_required)
        }
    }

    val dictationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val spoken = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                viewModel.appendSymptomChip(spoken)
                permissionError = null
            }
        }
    }

    val savedEquipment = (equipmentStream as? StreamUiState.Success<List<Equipment>>)?.data.orEmpty()
    val activeProfile = remember(selectedCategory) {
        AcousticCaptureProfile.fromCategory(selectedCategory)
    }

    AudioCaptureContent(
        activeProfile = activeProfile,
        isRecording = isRecording,
        isPlaying = isPlaying,
        elapsedMillis = elapsedMillis,
        maxDurationMs = audioRecorder.maxDurationMs,
        currentDb = currentDb,
        signalQuality = signalQuality,
        waveformSamples = waveformSamples,
        playbackProgress = playbackProgress,
        recordedBytesSize = recordedWav?.size,
        selectedCategory = selectedCategory,
        savedEquipment = savedEquipment,
        equipmentInput = equipmentInput,
        symptomInput = symptomInput,
        diagnosisState = diagnosisState,
        followUpMessages = followUpMessages,
        isFollowUpLoading = isFollowUpLoading,
        isTorchOn = isTorchOn,
        isFlashAvailable = isFlashAvailable,
        permissionError = permissionError,
        onSelectProfile = { profile -> viewModel.setCategory(profile.category) },
        onSelectCategory = viewModel::setCategory,
        onSelectEquipment = viewModel::selectEquipment,
        onEquipmentInputChange = viewModel::setEquipmentInput,
        onSymptomInputChange = viewModel::setSymptomInput,
        onAppendSymptom = viewModel::appendSymptomChip,
        onToggleTorch = {
            val controller = torchController
            if (controller == null || !controller.toggleTorch()) {
                permissionError = context.getString(R.string.torch_card_unavailable)
            } else {
                permissionError = null
                onTorchToggled(controller.isTorchOn.value)
            }
        },
        onToggleRecord = {
            if (isRecording) {
                audioRecorder.stopRecording()
            } else {
                audioRecorder.stopPlayback()
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    permissionError = null
                    audioRecorder.startRecording(
                        scope = scope,
                        onError = { msg -> permissionError = msg }
                    )
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        onTogglePlayback = {
            if (isPlaying) {
                audioRecorder.stopPlayback()
            } else {
                permissionError = null
                audioRecorder.startPlayback(
                    scope = scope,
                    onError = { msg -> permissionError = msg }
                )
            }
        },
        onClearAudio = { audioRecorder.clearRecording() },
        onAnalyzeAudio = {
            audioRecorder.stopPlayback()
            if (audioRecorder.isRecording.value) audioRecorder.stopRecording()
            viewModel.runDiagnosis(
                mode = DiagnosticMode.AUDIO,
                audioWavBytes = audioRecorder.recordedWavBytes.value,
                includeCapturedPhoto = false
            )
        },
        onSendFollowUp = viewModel::sendFollowUp,
        onSaveDiagnosis = viewModel::saveCurrentDiagnosis,
        onResetDiagnosis = {
            viewModel.resetDiagnosis()
            audioRecorder.clearRecording()
        },
        showInlineSupportBanner = showInlineSupportBanner,
        onOpenSupportSheet = onOpenSupportSheet,
        onDismissSupportBanner = onDismissSupportBanner,
        onShareReport = onShareReport,
        onOpenAiSettings = onOpenAiSettings,
        onRunOfflineFallback = {
            viewModel.runOfflineReferenceFallback(
                mode = DiagnosticMode.AUDIO,
                hasAudio = audioRecorder.recordedWavBytes.value != null,
                hasImage = false
            )
        },
        onDictateSymptoms = {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.dictate_symptoms))
            }
            runCatching { dictationLauncher.launch(speechIntent) }.onFailure {
                permissionError = "On-device voice dictation is not available on this device."
            }
        },
        modifier = modifier
    )
}

@Composable
fun AudioCaptureContent(
    activeProfile: AcousticCaptureProfile,
    isRecording: Boolean,
    isPlaying: Boolean,
    elapsedMillis: Long,
    maxDurationMs: Long,
    currentDb: Int,
    signalQuality: AcousticSignalQuality,
    waveformSamples: List<Float>,
    playbackProgress: Float,
    recordedBytesSize: Int?,
    selectedCategory: EquipmentCategory,
    savedEquipment: List<Equipment>,
    equipmentInput: String,
    symptomInput: String,
    diagnosisState: DiagnosisUiState,
    followUpMessages: List<FollowUpMessage> = emptyList(),
    isFollowUpLoading: Boolean = false,
    isTorchOn: Boolean = false,
    isFlashAvailable: Boolean = true,
    permissionError: String?,
    onSelectProfile: (AcousticCaptureProfile) -> Unit,
    onSelectCategory: (EquipmentCategory) -> Unit,
    onSelectEquipment: (Equipment) -> Unit,
    onEquipmentInputChange: (String) -> Unit,
    onSymptomInputChange: (String) -> Unit,
    onAppendSymptom: (String) -> Unit,
    onToggleTorch: () -> Unit = {},
    onToggleRecord: () -> Unit,
    onTogglePlayback: () -> Unit = {},
    onClearAudio: () -> Unit,
    onAnalyzeAudio: () -> Unit,
    onSendFollowUp: (String) -> Unit = {},
    onSaveDiagnosis: () -> Unit,
    onResetDiagnosis: () -> Unit,
    modifier: Modifier = Modifier,
    showInlineSupportBanner: Boolean = false,
    onOpenSupportSheet: () -> Unit = {},
    onDismissSupportBanner: () -> Unit = {},
    onShareReport: ((String) -> Unit)? = null,
    onOpenAiSettings: () -> Unit = {},
    onRunOfflineFallback: () -> Unit = {},
    onDictateSymptoms: () -> Unit = {}
) {
    var followUpInput by remember { mutableStateOf("") }
    val hasAudioReady = recordedBytesSize != null && recordedBytesSize > 0

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 760.dp

        val probeAndActionPane: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AcousticProbeConsoleCard(
                    activeProfile = activeProfile,
                    isRecording = isRecording,
                    isPlaying = isPlaying,
                    elapsedMillis = elapsedMillis,
                    maxDurationMs = maxDurationMs,
                    currentDb = currentDb,
                    signalQuality = signalQuality,
                    waveformSamples = waveformSamples,
                    playbackProgress = playbackProgress,
                    recordedBytesSize = recordedBytesSize,
                    onSelectProfile = onSelectProfile,
                    onToggleRecord = onToggleRecord,
                    onTogglePlayback = onTogglePlayback,
                    onClearAudio = onClearAudio
                )

                if (isFlashAvailable) {
                    InspectionFlashlightToggleCard(
                        isTorchOn = isTorchOn,
                        isFlashAvailable = isFlashAvailable,
                        onToggle = onToggleTorch
                    )
                }

                if (!permissionError.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = permissionError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        val contextAndResultPane: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Equipment & Noise Pattern Context
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.audio_context_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = DiagnosticCyan
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EquipmentCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { onSelectCategory(category) },
                                    label = { Text(category.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        if (savedEquipment.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(R.string.nav_garage)}:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                savedEquipment.forEach { eq ->
                                    FilterChip(
                                        selected = equipmentInput.contains(eq.name, ignoreCase = true),
                                        onClick = { onSelectEquipment(eq) },
                                        label = { Text(eq.name, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = equipmentInput,
                            onValueChange = onEquipmentInputChange,
                            label = { Text(stringResource(R.string.audio_make_model_label)) },
                            placeholder = { Text(activeProfile.equipmentPlaceholder) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mechanical_audio_equipment_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeProfile.symptomChips.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onAppendSymptom(tag) },
                                    label = { Text("+ $tag", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = symptomInput,
                            onValueChange = onSymptomInputChange,
                            label = { Text(stringResource(R.string.audio_symptom_label)) },
                            placeholder = { Text(activeProfile.symptomPlaceholder) },
                            minLines = 2,
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(
                                    onClick = onDictateSymptoms,
                                    modifier = Modifier.testTag("audio_dictate_symptoms_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = stringResource(R.string.dictate_symptoms),
                                        tint = DiagnosticCyan
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mechanical_audio_symptom_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // Analyze Button
                Button(
                    onClick = onAnalyzeAudio,
                    enabled = (hasAudioReady || symptomInput.isNotBlank()) &&
                        diagnosisState !is DiagnosisUiState.Analyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .workshopFocusRing(RoundedCornerShape(16.dp), focusColor = DiagnosticCyan)
                        .testTag("mechanical_audio_analyze_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = Color(0xFF281800)
                    )
                ) {
                    if (diagnosisState is DiagnosisUiState.Analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF281800),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.audio_analyzing_cta), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.audio_analyze_cta), style = MaterialTheme.typography.labelLarge)
                    }
                }

                AnimatedVisibility(visible = diagnosisState !is DiagnosisUiState.Idle) {
                    when (diagnosisState) {
                        is DiagnosisUiState.Idle -> Unit
                        is DiagnosisUiState.Analyzing -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SafetyAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = SafetyAmber
                                    )
                                    Text(
                                        text = stringResource(R.string.audio_analyzing_cta),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is DiagnosisUiState.QuotaChoice -> {
                            AiQuotaChoiceCard(
                                usedToday = diagnosisState.usedToday,
                                dailyLimit = diagnosisState.dailyLimit,
                                onConfigureByok = onOpenAiSettings,
                                onUnlockSupporter = onOpenSupportSheet,
                                onRunOfflineLookup = onRunOfflineFallback
                            )
                        }

                        is DiagnosisUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = diagnosisState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onAnalyzeAudio,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.action_retry), maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = onOpenAiSettings,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.action_byok), maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = onRunOfflineFallback,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = StatusOkGreen, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.action_offline), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        is DiagnosisUiState.Ready -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DiagnosticResultCard(
                                    result = diagnosisState.result,
                                    isSaved = diagnosisState.savedReportId != null,
                                    onSaveClick = onSaveDiagnosis,
                                    onShareClick = onShareReport,
                                    engineSource = diagnosisState.engineSource,
                                    category = diagnosisState.category
                                )

                                if (showInlineSupportBanner) {
                                    InlinePostDiagnosisSupportCard(
                                        onSupportClick = onOpenSupportSheet,
                                        onDismiss = onDismissSupportBanner
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(18.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.result_followup_header),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = DiagnosticCyan
                                        )

                                        followUpMessages.forEach { msg ->
                                            Surface(
                                                color = if (msg.isUser) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (msg.isUser) "You" else stringResource(R.string.app_name),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (msg.isUser) SafetyAmber else DiagnosticCyan
                                                    )
                                                    Text(
                                                        text = msg.text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (msg.sources.isNotEmpty()) {
                                                        GroundingSourcesSection(sources = msg.sources)
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = followUpInput,
                                                onValueChange = { followUpInput = it },
                                                placeholder = { Text(stringResource(R.string.result_followup_hint)) },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("audio_followup_input"),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val q = followUpInput.trim()
                                                    if (q.isNotEmpty()) {
                                                        followUpInput = ""
                                                        onSendFollowUp(q)
                                                    }
                                                },
                                                enabled = followUpInput.isNotBlank() && !isFollowUpLoading,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .testTag("audio_followup_send_button")
                                            ) {
                                                if (isFollowUpLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.Send,
                                                        contentDescription = stringResource(R.string.result_followup_header),
                                                        tint = DiagnosticCyan
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = onResetDiagnosis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.audio_capture_another))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    probeAndActionPane()
                }
                Box(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    contextAndResultPane()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                probeAndActionPane()
                contextAndResultPane()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioCaptureScreenPreview() {
    PocketMechanicTheme(darkTheme = true) {
        AudioCaptureContent(
            activeProfile = AcousticCaptureProfile.ENGINE_BAY,
            isRecording = true,
            isPlaying = false,
            elapsedMillis = 7_400L,
            maxDurationMs = 20_000L,
            currentDb = 78,
            signalQuality = AcousticSignalQuality.OPTIMAL,
            waveformSamples = listOf(
                0.15f, 0.25f, 0.42f, 0.78f, 0.35f, 0.22f, 0.85f, 0.60f, 0.30f,
                0.20f, 0.45f, 0.90f, 0.65f, 0.28f, 0.18f, 0.55f, 0.72f, 0.38f,
                0.24f, 0.48f, 0.82f, 0.50f, 0.22f, 0.14f, 0.68f, 0.92f, 0.44f
            ),
            playbackProgress = 0f,
            recordedBytesSize = null,
            selectedCategory = EquipmentCategory.VEHICLE,
            savedEquipment = emptyList(),
            equipmentInput = "2018 Honda Civic EX",
            symptomInput = "Rhythmic chirp on cold start",
            diagnosisState = DiagnosisUiState.Idle,
            permissionError = null,
            onSelectProfile = {},
            onSelectCategory = {},
            onSelectEquipment = {},
            onEquipmentInputChange = {},
            onSymptomInputChange = {},
            onAppendSymptom = {},
            onToggleRecord = {},
            onClearAudio = {},
            onAnalyzeAudio = {},
            onSaveDiagnosis = {},
            onResetDiagnosis = {}
        )
    }
}
