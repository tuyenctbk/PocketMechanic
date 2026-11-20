package com.example.ui.components

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.hardware.CameraXManager
import com.example.hardware.TorchController
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.TorchGold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MechanicalCameraViewfinder(
    isTorchOn: Boolean,
    torchController: TorchController,
    onToggleTorch: () -> Unit,
    onCapture: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var bindAttempt by remember { mutableStateOf(0) }
    val cameraManager = remember(context, torchController, bindAttempt) {
        CameraXManager(context, torchController)
    }

    var isCapturing by remember { mutableStateOf(false) }
    var shutterFlashVisible by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }
    var bindErrorMessage by remember { mutableStateOf<String?>(null) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1100L)
            focusTapOffset = null
        }
    }

    LaunchedEffect(shutterFlashVisible) {
        if (shutterFlashVisible) {
            delay(140L)
            shutterFlashVisible = false
        }
    }

    DisposableEffect(lifecycleOwner, cameraManager, bindAttempt) {
        isCameraReady = false
        bindErrorMessage = null
        cameraManager.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onBound = { isCameraReady = true },
            onError = { msg ->
                bindErrorMessage = msg
                onError(msg)
            }
        )
        onDispose {
            cameraManager.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(1.5.dp, DiagnosticCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .pointerInput(cameraManager) {
                detectTapGestures { offset ->
                    focusTapOffset = offset
                    cameraManager.focusAtPoint(previewView, offset.x, offset.y)
                }
            }
            .testTag("camerax_viewfinder")
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (bindErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD0E131A))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = bindErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Button(
                        onClick = { bindAttempt++ },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.camera_retry))
                    }
                }
            }
        } else if (!isCameraReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DiagnosticCyan)
            }
        }

        // Precision Alignment Reticle Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val insetX = size.width * 0.16f
            val insetY = size.height * 0.20f
            val bracketLen = 24.dp.toPx()
            val strokeW = 2.5.dp.toPx()
            val left = insetX
            val right = size.width - insetX
            val top = insetY
            val bottom = size.height - insetY

            // Top-Left
            drawLine(SafetyAmber, Offset(left, top), Offset(left + bracketLen, top), strokeW, StrokeCap.Round)
            drawLine(SafetyAmber, Offset(left, top), Offset(left, top + bracketLen), strokeW, StrokeCap.Round)
            // Top-Right
            drawLine(SafetyAmber, Offset(right, top), Offset(right - bracketLen, top), strokeW, StrokeCap.Round)
            drawLine(SafetyAmber, Offset(right, top), Offset(right, top + bracketLen), strokeW, StrokeCap.Round)
            // Bottom-Left
            drawLine(SafetyAmber, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeW, StrokeCap.Round)
            drawLine(SafetyAmber, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeW, StrokeCap.Round)
            // Bottom-Right
            drawLine(SafetyAmber, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeW, StrokeCap.Round)
            drawLine(SafetyAmber, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeW, StrokeCap.Round)
        }

        // Tap-to-Focus Indicator Ring
        val tapPos = focusTapOffset
        val density = LocalDensity.current
        val focusRingScale by animateFloatAsState(
            targetValue = if (tapPos != null) 1f else 1.35f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "focus_ring_scale"
        )
        AnimatedVisibility(
            visible = tapPos != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (tapPos != null) {
                val halfSizePx = with(density) { 26.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (tapPos.x - halfSizePx).roundToInt(),
                                (tapPos.y - halfSizePx).roundToInt()
                            )
                        }
                        .size(52.dp)
                        .scale(focusRingScale)
                        .border(2.dp, SafetyAmber, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CenterFocusStrong,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Top Controls: Torch Toggle + Hint + Close
        Surface(
            color = if (isTorchOn) TorchGold else Color(0xB30E131A),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClick = onToggleTorch)
                .testTag("viewfinder_torch_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = stringResource(R.string.torch_toggle),
                    tint = if (isTorchOn) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(if (isTorchOn) R.string.camera_torch_on else R.string.camera_torch_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTorchOn) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            color = Color(0x990E131A),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_tap_focus),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xB3000000))
                .testTag("camera_close_button")
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.camera_close), tint = Color.White)
        }

        // Bottom Controls: Quick Zoom Pills + Shutter Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom Selector
            Surface(
                color = Color(0xB30E131A),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0f to "1x", 0.35f to "1.5x", 0.7f to "2x").forEach { (zoomVal, label) ->
                        val selected = kotlin.math.abs(linearZoom - zoomVal) < 0.05f
                        val pillBg by animateColorAsState(
                            targetValue = if (selected) DiagnosticCyan else Color.Transparent,
                            animationSpec = tween(180),
                            label = "zoom_pill_bg"
                        )
                        val pillFg by animateColorAsState(
                            targetValue = if (selected) Color(0xFF002536) else Color.White,
                            animationSpec = tween(180),
                            label = "zoom_pill_fg"
                        )
                        Surface(
                            color = pillBg,
                            shape = CircleShape,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable {
                                    linearZoom = zoomVal
                                    cameraManager.setLinearZoom(zoomVal)
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = pillFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isCapturing) return@Button
                    isCapturing = true
                    shutterFlashVisible = true
                    cameraManager.capturePhoto(
                        onCaptured = { bmp ->
                            isCapturing = false
                            onCapture(bmp)
                        },
                        onError = { msg ->
                            isCapturing = false
                            onError(msg)
                        }
                    )
                },
                enabled = !isCapturing,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("camera_shutter_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = Color(0xFF281800)
                )
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF281800),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.camera_capturing), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.camera_snap_part), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Brief visual shutter flash overlay on capture
        AnimatedVisibility(
            visible = shutterFlashVisible,
            enter = fadeIn(tween(45)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }
    }
}
