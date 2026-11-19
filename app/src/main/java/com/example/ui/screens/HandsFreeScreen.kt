package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.hardware.AudioRecorderManager
import com.example.hardware.HandsFreePhase
import com.example.hardware.HandsFreeVoiceEngine
import com.example.hardware.TorchController
import com.example.ui.MechanicViewModel
import com.example.ui.components.GroundingSourcesSection
import com.example.ui.components.TorchActionButton
import com.example.ui.components.workshopFocusRing
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen

@Composable
fun HandsFreeScreen(
    viewModel: MechanicViewModel,
    voiceEngine: HandsFreeVoiceEngine,
    audioRecorder: AudioRecorderManager,
    torchController: TorchController,
    modifier: Modifier = Modifier,
    onTorchToggled: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val phase by voiceEngine.phase.collectAsStateWithLifecycle()
    val partialTranscript by voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val speechSupported by voiceEngine.isSpeechRecognizerSupported.collectAsStateWithLifecycle()
    val isAudioRecording by audioRecorder.isRecording.collectAsStateWithLifecycle()
    val turns by viewModel.voiceTurns.collectAsStateWithLifecycle()
    val equipmentInput by viewModel.equipmentInput.collectAsStateWithLifecycle()
    val isTorchOn by torchController.isTorchOn.collectAsStateWithLifecycle()
    val isFlashAvailable by torchController.isFlashAvailable.collectAsStateWithLifecycle()

    var autoLoopEnabled by rememberSaveable { mutableStateOf(true) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    var isScreenActive by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        isScreenActive = true
        onDispose {
            isScreenActive = false
            viewModel.cancelHandsFreeTurn()
            voiceEngine.stopAll()
            if (audioRecorder.isRecording.value) audioRecorder.stopRecording()
        }
    }

    fun startSpeechCycle() {
        if (!isScreenActive) return
        voiceNotice = null
        if (speechSupported) {
            voiceEngine.startListening(
                onTranscript = { transcript ->
                    if (!isScreenActive) return@startListening
                    viewModel.sendHandsFreeTurn(
                        spokenText = transcript,
                        audioWavBytes = null,
                        onReplyReady = { reply ->
                            if (!isScreenActive) return@sendHandsFreeTurn
                            voiceEngine.speak(reply) {
                                if (isScreenActive && autoLoopEnabled) {
                                    startSpeechCycle()
                                }
                            }
                        },
                        onError = { msg ->
                            voiceEngine.stopAll()
                            if (isScreenActive) voiceNotice = msg
                        }
                    )
                },
                onError = { msg ->
                    if (isScreenActive) voiceNotice = msg
                }
            )
        } else {
            // Fallback: record direct audio clip and send to Gemini Audio Multimodal
            if (isAudioRecording) {
                val wav = audioRecorder.stopRecording()
                if (wav != null) {
                    voiceEngine.setThinking()
                    viewModel.sendHandsFreeTurn(
                        spokenText = null,
                        audioWavBytes = wav,
                        onReplyReady = { reply ->
                            if (isScreenActive) {
                                voiceEngine.speak(reply) {}
                            }
                        },
                        onError = { msg ->
                            voiceEngine.stopAll()
                            if (isScreenActive) voiceNotice = msg
                        }
                    )
                }
            } else {
                audioRecorder.startRecording(
                    scope = scope,
                    onError = { msg -> if (isScreenActive) voiceNotice = msg }
                )
            }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechCycle()
        } else {
            voiceNotice = context.getString(R.string.permission_mic_required)
        }
    }

    val effectivePhase = if (isAudioRecording) HandsFreePhase.LISTENING else phase

    val orbColor by animateColorAsState(
        targetValue = when (effectivePhase) {
            HandsFreePhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            HandsFreePhase.LISTENING -> SafetyAmber
            HandsFreePhase.THINKING -> DiagnosticCyan
            HandsFreePhase.SPEAKING -> StatusOkGreen
        },
        label = "orb_color"
    )

    val pulseTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (effectivePhase != HandsFreePhase.IDLE) 1.09f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val triggerQuickPrompt: (String) -> Unit = { promptText ->
        voiceNotice = null
        voiceEngine.setThinking()
        viewModel.sendHandsFreeTurn(
            spokenText = promptText,
            audioWavBytes = null,
            onReplyReady = { reply ->
                if (isScreenActive) {
                    voiceEngine.speak(reply) {}
                }
            },
            onError = { msg ->
                voiceEngine.stopAll()
                if (isScreenActive) voiceNotice = msg
            }
        )
    }

    val controlsAndOrbPane: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Controls Row (Auto-loop + Torch + Clear)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hands_free_banner),
                                style = MaterialTheme.typography.labelLarge,
                                color = SafetyAmber
                            )
                            Text(
                                text = equipmentInput.ifBlank { stringResource(R.string.hands_free_general_assistant) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isFlashAvailable) {
                            TorchActionButton(
                                isTorchOn = isTorchOn,
                                onToggle = {
                                    if (torchController.toggleTorch()) {
                                        voiceNotice = null
                                        onTorchToggled(torchController.isTorchOn.value)
                                    } else {
                                        voiceNotice = context.getString(R.string.torch_card_unavailable)
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = autoLoopEnabled,
                                onCheckedChange = { autoLoopEnabled = it },
                                enabled = speechSupported
                            )
                            Text(
                                text = if (speechSupported) {
                                    stringResource(R.string.hands_free_loop_enabled)
                                } else {
                                    stringResource(R.string.hands_free_tap_record)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (turns.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearVoiceTurns() }
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = "Clear voice conversation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Huge Touch-Anywhere Status Orb
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .workshopFocusRing(
                        shape = RoundedCornerShape(24.dp),
                        focusColor = SafetyAmber,
                        unfocusedColor = orbColor.copy(alpha = 0.55f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        if (effectivePhase == HandsFreePhase.LISTENING && speechSupported) {
                            viewModel.cancelHandsFreeTurn()
                            voiceEngine.stopAll()
                        } else if (effectivePhase == HandsFreePhase.SPEAKING || effectivePhase == HandsFreePhase.THINKING) {
                            viewModel.cancelHandsFreeTurn()
                            voiceEngine.stopAll()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                startSpeechCycle()
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    .testTag("hands_free_orb_button")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(orbColor.copy(alpha = 0.22f))
                            .border(2.dp, orbColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (effectivePhase) {
                                HandsFreePhase.IDLE -> Icons.Rounded.Mic
                                HandsFreePhase.LISTENING -> if (isAudioRecording) Icons.Rounded.Stop else Icons.Rounded.GraphicEq
                                HandsFreePhase.THINKING -> Icons.Rounded.GraphicEq
                                HandsFreePhase.SPEAKING -> Icons.Rounded.RecordVoiceOver
                            },
                            contentDescription = "Voice mode control",
                            tint = orbColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = when (effectivePhase) {
                            HandsFreePhase.IDLE -> if (speechSupported) stringResource(R.string.hands_free_tap_start) else stringResource(R.string.hands_free_tap_record)
                            HandsFreePhase.LISTENING -> stringResource(R.string.hands_free_listening)
                            HandsFreePhase.THINKING -> stringResource(R.string.hands_free_thinking)
                            HandsFreePhase.SPEAKING -> stringResource(R.string.hands_free_speaking)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = "\"$partialTranscript\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SafetyAmber,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!voiceNotice.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = voiceNotice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (effectivePhase != HandsFreePhase.IDLE) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelHandsFreeTurn()
                        voiceEngine.stopAll()
                        if (audioRecorder.isRecording.value) audioRecorder.clearRecording()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, tint = StatusCriticalRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.hands_free_pause))
                }
            }
        }
    }

    val transcriptPane: @Composable (Modifier) -> Unit = { paneMod ->
        LazyColumn(
            state = listState,
            modifier = paneMod.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "TRY ASKING HANDS-FREE",
                                style = MaterialTheme.typography.labelLarge,
                                color = DiagnosticCyan
                            )
                            Text(
                                text = "Tap a quick scenario below or press the mic orb to talk through your repair step by step:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                "My engine belt squeals loudly on cold start — what should I check?",
                                "Washer showing OE drain error code and won't spin",
                                "Slow drip under kitchen sink P-trap slip nut",
                                "AC condenser is buzzing and fan won't spin"
                            ).forEach { samplePrompt ->
                                FilterChip(
                                    selected = false,
                                    onClick = { triggerQuickPrompt(samplePrompt) },
                                    label = { Text(samplePrompt, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                items(turns, key = { it.id }) { turn ->
                    Surface(
                        color = if (turn.isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (turn.isUser) "YOU" else "POCKETMECHANIC",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (turn.isUser) SafetyAmber else DiagnosticCyan
                            )
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (turn.sources.isNotEmpty()) {
                                GroundingSourcesSection(sources = turn.sources)
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(modifier = Modifier.weight(0.95f).fillMaxHeight()) {
                    controlsAndOrbPane()
                }
                transcriptPane(Modifier.weight(1.05f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                controlsAndOrbPane()
                transcriptPane(Modifier.weight(1f))
            }
        }
    }
}
