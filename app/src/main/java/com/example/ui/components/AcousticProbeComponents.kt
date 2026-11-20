package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.hardware.AcousticSignalQuality
import com.example.model.EquipmentCategory
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.ui.theme.StatusWarnOrange
import java.util.Locale

enum class AcousticCaptureProfile(
    val labelRes: Int,
    val placementHintRes: Int,
    val icon: ImageVector,
    val category: EquipmentCategory,
    val symptomChipRes: List<Int>,
    val equipmentPlaceholderRes: Int,
    val symptomPlaceholderRes: Int
) {
    ENGINE_BAY(
        labelRes = R.string.audio_profile_engine,
        placementHintRes = R.string.audio_placement_engine,
        icon = Icons.Rounded.DirectionsCar,
        category = EquipmentCategory.VEHICLE,
        symptomChipRes = listOf(
            R.string.chip_belt_squeal,
            R.string.chip_rhythmic_knock,
            R.string.chip_lifter_tick,
            R.string.chip_bad_bearing,
            R.string.chip_exhaust_hiss,
            R.string.chip_alternator_whine
        ),
        equipmentPlaceholderRes = R.string.placeholder_engine_equip,
        symptomPlaceholderRes = R.string.placeholder_engine_symptom
    ),
    PLUMBING(
        labelRes = R.string.audio_profile_plumbing,
        placementHintRes = R.string.audio_placement_plumbing,
        icon = Icons.Rounded.Plumbing,
        category = EquipmentCategory.PLUMBING,
        symptomChipRes = listOf(
            R.string.chip_water_hammer,
            R.string.chip_pipe_rattle,
            R.string.chip_trap_gurgle,
            R.string.chip_valve_hiss,
            R.string.chip_sump_pump_hum,
            R.string.chip_dripping_echo
        ),
        equipmentPlaceholderRes = R.string.placeholder_plumbing_equip,
        symptomPlaceholderRes = R.string.placeholder_plumbing_symptom
    ),
    APPLIANCE(
        labelRes = R.string.audio_profile_appliance,
        placementHintRes = R.string.audio_placement_appliance,
        icon = Icons.Rounded.Kitchen,
        category = EquipmentCategory.APPLIANCE,
        symptomChipRes = listOf(
            R.string.chip_compressor_buzz,
            R.string.chip_relay_click,
            R.string.chip_drum_thump,
            R.string.chip_fan_grind,
            R.string.chip_drain_pump_whine,
            R.string.chip_motor_hum
        ),
        equipmentPlaceholderRes = R.string.placeholder_appliance_equip,
        symptomPlaceholderRes = R.string.placeholder_appliance_symptom
    );

    companion object {
        fun fromCategory(category: EquipmentCategory): AcousticCaptureProfile = when (category) {
            EquipmentCategory.PLUMBING -> PLUMBING
            EquipmentCategory.APPLIANCE, EquipmentCategory.HVAC -> APPLIANCE
            else -> ENGINE_BAY
        }
    }
}

@Composable
fun AcousticProbeConsoleCard(
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
    onSelectProfile: (AcousticCaptureProfile) -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlayback: () -> Unit,
    onClearAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAudioReady = recordedBytesSize != null && recordedBytesSize > 0
    val progressFraction = (elapsedMillis.toFloat() / maxDurationMs.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)
    val animatedRingProgress by animateFloatAsState(
        targetValue = if (isRecording) progressFraction else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "record_ring_progress"
    )

    val pulseScale = if (isRecording) {
        val pulseTransition = rememberInfiniteTransition(label = "record_pulse")
        pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(680),
                repeatMode = RepeatMode.Reverse
            ),
            label = "record_pulse_scale"
        ).value
    } else {
        1f
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isRecording -> SafetyAmber.copy(alpha = 0.78f)
            isPlaying -> DiagnosticCyan.copy(alpha = 0.75f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "console_border_color"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.probe_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = SafetyAmber
                    )
                    Text(
                        text = stringResource(activeProfile.placementHintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = when {
                        isRecording -> StatusCriticalRed.copy(alpha = 0.18f)
                        isPlaying -> DiagnosticCyan.copy(alpha = 0.18f)
                        hasAudioReady -> StatusOkGreen.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            isRecording -> stringResource(R.string.probe_status_rec)
                            isPlaying -> stringResource(R.string.probe_status_play)
                            hasAudioReady -> stringResource(R.string.probe_status_ready)
                            else -> stringResource(R.string.probe_status_idle)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isRecording -> StatusCriticalRed
                            isPlaying -> DiagnosticCyan
                            hasAudioReady -> StatusOkGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Engine vs. Plumbing vs. Appliance capture profile switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("acoustic_profile_selector")
            ) {
                AcousticCaptureProfile.entries.forEachIndexed { index, profile ->
                    SegmentedButton(
                        selected = activeProfile == profile,
                        onClick = { onSelectProfile(profile) },
                        enabled = !isRecording,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AcousticCaptureProfile.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = profile.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("acoustic_profile_${profile.name.lowercase()}")
                    ) {
                        Text(
                            text = stringResource(profile.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            MechanicalWaveformDisplay(
                samples = waveformSamples,
                isRecording = isRecording,
                isPlaying = isPlaying,
                playbackProgress = playbackProgress,
                hasAudioReady = hasAudioReady,
                modifier = Modifier.testTag("mechanical_waveform_display")
            )

            // Live Telemetry & Signal Quality Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val secs = (elapsedMillis / 1000L).toInt()
                val tenths = ((elapsedMillis % 1000L) / 100L).toInt()
                val maxSecs = (maxDurationMs / 1000L).toInt()
                Text(
                    text = if (isRecording) {
                        String.format(Locale.US, "00:%02d.%d / 00:%02d", secs, tenths, maxSecs)
                    } else {
                        String.format(Locale.US, "00:%02d / 00:%02d", secs, maxSecs)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isRecording) StatusCriticalRed else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRecording && signalQuality != AcousticSignalQuality.IDLE) {
                        SignalQualityPill(quality = signalQuality)
                    }
                    Text(
                        text = when {
                            isRecording -> "~$currentDb dB"
                            isPlaying -> stringResource(R.string.probe_playing_pct, (playbackProgress * 100).toInt())
                            hasAudioReady -> stringResource(R.string.probe_kb_wav, (recordedBytesSize ?: 0) / 1024)
                            else -> stringResource(R.string.probe_raw_wav)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = DiagnosticCyan
                    )
                }
            }

            // Centered Transport Controls: Discard | Record/Stop (with 20s Ring) | Play Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasAudioReady && !isRecording) {
                    IconButton(
                        onClick = onClearAudio,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("mechanical_audio_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.probe_discard_audio),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(106.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(94.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(StatusCriticalRed.copy(alpha = 0.22f))
                        )
                        Canvas(modifier = Modifier.size(96.dp)) {
                            val stroke = 4.dp.toPx()
                            drawArc(
                                color = Color(0xFF2B384A),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = SafetyAmber,
                                startAngle = -90f,
                                sweepAngle = animatedRingProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Surface(
                        onClick = onToggleRecord,
                        shape = CircleShape,
                        color = if (isRecording) StatusCriticalRed else SafetyAmber,
                        modifier = Modifier
                            .size(78.dp)
                            .minimumInteractiveComponentSize()
                            .border(
                                3.dp,
                                if (isRecording) Color.White.copy(alpha = 0.35f) else Color(0xFF553200),
                                CircleShape
                            )
                            .testTag("mechanical_audio_record_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = stringResource(
                                    if (isRecording) R.string.probe_stop_recording else R.string.probe_start_recording
                                ),
                                tint = if (isRecording) Color.White else Color(0xFF281800),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                if (hasAudioReady && !isRecording) {
                    Spacer(modifier = Modifier.width(20.dp))
                    IconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) {
                                    DiagnosticCyan.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            )
                            .border(
                                1.dp,
                                if (isPlaying) DiagnosticCyan else Color.Transparent,
                                CircleShape
                            )
                            .testTag("mechanical_audio_playback_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.audio_playback_stop else R.string.audio_playback_play
                            ),
                            tint = DiagnosticCyan
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }
            }

            Text(
                text = when {
                    isRecording -> stringResource(R.string.probe_hint_recording)
                    isPlaying -> stringResource(R.string.probe_hint_playing)
                    hasAudioReady -> stringResource(R.string.probe_hint_ready)
                    else -> stringResource(R.string.probe_hint_idle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SignalQualityPill(quality: AcousticSignalQuality) {
    val (labelRes, color) = when (quality) {
        AcousticSignalQuality.TOO_QUIET -> R.string.audio_signal_quiet to StatusWarnOrange
        AcousticSignalQuality.OPTIMAL -> R.string.audio_signal_optimal to StatusOkGreen
        AcousticSignalQuality.CLIPPING -> R.string.audio_signal_clipping to StatusCriticalRed
        AcousticSignalQuality.IDLE -> return
    }

    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.testTag("acoustic_signal_quality_pill")
    ) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MechanicalWaveformDisplay(
    samples: List<Float>,
    isRecording: Boolean,
    hasAudioReady: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f
) {
    val activeBrush = remember(isRecording, hasAudioReady) {
        when {
            isRecording -> Brush.verticalGradient(
                listOf(SafetyAmber, StatusCriticalRed, SafetyAmber)
            )
            hasAudioReady -> Brush.verticalGradient(
                listOf(DiagnosticCyan, StatusOkGreen, DiagnosticCyan)
            )
            else -> Brush.verticalGradient(
                listOf(DiagnosticCyan.copy(alpha = 0.55f), DiagnosticCyan.copy(alpha = 0.25f))
            )
        }
    }
    val playedBrush = remember {
        Brush.verticalGradient(listOf(SafetyAmber, StatusOkGreen, SafetyAmber))
    }

    val gridColor = Color(0xFF1B2736)
    val curveColor = when {
        isRecording -> Color(0xFFFFCA7A)
        isPlaying -> StatusOkGreen
        else -> DiagnosticCyan
    }

    val showIdleContour = !isRecording && !hasAudioReady && !isPlaying

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF080C12))
            .border(
                1.dp,
                when {
                    isRecording -> SafetyAmber.copy(alpha = 0.6f)
                    isPlaying -> DiagnosticCyan.copy(alpha = 0.65f)
                    else -> Color(0xFF233245)
                },
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // Subtle oscilloscope horizontal graticule
        listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
            val y = h * ratio
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (ratio == 0.5f) 1.5.dp.toPx() else 1.dp.toPx()
            )
        }

        val count = samples.size.coerceAtLeast(1)
        val gap = 3.5.dp.toPx()
        val barWidth = ((w - gap * (count - 1)) / count).coerceAtLeast(2.5f)
        val envelopePath = Path()
        val playheadX = (w * playbackProgress.coerceIn(0f, 1f))

        samples.forEachIndexed { index, amp ->
            val effectiveAmp = if (showIdleContour) {
                val centerWeight = 1f - kotlin.math.abs((index.toFloat() / (count - 1).coerceAtLeast(1)) - 0.5f) * 1.4f
                val wave = ((sin(0.9f + index * 0.42f) + 1f) * 0.5f)
                (0.08f + 0.18f * wave * centerWeight.coerceIn(0.25f, 1f)).coerceIn(0.08f, 0.34f)
            } else {
                amp.coerceIn(0.06f, 1f)
            }
            val barHeight = (h * effectiveAmp).coerceAtLeast(6.dp.toPx())
            val x = index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val isBarPlayed = isPlaying && (x + barWidth / 2f) <= playheadX

            drawRoundRect(
                brush = if (isBarPlayed) playedBrush else activeBrush,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            val pointX = x + barWidth / 2f
            val pointY = top
            if (index == 0) {
                envelopePath.moveTo(pointX, pointY)
            } else {
                envelopePath.lineTo(pointX, pointY)
            }
        }

        if (samples.size > 1) {
            drawPath(
                path = envelopePath,
                color = curveColor.copy(alpha = 0.75f),
                style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        if (isPlaying) {
            drawLine(
                color = SafetyAmber,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, h),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
