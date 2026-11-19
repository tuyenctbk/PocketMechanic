package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiAccessRepository
import com.example.ai.AiAccessState
import com.example.ai.AiProviderMode
import com.example.ui.ByokVerificationUiState
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.util.openExternalUrl

@Composable
fun AiQuotaChoiceCard(
    usedToday: Int,
    dailyLimit: Int,
    onConfigureByok: () -> Unit,
    onUnlockSupporter: () -> Unit,
    onRunOfflineLookup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, SafetyAmber.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("ai_quota_choice_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SafetyAmber)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_quota_reached_title, usedToday, dailyLimit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ai_quota_reached_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onConfigureByok,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("quota_choose_byok_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiagnosticCyan,
                    contentColor = Color(0xFF002029)
                )
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ai_choose_byok_cta), style = MaterialTheme.typography.labelLarge)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onUnlockSupporter,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quota_choose_supporter_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_choose_supporter_cta), style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onRunOfflineLookup,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quota_choose_offline_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = StatusOkGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_choose_offline_cta), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun GarageAiEngineCard(
    accessState: AiAccessState,
    isActiveSupporter: Boolean,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = AiAccessRepository.remainingDailyQuota(accessState, isActiveSupporter)
    val dailyLimit = AiAccessRepository.dailyLimitFor(isActiveSupporter)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DiagnosticCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenAiSettings)
            .testTag("garage_ai_engine_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = DiagnosticCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (accessState.hasByokKey) Icons.Rounded.Key else Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = DiagnosticCyan
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.ai_engine_settings_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            accessState.preferredMode == AiProviderMode.OFFLINE_ONLY ->
                                "Offline Reference Only ($0 • Zero network)"
                            accessState.hasByokKey ->
                                "BYOK Active (${AiAccessRepository.maskApiKey(accessState.byokApiKey)}) • Unlimited"
                            else ->
                                "${accessState.preferredMode.label} • $remaining/$dailyLimit free left today"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Configure AI engine and BYOK key",
                tint = DiagnosticCyan
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEngineChoiceSheet(
    accessState: AiAccessState,
    isActiveSupporter: Boolean,
    verificationState: ByokVerificationUiState,
    onSelectMode: (AiProviderMode) -> Unit,
    onTestAndSaveKey: (String, String) -> Unit,
    onSaveKeyWithoutTest: (String, String) -> Unit,
    onRemoveKey: () -> Unit,
    onOpenSupportSheet: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keyDraft by remember(accessState.byokApiKey) { mutableStateOf(accessState.byokApiKey) }
    var selectedModel by remember(accessState.byokModelName) { mutableStateOf(accessState.byokModelName) }
    var showKeyPlain by remember { mutableStateOf(false) }

    val remaining = AiAccessRepository.remainingDailyQuota(accessState, isActiveSupporter)
    val dailyLimit = AiAccessRepository.dailyLimitFor(isActiveSupporter)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = DiagnosticCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = DiagnosticCyan)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_engine_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose how PocketMechanic runs diagnostics — 100% transparent & flexible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mode Switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_mode_segmented_row")
            ) {
                AiProviderMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = accessState.preferredMode == mode,
                        onClick = { onSelectMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AiProviderMode.entries.size
                        ),
                        modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            // Free Shared Tier Status Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isActiveSupporter) "SUPPORTER SHARED ALLOWANCE" else "FREE SHARED TIER",
                            style = MaterialTheme.typography.labelLarge,
                            color = SafetyAmber
                        )
                        Text(
                            text = "$remaining / $dailyLimit left today",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remaining > 0) StatusOkGreen else StatusCriticalRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Uses Flash with automatic Flash-Lite fallback if busy. Resets daily at midnight UTC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isActiveSupporter) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenSupportSheet()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Support Indie Dev (Boosts Daily Tier to 25/day)")
                        }
                    }
                }
            }

            // BYOK Personal Gemini API Key Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (accessState.hasByokKey) StatusOkGreen.copy(alpha = 0.5f) else DiagnosticCyan.copy(alpha = 0.35f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = DiagnosticCyan, modifier = Modifier.size(18.dp))
                            Text(
                                text = "BRING YOUR OWN GEMINI KEY (BYOK)",
                                style = MaterialTheme.typography.labelLarge,
                                color = DiagnosticCyan
                            )
                        }
                        if (accessState.hasByokKey) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Key saved", tint = StatusOkGreen, modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = "Paste your own free Google AI Studio Gemini API key for unlimited multimodal audio, camera & voice diagnoses directly under your own free tier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { context.openExternalUrl(AiAccessState.AI_STUDIO_KEY_URL, "Google AI Studio Key URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("byok_get_free_key_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.ai_get_key_cta))
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text(stringResource(R.string.ai_key_input_label)) },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKeyPlain = !showKeyPlain }) {
                                Icon(
                                    imageVector = if (showKeyPlain) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showKeyPlain) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_api_key_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            AiAccessState.DEFAULT_BYOK_MODEL to "Flash (Best)",
                            AiAccessState.LITE_BYOK_MODEL to "Flash-Lite (Fastest)"
                        ).forEach { (modelId, label) ->
                            FilterChip(
                                selected = selectedModel == modelId,
                                onClick = { selectedModel = modelId },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onTestAndSaveKey(keyDraft, selectedModel) },
                            enabled = keyDraft.trim().length >= 20 && verificationState !is ByokVerificationUiState.Testing,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("byok_verify_save_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DiagnosticCyan,
                                contentColor = Color(0xFF002029)
                            )
                        ) {
                            if (verificationState is ByokVerificationUiState.Testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF002029)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ai_testing_key))
                            } else {
                                Text(stringResource(R.string.ai_test_save_key))
                            }
                        }

                        if (accessState.hasByokKey) {
                            OutlinedButton(
                                onClick = {
                                    keyDraft = ""
                                    onRemoveKey()
                                },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(48.dp)
                                    .testTag("byok_remove_key_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.ai_clear_key), tint = StatusCriticalRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.ai_clear_key), color = StatusCriticalRed)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSaveKeyWithoutTest(keyDraft, selectedModel) },
                                enabled = keyDraft.trim().length >= 20,
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }

                    AnimatedVisibility(visible = verificationState is ByokVerificationUiState.Valid || verificationState is ByokVerificationUiState.Invalid) {
                        val isOk = verificationState is ByokVerificationUiState.Valid
                        val msg = when (verificationState) {
                            is ByokVerificationUiState.Valid -> verificationState.message
                            is ByokVerificationUiState.Invalid -> verificationState.message
                            else -> ""
                        }
                        Surface(
                            color = if (isOk) StatusOkGreen.copy(alpha = 0.14f) else StatusCriticalRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOk) StatusOkGreen else StatusCriticalRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_privacy_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
