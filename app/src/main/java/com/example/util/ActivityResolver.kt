package com.example.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/**
 * Unwraps nested ContextWrappers (such as ContextThemeWrapper used by Jetpack Compose
 * or Hilt/View wrappers) to obtain the live hosting [Activity] required by Google Play
 * BillingClient, In-App Review, In-App Updates, and Intent choosers.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

/**
 * Returns a non-finishing, non-destroyed [Activity] if available.
 */
fun Context.findActiveActivity(): Activity? {
    val activity = findActivity() ?: return null
    return if (activity.isFinishing || activity.isDestroyed) null else activity
}

sealed interface ExternalOpenOutcome {
    data object Opened : ExternalOpenOutcome
    data object CopiedFallback : ExternalOpenOutcome
    data class Failed(val reason: String) : ExternalOpenOutcome
}

fun Context.copyTextToClipboard(label: String, text: String): Boolean {
    return runCatching {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)
}

fun Context.openExternalUrl(url: String, clipboardLabel: String = "Link"): ExternalOpenOutcome {
    val parsedUri = runCatching { Uri.parse(url) }.getOrNull()
        ?: return ExternalOpenOutcome.Failed("Invalid URL.")
    val viewIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (findActiveActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return try {
        val launcherContext: Context = findActiveActivity() ?: this
        launcherContext.startActivity(viewIntent)
        ExternalOpenOutcome.Opened
    } catch (_: ActivityNotFoundException) {
        if (copyTextToClipboard(clipboardLabel, url)) {
            ExternalOpenOutcome.CopiedFallback
        } else {
            ExternalOpenOutcome.Failed("No browser found to open $url")
        }
    } catch (e: Exception) {
        if (copyTextToClipboard(clipboardLabel, url)) {
            ExternalOpenOutcome.CopiedFallback
        } else {
            ExternalOpenOutcome.Failed(e.localizedMessage ?: "Unable to open link.")
        }
    }
}

sealed interface ShareOutcome {
    data object OpenedChooser : ShareOutcome
    data object CopiedFallback : ShareOutcome
    data object Failed : ShareOutcome
}

fun Context.sharePlainText(chooserTitle: String, subject: String, text: String): ShareOutcome {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
        if (findActiveActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return try {
        val launcherContext: Context = findActiveActivity() ?: this
        launcherContext.startActivity(chooser)
        ShareOutcome.OpenedChooser
    } catch (_: Exception) {
        if (copyTextToClipboard(subject, text)) {
            ShareOutcome.CopiedFallback
        } else {
            ShareOutcome.Failed
        }
    }
}

fun Context.openMapsSearch(query: String): ExternalOpenOutcome {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return ExternalOpenOutcome.Failed("Empty maps query.")
    val encoded = Uri.encode(cleanQuery)
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
        if (findActiveActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return try {
        val launcherContext: Context = findActiveActivity() ?: this
        launcherContext.startActivity(geoIntent)
        ExternalOpenOutcome.Opened
    } catch (_: Exception) {
        openExternalUrl(
            url = "https://www.google.com/maps/search/?api=1&query=$encoded",
            clipboardLabel = "Maps Search"
        )
    }
}

