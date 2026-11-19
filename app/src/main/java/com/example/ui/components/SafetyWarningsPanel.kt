package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.model.DiagnosticSeverity
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarning
import com.example.model.SafetyWarningLevel
import com.example.ui.theme.PocketMechanicTheme
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.ui.theme.StatusWarnOrange

@Composable
fun SafetyWarningsPanel(
    warnings: List<SafetyWarning>,
    overallSeverity: DiagnosticSeverity = DiagnosticSeverity.MEDIUM,
    modifier: Modifier = Modifier
) {
    if (warnings.isEmpty()) return

    val acknowledgedMap = remember(warnings) { mutableStateMapOf<String, Boolean>() }
    val acknowledgedCount = warnings.count { acknowledgedMap[it.id] == true }
    val allAcknowledged = acknowledgedCount == warnings.size

    val highestLevel = remember(warnings, overallSeverity) {
        warnings.maxByOrNull { it.level.priority }?.level ?: when (overallSeverity) {
            DiagnosticSeverity.CRITICAL -> SafetyWarningLevel.DANGER
            DiagnosticSeverity.HIGH -> SafetyWarningLevel.WARNING
            else -> SafetyWarningLevel.CAUTION
        }
    }

    val accentColor by animateColorAsState(
        targetValue = when {
            allAcknowledged -> StatusOkGreen
            highestLevel == SafetyWarningLevel.DANGER -> StatusCriticalRed
            highestLevel == SafetyWarningLevel.WARNING -> StatusWarnOrange
            else -> SafetyAmber
        },
        animationSpec = tween(250),
        label = "safety_panel_accent"
    )

    val gradientTop = when {
        allAcknowledged -> StatusOkGreen.copy(alpha = 0.14f)
        highestLevel == SafetyWarningLevel.DANGER -> StatusCriticalRed.copy(alpha = 0.24f)
        highestLevel == SafetyWarningLevel.WARNING -> StatusWarnOrange.copy(alpha = 0.22f)
        else -> SafetyAmber.copy(alpha = 0.20f)
    }

    val isActiveDanger = highestLevel == SafetyWarningLevel.DANGER && !allAcknowledged
    val dangerPulseTransition = rememberInfiniteTransition(label = "safety_danger_pulse")
    val borderPulseAlpha by dangerPulseTransition.animateFloat(
        initialValue = if (isActiveDanger) 0.45f else 0.85f,
        targetValue = if (isActiveDanger) 1f else 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "safety_border_alpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isActiveDanger) 2.dp else 1.5.dp,
                color = accentColor.copy(alpha = if (isActiveDanger) borderPulseAlpha else 0.85f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .testTag("safety_warnings_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(gradientTop, MaterialTheme.colorScheme.surface)
                    )
                )
        ) {
            // Industrial Hazard Caution Strip
            HazardCautionStripe(
                primaryColor = accentColor,
                secondaryColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    allAcknowledged -> Icons.Rounded.VerifiedUser
                                    highestLevel == SafetyWarningLevel.DANGER -> Icons.Rounded.GppMaybe
                                    else -> Icons.Rounded.WarningAmber
                                },
                                contentDescription = "Safety warning alert",
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = when {
                                    allAcknowledged -> "SAFETY CHECKS CONFIRMED"
                                    highestLevel == SafetyWarningLevel.DANGER -> "CRITICAL SAFETY WARNINGS"
                                    else -> "SAFETY PRECAUTIONS FIRST"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (allAcknowledged) {
                                    "All ${warnings.size} safety precautions checked off"
                                } else {
                                    "Verify each precaution before touching equipment"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = accentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "$acknowledgedCount/${warnings.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Individual Structured Safety Warning Items
                warnings.forEach { warning ->
                    val isChecked = acknowledgedMap[warning.id] == true
                    SafetyWarningItemCard(
                        warning = warning,
                        isAcknowledged = isChecked,
                        onToggleAcknowledge = { acknowledgedMap[warning.id] = !isChecked }
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyWarningItemCard(
    warning: SafetyWarning,
    isAcknowledged: Boolean,
    onToggleAcknowledge: () -> Unit
) {
    val levelColor = when (warning.level) {
        SafetyWarningLevel.DANGER -> StatusCriticalRed
        SafetyWarningLevel.WARNING -> StatusWarnOrange
        SafetyWarningLevel.CAUTION -> SafetyAmber
    }
    val effectiveAccent = if (isAcknowledged) StatusOkGreen else levelColor

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isAcknowledged) 0.48f else 0.85f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .workshopFocusRing(
                shape = RoundedCornerShape(14.dp),
                focusColor = effectiveAccent,
                unfocusedColor = effectiveAccent.copy(alpha = if (isAcknowledged) 0.45f else 0.55f)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleAcknowledge)
            .testTag("safety_warning_item_${warning.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isAcknowledged) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isAcknowledged) {
                    "Safety precaution confirmed: ${warning.title}"
                } else {
                    "Confirm safety precaution: ${warning.title}"
                },
                tint = effectiveAccent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = levelColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = warning.level.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = hazardIcon(warning.hazardType),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = warning.hazardType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = warning.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = warning.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAcknowledged) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                    }
                )
            }
        }
    }
}

@Composable
private fun HazardCautionStripe(
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(secondaryColor)
    ) {
        val stripeW = 14.dp.toPx()
        val skew = 7.dp.toPx()
        var x = -stripeW
        while (x < size.width + stripeW) {
            val path = Path().apply {
                moveTo(x, size.height)
                lineTo(x + skew, 0f)
                lineTo(x + skew + stripeW * 0.55f, 0f)
                lineTo(x + stripeW * 0.55f, size.height)
                close()
            }
            drawPath(path = path, color = primaryColor)
            x += stripeW
        }
    }
}

private fun hazardIcon(type: SafetyHazardType): ImageVector = when (type) {
    SafetyHazardType.ELECTRICAL -> Icons.Rounded.Bolt
    SafetyHazardType.THERMAL -> Icons.Rounded.Whatshot
    SafetyHazardType.PRESSURE -> Icons.Rounded.Compress
    SafetyHazardType.MECHANICAL -> Icons.Rounded.Build
    SafetyHazardType.CHEMICAL -> Icons.Rounded.Science
    SafetyHazardType.STRUCTURAL -> Icons.Rounded.CarRepair
    SafetyHazardType.GENERAL -> Icons.Rounded.Shield
}

@Preview(showBackground = true)
@Composable
private fun SafetyWarningsPanelPreview() {
    PocketMechanicTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            SafetyWarningsPanel(
                warnings = listOf(
                    SafetyWarning(
                        id = "warn_0",
                        title = "Isolate 240V Breaker First",
                        detail = "Switch off the main double-pole breaker and verify zero voltage before touching the capacitor or control board.",
                        level = SafetyWarningLevel.DANGER,
                        hazardType = SafetyHazardType.ELECTRICAL
                    ),
                    SafetyWarning(
                        id = "warn_1",
                        title = "Hot Discharge Line & Coil Fins",
                        detail = "Allow compressor discharge piping to cool for 15 minutes before reaching inside the condenser housing.",
                        level = SafetyWarningLevel.WARNING,
                        hazardType = SafetyHazardType.THERMAL
                    )
                ),
                overallSeverity = DiagnosticSeverity.CRITICAL
            )
        }
    }
}
