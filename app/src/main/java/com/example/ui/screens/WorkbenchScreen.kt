package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.speech.RecognizerIntent
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.hardware.AudioRecorderManager
import com.example.hardware.TorchController
import com.example.model.DiagnosticMode
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.example.ui.DiagnosisUiState
import com.example.ui.MechanicViewModel
import com.example.ui.StreamUiState
import com.example.ui.components.AcousticWaveformCanvas
import com.example.ui.components.AiQuotaChoiceCard
import com.example.ui.components.DiagnosticResultCard
import com.example.ui.components.GroundingSourcesSection
import com.example.ui.components.InlinePostDiagnosisSupportCard
import com.example.ui.components.InspectionFlashlightToggleCard
import com.example.ui.components.MechanicalCameraViewfinder
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkbenchScreen(
    viewModel: MechanicViewModel,
    audioRecorder: AudioRecorderManager,
    torchController: TorchController,
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

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val equipmentInput by viewModel.equipmentInput.collectAsStateWithLifecycle()
    val symptomInput by viewModel.symptomInput.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val diagnosisState by viewModel.diagnosisState.collectAsStateWithLifecycle()
    val followUpMessages by viewModel.followUpMessages.collectAsStateWithLifecycle()
    val isFollowUpLoading by viewModel.isFollowUpLoading.collectAsStateWithLifecycle()
    val equipmentStream by viewModel.equipmentState.collectAsStateWithLifecycle()

    val isRecording by audioRecorder.isRecording.collectAsStateWithLifecycle()
    val isPlaying by audioRecorder.isPlaying.collectAsStateWithLifecycle()
    val elapsedSeconds by audioRecorder.elapsedSeconds.collectAsStateWithLifecycle()
    val waveformSamples by audioRecorder.waveformSamples.collectAsStateWithLifecycle()
    val recordedWav by audioRecorder.recordedWavBytes.collectAsStateWithLifecycle()
    val isTorchOn by torchController.isTorchOn.collectAsStateWithLifecycle()
    val isFlashAvailable by torchController.isFlashAvailable.collectAsStateWithLifecycle()

    var activeMode by remember { mutableStateOf(DiagnosticMode.VISION) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var localNotice by remember { mutableStateOf<String?>(null) }
    var followUpInput by remember { mutableStateOf("") }

    BackHandler(enabled = isCameraOpen) {
        isCameraOpen = false
    }

    DisposableEffect(Unit) {
        onDispose {
            isCameraOpen = false
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
            localNotice = null
            audioRecorder.startRecording(
                scope = scope,
                onError = { msg -> localNotice = msg }
            )
        } else {
            localNotice = context.getString(R.string.permission_mic_required)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            localNotice = null
            isCameraOpen = true
        } else {
            localNotice = context.getString(R.string.permission_camera_required)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, uri) }
                if (bmp != null) {
                    viewModel.setCapturedBitmap(bmp)
                    isCameraOpen = false
                    localNotice = null
                } else {
                    localNotice = "Could not load selected image."
                }
            }
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
                localNotice = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Equipment & Category Context Card
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
                        text = stringResource(R.string.workbench_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EquipmentCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.setCategory(category) },
                                label = {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    val savedEquipment = (equipmentStream as? StreamUiState.Success<List<Equipment>>)?.data.orEmpty()
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
                            savedEquipment.forEach { item ->
                                FilterChip(
                                    selected = equipmentInput.contains(item.name, ignoreCase = true),
                                    onClick = { viewModel.selectEquipment(item) },
                                    label = { Text(item.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = equipmentInput,
                        onValueChange = { viewModel.setEquipmentInput(it) },
                        label = { Text(stringResource(R.string.workbench_make_model_label)) },
                        placeholder = { Text("e.g., 2018 Honda Civic, LG WM3900HWA, Kohler 20hp") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("equipment_model_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Capture Mode Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    DiagnosticMode.AUDIO to stringResource(R.string.workbench_mode_audio),
                    DiagnosticMode.VISION to stringResource(R.string.workbench_mode_camera),
                    DiagnosticMode.TEXT to stringResource(R.string.workbench_mode_code)
                )
                tabs.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = activeMode == mode,
                        onClick = {
                            activeMode = mode
                            if (mode != DiagnosticMode.VISION) isCameraOpen = false
                            if (mode != DiagnosticMode.AUDIO && audioRecorder.isRecording.value) {
                                audioRecorder.stopRecording()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        icon = {
                            val icon = when (mode) {
                                DiagnosticMode.AUDIO -> Icons.Rounded.GraphicEq
                                DiagnosticMode.VISION -> Icons.Rounded.CameraAlt
                                else -> Icons.Rounded.EditNote
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("mode_tab_${mode.name.lowercase()}")
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Hardware Flashlight Toggle for dark engine bays & under-sink inspections
            InspectionFlashlightToggleCard(
                isTorchOn = isTorchOn,
                isFlashAvailable = isFlashAvailable,
                onToggle = {
                    val ok = torchController.toggleTorch()
                    if (!ok) {
                        localNotice = context.getString(R.string.torch_card_unavailable)
                    } else {
                        localNotice = null
                        onTorchToggled(torchController.isTorchOn.value)
                    }
                }
            )

            // Active Capture Module
            AnimatedContent(
                targetState = activeMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                        slideInVertically(animationSpec = tween(200)) { it / 24 })
                        .togetherWith(fadeOut(animationSpec = tween(140)))
                },
                label = "workbench_mode_panel"
            ) { mode ->
                when (mode) {
                    DiagnosticMode.AUDIO -> {
                        AudioProbePanel(
                            isRecording = isRecording,
                            isPlaying = isPlaying,
                            elapsedSeconds = elapsedSeconds,
                            waveformSamples = waveformSamples,
                            hasAudioReady = recordedWav != null,
                            onToggleRecord = {
                                if (isRecording) {
                                    audioRecorder.stopRecording()
                                } else {
                                    audioRecorder.stopPlayback()
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        localNotice = null
                                        audioRecorder.startRecording(
                                            scope = scope,
                                            onError = { msg -> localNotice = msg }
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
                                    localNotice = null
                                    audioRecorder.startPlayback(
                                        scope = scope,
                                        onError = { msg -> localNotice = msg }
                                    )
                                }
                            },
                            onClearAudio = { audioRecorder.clearRecording() },
                            onSymptomChipClick = { chip -> viewModel.appendSymptomChip(chip) }
                        )
                    }

                    DiagnosticMode.VISION -> {
                        VisionInspectorPanel(
                            capturedBitmap = capturedBitmap,
                            isCameraOpen = isCameraOpen,
                            isTorchOn = isTorchOn,
                            onOpenCamera = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    localNotice = null
                                    isCameraOpen = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onCloseCamera = { isCameraOpen = false },
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onToggleTorch = {
                                val ok = torchController.toggleTorch()
                                if (ok) {
                                    localNotice = null
                                    onTorchToggled(torchController.isTorchOn.value)
                                } else {
                                    localNotice = context.getString(R.string.torch_card_unavailable)
                                }
                            },
                            onPhotoCaptured = { bmp ->
                                viewModel.setCapturedBitmap(bmp)
                                isCameraOpen = false
                            },
                            onClearPhoto = { viewModel.setCapturedBitmap(null) },
                            torchController = torchController,
                            onCameraError = { msg -> localNotice = msg }
                        )
                    }

                    else -> {
                        // Quick Text / Error Code hints
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "QUICK ERROR CODE / SYMPTOM TEMPLATES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DiagnosticCyan
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Washer showing OE drain code",
                                        "Engine OBD code P0301 misfire",
                                        "Slow drip under P-trap slip nut",
                                        "AC condenser buzzing, fan not spinning"
                                    ).forEach { hint ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { viewModel.setSymptomInput(hint) },
                                            label = { Text(hint, style = MaterialTheme.typography.bodySmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Shared Symptom / Notes Field + Multimodal Attachments Summary
            OutlinedTextField(
                value = symptomInput,
                onValueChange = { viewModel.setSymptomInput(it) },
                label = { Text(stringResource(R.string.workbench_symptom_label)) },
                placeholder = { Text("e.g., Squeals on cold start, or flashing E24 on dishwasher") },
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.dictate_symptoms))
                            }
                            runCatching { dictationLauncher.launch(speechIntent) }.onFailure {
                                localNotice = "On-device voice dictation is not available on this device."
                            }
                        },
                        modifier = Modifier.testTag("workbench_dictate_symptoms_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Dictate symptom notes with voice",
                            tint = DiagnosticCyan
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("symptom_notes_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Attachment Badges Row
            if (recordedWav != null || capturedBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recordedWav != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = SafetyAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Audio (${(recordedWav!!.size / 1024)} KB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    if (capturedBitmap != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    tint = DiagnosticCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Photo Attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (!localNotice.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = localNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Primary Diagnose CTA
            Button(
                onClick = {
                    audioRecorder.stopPlayback()
                    if (isRecording) audioRecorder.stopRecording()
                    viewModel.runDiagnosis(
                        mode = activeMode,
                        audioWavBytes = audioRecorder.recordedWavBytes.value
                    )
                },
                enabled = diagnosisState !is DiagnosisUiState.Analyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("run_diagnosis_button"),
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
                    Text(stringResource(R.string.workbench_analyzing), style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.workbench_run_diagnosis), style = MaterialTheme.typography.labelLarge)
                }
            }

            // Diagnosis Result Section
            AnimatedVisibility(visible = diagnosisState !is DiagnosisUiState.Idle) {
                when (val state = diagnosisState) {
                    is DiagnosisUiState.Idle -> Unit
                    is DiagnosisUiState.Analyzing -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SafetyAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = SafetyAmber)
                                Text(
                                    text = "Cross-referencing acoustic/visual signatures with repair manuals...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is DiagnosisUiState.QuotaChoice -> {
                        AiQuotaChoiceCard(
                            usedToday = state.usedToday,
                            dailyLimit = state.dailyLimit,
                            onConfigureByok = onOpenAiSettings,
                            onUnlockSupporter = onOpenSupportSheet,
                            onRunOfflineLookup = {
                                viewModel.runOfflineReferenceFallback(
                                    mode = activeMode,
                                    hasAudio = recordedWav != null,
                                    hasImage = capturedBitmap != null
                                )
                            }
                        )
                    }

                    is DiagnosisUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Diagnosis Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.runDiagnosis(activeMode, recordedWav) },
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
                                        onClick = {
                                            viewModel.runOfflineReferenceFallback(
                                                mode = activeMode,
                                                hasAudio = recordedWav != null,
                                                hasImage = capturedBitmap != null
                                            )
                                        },
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
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DiagnosticResultCard(
                                result = state.result,
                                isSaved = state.savedReportId != null,
                                onSaveClick = { viewModel.saveCurrentDiagnosis() },
                                onShareClick = onShareReport,
                                engineSource = state.engineSource,
                                category = state.category
                            )

                            if (showInlineSupportBanner) {
                                InlinePostDiagnosisSupportCard(
                                    onSupportClick = onOpenSupportSheet,
                                    onDismiss = onDismissSupportBanner
                                )
                            }

                            // Follow-Up Q&A Card
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
                                                .testTag("followup_input"),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                val q = followUpInput.trim()
                                                if (q.isNotEmpty()) {
                                                    followUpInput = ""
                                                    viewModel.sendFollowUp(q)
                                                }
                                            },
                                            enabled = followUpInput.isNotBlank() && !isFollowUpLoading,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .testTag("followup_send_button")
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
                                onClick = {
                                    viewModel.resetDiagnosis()
                                    audioRecorder.clearRecording()
                                    viewModel.setCapturedBitmap(null)
                                    viewModel.setSymptomInput("")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.workbench_start_new))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AudioProbePanel(
    isRecording: Boolean,
    isPlaying: Boolean,
    elapsedSeconds: Int,
    waveformSamples: List<Float>,
    hasAudioReady: Boolean,
    onToggleRecord: () -> Unit,
    onTogglePlayback: () -> Unit,
    onClearAudio: () -> Unit,
    onSymptomChipClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACOUSTIC SOUND PROBE",
                        style = MaterialTheme.typography.labelLarge,
                        color = SafetyAmber
                    )
                    Text(
                        text = "Hold phone near engine, belt, motor, or pipe (up to 20s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.US, "00:%02d", elapsedSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecording) StatusCriticalRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AcousticWaveformCanvas(samples = waveformSamples, isRecording = isRecording)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleRecord,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("record_audio_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) StatusCriticalRed else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRecording -> "Stop Listening"
                            hasAudioReady -> "Re-Record Sound"
                            else -> "Listen to Noise"
                        }
                    )
                }

                if (hasAudioReady && !isRecording) {
                    IconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .testTag("workbench_audio_playback_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Stop audio preview" else "Play audio preview",
                            tint = DiagnosticCyan
                        )
                    }

                    IconButton(
                        onClick = onClearAudio,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("clear_audio_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Discard recorded audio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (hasAudioReady && !isRecording) {
                Text(
                    text = "Audio clip captured — ready for Gemini rhythmic pattern analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusOkGreen
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Belt squeal", "Rhythmic knock", "Grinding bearing", "Clicking relay", "Pipe rattle").forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { onSymptomChipClick(tag) },
                        label = { Text("+ $tag", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionInspectorPanel(
    capturedBitmap: Bitmap?,
    isCameraOpen: Boolean,
    isTorchOn: Boolean,
    onOpenCamera: () -> Unit,
    onCloseCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onToggleTorch: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onClearPhoto: () -> Unit,
    torchController: TorchController,
    onCameraError: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VISUAL FAULT & ERROR SCANNER",
                    style = MaterialTheme.typography.labelLarge,
                    color = DiagnosticCyan
                )
                Text(
                    text = "Snap error displays, blown fuses, hoses, or pipe fittings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCameraOpen) {
                MechanicalCameraViewfinder(
                    isTorchOn = isTorchOn,
                    torchController = torchController,
                    onToggleTorch = onToggleTorch,
                    onCapture = onPhotoCaptured,
                    onClose = onCloseCamera,
                    onError = onCameraError
                )
            } else if (capturedBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DiagnosticCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Captured diagnostic photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onClearPhoto,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA000000))
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove photo",
                            tint = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = if (isCameraOpen) onCloseCamera else onOpenCamera,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("open_camera_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isCameraOpen) "Hide Camera" else "Live Camera")
                }

                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("pick_photo_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Photo Library")
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val maxSide = 1600
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxSide ||
            boundsOptions.outHeight / sampleSize > maxSide
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { exifStream ->
            ExifInterface(exifStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return decoded
        }

        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        rotated
    } catch (_: Exception) {
        null
    }
}
