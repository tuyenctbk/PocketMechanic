package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ai.AiEngineSource
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.GroundedSource
import com.example.ui.localizedBadgeLabel
import com.example.ui.localizedLabel
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalContainer
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.ui.theme.StatusWarnOrange
import com.example.ui.theme.TorchGold
import com.example.util.openExternalUrl
import com.example.util.openMapsSearch
import com.example.util.sharePlainText

@Composable
fun TorchActionButton(
    isTorchOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isTorchOn) TorchGold else MaterialTheme.colorScheme.surfaceVariant,
        label = "torch_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isTorchOn) Color(0xFF1A1400) else MaterialTheme.colorScheme.onSurface,
        label = "torch_fg"
    )

    Surface(
        color = bgColor,
        shape = CircleShape,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(
                1.dp,
                if (isTorchOn) TorchGold else MaterialTheme.colorScheme.outline,
                CircleShape
            )
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .testTag("torch_toggle_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                contentDescription = stringResource(R.string.torch_toggle),
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isTorchOn) stringResource(R.string.torch_light_on) else stringResource(R.string.torch_light_off),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InspectionFlashlightToggleCard(
    isTorchOn: Boolean,
    isFlashAvailable: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isTorchOn) TorchGold.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant,
        label = "torch_card_border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isTorchOn) TorchGold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        label = "torch_card_bg"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (isTorchOn) TorchGold else MaterialTheme.colorScheme.surfaceVariant,
        label = "torch_badge_bg"
    )
    val badgeTint by animateColorAsState(
        targetValue = if (isTorchOn) Color(0xFF1A1400) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "torch_badge_fg"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .border(if (isTorchOn) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (isTorchOn) {
                    "Inspection flashlight on, tap to turn off"
                } else {
                    "Inspection flashlight off, tap to turn on"
                }
            }
            .testTag("diagnosis_flashlight_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isTorchOn) stringResource(R.string.torch_card_title_on) else stringResource(R.string.torch_card_title_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isTorchOn) TorchGold else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        !isFlashAvailable -> stringResource(R.string.torch_card_unavailable)
                        isTorchOn -> stringResource(R.string.torch_card_desc_on)
                        else -> stringResource(R.string.torch_card_desc_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isTorchOn,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF1A1400),
                    checkedTrackColor = TorchGold,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.testTag("diagnosis_flashlight_toggle")
            )
        }
    }
}

@Composable
fun AcousticWaveformCanvas(
    samples: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val barActiveColor = if (isRecording) SafetyAmber else DiagnosticCyan
    val barTrackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0F15))
            .border(
                1.dp,
                if (isRecording) SafetyAmber.copy(alpha = 0.6f) else barTrackColor,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        val count = samples.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        val centerY = size.height / 2f

        // Center baseline
        drawLine(
            color = barTrackColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        samples.forEachIndexed { index, amp ->
            val clamped = amp.coerceIn(0.06f, 1f)
            val barHeight = size.height * clamped
            val x = index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            drawRoundRect(
                color = barActiveColor.copy(alpha = 0.35f + 0.65f * clamped),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticResultCard(
    result: DiagnosticResult,
    isSaved: Boolean,
    onSaveClick: (() -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    engineSource: AiEngineSource? = null,
    category: EquipmentCategory? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checkedSteps = remember(result) { mutableStateMapOf<Int, Boolean>() }
    val animatedConfidence by animateFloatAsState(
        targetValue = (result.confidencePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "confidence_progress"
    )

    val (severityBg, severityFg) = when (result.severity) {
        DiagnosticSeverity.LOW -> StatusOkGreen.copy(alpha = 0.16f) to StatusOkGreen
        DiagnosticSeverity.MEDIUM -> SafetyAmber.copy(alpha = 0.18f) to SafetyAmber
        DiagnosticSeverity.HIGH -> StatusWarnOrange.copy(alpha = 0.18f) to StatusWarnOrange
        DiagnosticSeverity.CRITICAL -> StatusCriticalContainer to StatusCriticalRed
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val summaryCard: @Composable (Modifier) -> Unit = { cardModifier ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = cardModifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = severityBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = result.severity.localizedLabel().uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = severityFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.result_confidence_match, result.confidencePercent),
                            style = MaterialTheme.typography.labelMedium,
                            color = DiagnosticCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (engineSource != null) {
                        val (sourceBg, sourceFg, sourceIcon) = when (engineSource) {
                            AiEngineSource.BYOK_PERSONAL_KEY -> Triple(
                                DiagnosticCyan.copy(alpha = 0.14f),
                                DiagnosticCyan,
                                Icons.Rounded.Key
                            )
                            AiEngineSource.OFFLINE_REFERENCE -> Triple(
                                StatusOkGreen.copy(alpha = 0.14f),
                                StatusOkGreen,
                                Icons.AutoMirrored.Rounded.MenuBook
                            )
                            AiEngineSource.FREE_SHARED_FLASH,
                            AiEngineSource.FREE_SHARED_LITE_FALLBACK -> Triple(
                                SafetyAmber.copy(alpha = 0.14f),
                                SafetyAmber,
                                Icons.Rounded.AutoAwesome
                            )
                        }
                        Surface(
                            color = sourceBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("diagnosis_engine_source_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = sourceIcon,
                                    contentDescription = null,
                                    tint = sourceFg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = engineSource.localizedBadgeLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sourceFg,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LinearProgressIndicator(
                        progress = { animatedConfidence },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SafetyAmber,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.result_likely_culprit),
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyAmber
                            )
                            Text(
                                text = result.primaryCulprit,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onSaveClick != null) {
                            if (isSaved) {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diagnosis_saved_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdded,
                                        contentDescription = null,
                                        tint = StatusOkGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.result_saved_to_log), color = StatusOkGreen)
                                }
                            } else {
                                Button(
                                    onClick = onSaveClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_diagnosis_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.result_save_report))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val shareSummary = buildString {
                                    appendLine("PocketMechanic Diagnosis: ${result.title}")
                                    appendLine("Severity: ${result.severity.label} (${result.confidencePercent}% match)")
                                    appendLine("Likely Culprit: ${result.primaryCulprit}")
                                    if (result.diySteps.isNotEmpty()) {
                                        appendLine("Top DIY Steps:")
                                        result.diySteps.take(3).forEachIndexed { idx, s ->
                                            appendLine("${idx + 1}. $s")
                                        }
                                    }
                                }.trim()
                                if (onShareClick != null) {
                                    onShareClick(shareSummary)
                                } else {
                                    context.sharePlainText(
                                        chooserTitle = context.getString(R.string.result_share_diagnosis),
                                        subject = result.title,
                                        text = shareSummary
                                    )
                                }
                            },
                            modifier = Modifier
                                .let { if (onSaveClick == null) it.fillMaxWidth() else it }
                                .height(48.dp)
                                .testTag("share_diagnosis_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.result_share_full),
                                tint = DiagnosticCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (onSaveClick == null) {
                                    stringResource(R.string.result_share_full)
                                } else {
                                    stringResource(R.string.result_share)
                                },
                                color = DiagnosticCyan
                            )
                        }
                    }
                }
            }
        }

        if (result.safetyWarnings.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        summaryCard(Modifier.weight(1f))
                        SafetyWarningsPanel(
                            warnings = result.safetyWarnings,
                            overallSeverity = result.severity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SafetyWarningsPanel(
                            warnings = result.safetyWarnings,
                            overallSeverity = result.severity
                        )
                        summaryCard(Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            summaryCard(Modifier.fillMaxWidth())
        }

        // DIY Steps Checklist
        if (result.diySteps.isNotEmpty()) {
            val completedSteps = result.diySteps.indices.count { checkedSteps[it] == true }
            val stepProgress by animateFloatAsState(
                targetValue = completedSteps.toFloat() / result.diySteps.size.coerceAtLeast(1),
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "diy_steps_progress"
            )
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.result_diy_checklist),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.result_steps_done, completedSteps, result.diySteps.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (completedSteps == result.diySteps.size) StatusOkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { stepProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (completedSteps == result.diySteps.size) StatusOkGreen else SafetyAmber,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    result.diySteps.forEachIndexed { idx, step ->
                        val isDone = checkedSteps[idx] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .workshopFocusRing(RoundedCornerShape(10.dp), focusColor = SafetyAmber)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { checkedSteps[idx] = !isDone }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isDone) "Step completed" else "Mark step complete",
                                tint = if (isDone) StatusOkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "${idx + 1}. $step",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                    }
                }
            }
        }

        // Required Parts & Tools
        if (result.partsTools.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            tint = DiagnosticCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.result_parts_tools),
                            style = MaterialTheme.typography.labelLarge,
                            color = DiagnosticCyan
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        result.partsTools.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Zero-Cost Google Maps Local Parts & Repair Pro Finder
        LocalPartsAndShopFinderCard(
            category = category,
            partsTools = result.partsTools,
            severity = result.severity,
            onSearchMaps = { query -> context.openMapsSearch(query) }
        )

        // Grounding Sources + Google Search Entry Point
        if (result.sources.isNotEmpty() || !result.searchEntryPointHtml.isNullOrBlank()) {
            GroundingSourcesSection(
                sources = result.sources,
                searchEntryPointHtml = result.searchEntryPointHtml
            )
        }
    }
}

@Composable
private fun LocalPartsAndShopFinderCard(
    category: EquipmentCategory?,
    partsTools: List<String>,
    severity: DiagnosticSeverity,
    onSearchMaps: (String) -> Unit
) {
    val isUrgent = severity == DiagnosticSeverity.HIGH || severity == DiagnosticSeverity.CRITICAL
    val partsStorePrefix = when (category) {
        EquipmentCategory.VEHICLE -> "auto parts store"
        EquipmentCategory.PLUMBING -> "plumbing supply hardware store"
        EquipmentCategory.APPLIANCE -> "appliance parts store"
        EquipmentCategory.HVAC -> "HVAC supply hardware store"
        EquipmentCategory.YARD_TOOL -> "lawn mower small engine parts store"
        EquipmentCategory.OTHER, null -> "hardware store"
    }
    val topPart = partsTools.firstOrNull()?.take(36)?.trim()
    val partsQuery = if (!topPart.isNullOrBlank()) "$partsStorePrefix $topPart" else partsStorePrefix

    val repairShopQuery = when (category) {
        EquipmentCategory.VEHICLE -> "auto repair mechanic near me"
        EquipmentCategory.PLUMBING -> "licensed plumber near me"
        EquipmentCategory.APPLIANCE -> "appliance repair technician near me"
        EquipmentCategory.HVAC -> "HVAC repair service near me"
        EquipmentCategory.YARD_TOOL -> "small engine repair shop near me"
        EquipmentCategory.OTHER, null -> "home repair technician near me"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isUrgent) StatusWarnOrange.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("local_maps_finder_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = if (isUrgent) StatusWarnOrange else DiagnosticCyan,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nearby_help_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUrgent) StatusWarnOrange else DiagnosticCyan
                    )
                    Text(
                        text = if (isUrgent) {
                            stringResource(R.string.nearby_help_urgent)
                        } else {
                            stringResource(R.string.nearby_help_standard)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSearchMaps(partsQuery) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("find_parts_nearby_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = DiagnosticCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.find_parts_nearby),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onSearchMaps(repairShopQuery) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("find_repair_shop_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = if (isUrgent) StatusWarnOrange else SafetyAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.find_pro_nearby),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundingSourcesSection(
    sources: List<GroundedSource>,
    searchEntryPointHtml: String? = null
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = DiagnosticCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.result_grounded_sources),
                    style = MaterialTheme.typography.labelLarge,
                    color = DiagnosticCyan
                )
            }

            if (sources.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { src ->
                        AssistChip(
                            onClick = {
                                context.openExternalUrl(src.uri, src.title)
                            },
                            label = {
                                Text(
                                    text = src.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open source",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            if (!searchEntryPointHtml.isNullOrBlank()) {
                SearchSuggestionsWebView(htmlContent = searchEntryPointHtml)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchSuggestionsWebView(htmlContent: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                tag = htmlContent
                loadDataWithBaseURL(
                    "https://www.google.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL(
                    "https://www.google.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
    )
}
