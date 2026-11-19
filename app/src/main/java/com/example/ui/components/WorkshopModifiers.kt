package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SafetyAmber

/**
 * Returns true when running on an Android TV / Leanback television device.
 */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTelevisionDevice() }
}

fun Context.isTelevisionDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Adds a crisp D-pad / keyboard focus ring and subtle spring lift for Android TV
 * and keyboard accessibility without cluttering touch-only interactions.
 */
fun Modifier.workshopFocusRing(
    shape: Shape,
    focusColor: Color = SafetyAmber,
    unfocusedColor: Color = Color.Transparent,
    focusedBorderWidth: Dp = 2.5.dp,
    focusedScale: Float = 1.02f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "workshop_focus_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) focusColor else unfocusedColor,
        animationSpec = tween(160),
        label = "workshop_focus_border"
    )

    this
        .scale(scale)
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused || unfocusedColor != Color.Transparent) {
                Modifier.border(
                    width = if (isFocused) focusedBorderWidth else 1.dp,
                    color = borderColor,
                    shape = shape
                )
            } else {
                Modifier
            }
        )
}
