package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WorkshopSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var contentRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentRevealed = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (contentRevealed) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splash_logo_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splash_content_alpha"
    )

    val sweepTransition = rememberInfiniteTransition(label = "splash_gauge")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_sweep_angle"
    )
    val wavePhase by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_wave_phase"
    )

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(320)) + scaleOut(
            targetScale = 1.06f,
            animationSpec = tween(320, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A283A),
                            Color(0xFF111924),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .alpha(contentAlpha)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(168.dp)
                        .scale(logoScale)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.minDimension / 2f - 4.dp.toPx()

                        // Subtle instrument halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    DiagnosticCyan.copy(alpha = 0.20f),
                                    SafetyAmber.copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = outerRadius * 1.15f
                            ),
                            radius = outerRadius * 1.15f,
                            center = center
                        )

                        // Calibrated tachometer tick marks
                        val tickCount = 24
                        for (i in 0 until tickCount) {
                            val angleRad = (i * (360f / tickCount)) * (PI / 180.0)
                            val isMajor = i % 3 == 0
                            val tickInner = outerRadius - if (isMajor) 10.dp.toPx() else 6.dp.toPx()
                            val start = Offset(
                                x = center.x + (cos(angleRad) * tickInner).toFloat(),
                                y = center.y + (sin(angleRad) * tickInner).toFloat()
                            )
                            val end = Offset(
                                x = center.x + (cos(angleRad) * outerRadius).toFloat(),
                                y = center.y + (sin(angleRad) * outerRadius).toFloat()
                            )
                            drawLine(
                                color = if (isMajor) DiagnosticCyan.copy(alpha = 0.45f) else Color(0xFF2B3C52),
                                start = start,
                                end = end,
                                strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Sweeping diagnostic arcs
                        val stroke = 3.2.dp.toPx()
                        val inset = 14.dp.toPx()
                        drawArc(
                            color = SafetyAmber,
                            startAngle = sweepAngle,
                            sweepAngle = 105f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2f, size.height - inset * 2f),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = DiagnosticCyan,
                            startAngle = sweepAngle + 185f,
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = Offset(inset + 6.dp.toPx(), inset + 6.dp.toPx()),
                            size = Size(
                                size.width - (inset + 6.dp.toPx()) * 2f,
                                size.height - (inset + 6.dp.toPx()) * 2f
                            ),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground_art),
                        contentDescription = "PocketMechanic app emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(106.dp)
                            .clip(CircleShape)
                            .border(2.dp, DiagnosticCyan.copy(alpha = 0.55f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        1.dp,
                        SafetyAmber.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.app_tagline).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Live acoustic calibration equalizer bars
                Canvas(
                    modifier = Modifier
                        .width(148.dp)
                        .height(22.dp)
                ) {
                    val bars = 15
                    val gap = 4.dp.toPx()
                    val barW = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(3f)
                    val centerY = size.height / 2f
                    for (i in 0 until bars) {
                        val norm = ((sin(wavePhase + i * 0.55f) + 1f) / 2f).coerceIn(0.18f, 1f)
                        val barH = size.height * norm
                        val x = i * (barW + gap)
                        drawRoundRect(
                            color = if (i % 3 == 0) SafetyAmber else DiagnosticCyan.copy(alpha = 0.85f),
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.splash_calibrating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .alpha(contentAlpha)
            )
        }
    }
}
