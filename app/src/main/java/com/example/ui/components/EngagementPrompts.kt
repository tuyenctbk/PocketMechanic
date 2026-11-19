package com.example.ui.components

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppEventEntity
import com.example.engagement.AutoPromptType
import com.example.engagement.EngagementPreferencesState
import com.example.engagement.PromptDecision
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusOkGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveSupporterBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SafetyAmber.copy(alpha = 0.18f),
        shape = CircleShape,
        modifier = modifier
            .border(1.dp, SafetyAmber.copy(alpha = 0.65f), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("active_supporter_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = stringResource(R.string.active_supporter_badge),
                tint = SafetyAmber,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.active_supporter_badge).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SafetyAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InlinePostDiagnosisSupportCard(
    onSupportClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafetyAmber.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .testTag("inline_post_operation_support_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafetyAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalCafe,
                            contentDescription = null,
                            tint = SafetyAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.support_inline_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.support_inline_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_inline_support_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onSupportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inline_support_cta_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber.copy(alpha = 0.22f),
                    contentColor = SafetyAmber
                )
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.support_view_options), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SmartEngagementPopupDialog(
    prompt: PromptDecision.ShowAutoPopup,
    onPrimaryAction: () -> Unit,
    onSecondaryLater: () -> Unit,
    onOptOut: (() -> Unit)? = null
) {
    val (icon, tint) = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> Icons.Rounded.SystemUpdate to DiagnosticCyan
        AutoPromptType.RATE_APP -> Icons.Rounded.Star to SafetyAmber
        AutoPromptType.SHARE_APP -> Icons.Rounded.Share to StatusOkGreen
        AutoPromptType.DONATE_SUPPORT -> Icons.Rounded.Favorite to SafetyAmber
    }

    val localizedTitle = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> stringResource(R.string.update_app_title)
        AutoPromptType.RATE_APP -> stringResource(R.string.rate_app_title)
        AutoPromptType.SHARE_APP -> stringResource(R.string.share_popup_title)
        AutoPromptType.DONATE_SUPPORT -> stringResource(R.string.milestone_popup_title, prompt.milestoneValue)
    }
    val localizedMessage = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> stringResource(R.string.update_app_message)
        AutoPromptType.RATE_APP -> stringResource(R.string.rate_app_message)
        AutoPromptType.SHARE_APP -> stringResource(R.string.share_popup_message)
        AutoPromptType.DONATE_SUPPORT -> stringResource(R.string.milestone_popup_message)
    }
    val localizedPrimary = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> stringResource(R.string.update_app_primary)
        AutoPromptType.RATE_APP -> stringResource(R.string.rate_app_primary)
        AutoPromptType.SHARE_APP -> stringResource(R.string.share_popup_primary)
        AutoPromptType.DONATE_SUPPORT -> stringResource(R.string.milestone_popup_primary)
    }
    val localizedSecondary = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> stringResource(R.string.update_app_later)
        AutoPromptType.RATE_APP -> stringResource(R.string.rate_app_later)
        AutoPromptType.SHARE_APP -> stringResource(R.string.share_popup_later)
        AutoPromptType.DONATE_SUPPORT -> stringResource(R.string.milestone_popup_later)
    }
    val localizedOptOut = when (prompt.type) {
        AutoPromptType.UPDATE_APP -> stringResource(R.string.update_app_opt_out)
        AutoPromptType.RATE_APP -> stringResource(R.string.rate_app_opt_out)
        AutoPromptType.SHARE_APP -> stringResource(R.string.share_popup_opt_out)
        AutoPromptType.DONATE_SUPPORT -> null
    }

    AlertDialog(
        onDismissRequest = onSecondaryLater,
        icon = {
            Surface(
                color = tint.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = localizedTitle,
                        tint = tint
                    )
                }
            }
        },
        title = {
            Text(
                text = localizedTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = localizedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onOptOut != null && localizedOptOut != null) {
                    TextButton(
                        onClick = onOptOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engagement_popup_optout_button")
                    ) {
                        Text(
                            text = localizedOptOut,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPrimaryAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint,
                    contentColor = Color(0xFF121820)
                ),
                modifier = Modifier.testTag("engagement_popup_primary_button")
            ) {
                Text(localizedPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSecondaryLater,
                modifier = Modifier.testTag("engagement_popup_later_button")
            ) {
                Text(localizedSecondary)
            }
        }
    )
}

@Composable
fun SupportAboutAndTelemetrySection(
    engagementState: EngagementPreferencesState,
    recentEvents: List<AppEventEntity>,
    totalEventCount: Int,
    lastEvaluationReason: String,
    onOpenDonationSheet: () -> Unit,
    onRateApp: () -> Unit,
    onShareApp: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActivityLog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("garage_support_about_section")
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
                        text = stringResource(R.string.support_about_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = SafetyAmber
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} • ${stringResource(R.string.support_development_subtitle)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (engagementState.isActiveSupporter) {
                    ActiveSupporterBadge(onClick = onOpenDonationSheet)
                }
            }

            Button(
                onClick = onOpenDonationSheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("garage_support_development_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = Color(0xFF281800)
                )
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (engagementState.isActiveSupporter) {
                        stringResource(R.string.support_supporter_options)
                    } else {
                        stringResource(R.string.support_development_title)
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRateApp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("garage_rate_app_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_rate), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onShareApp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("garage_share_app_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusOkGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_share), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onCheckUpdates,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("garage_check_updates_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp), tint = DiagnosticCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_update), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("garage_privacy_policy_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Policy, contentDescription = null, modifier = Modifier.size(16.dp), tint = DiagnosticCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.privacy_policy_title), style = MaterialTheme.typography.labelMedium)
            }

            // Expandable App Event Tracking & Smart Timing Inspector
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showActivityLog = !showActivityLog }
                    .testTag("toggle_event_log_inspector")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Analytics,
                            contentDescription = null,
                            tint = DiagnosticCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.event_log_label, totalEventCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showActivityLog) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showActivityLog) "Collapse event log" else "Expand event log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = showActivityLog) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Launches: ${engagementState.appLaunchCount} • Diagnoses: ${engagementState.successfulDiagnosesCount} • Fixed: ${engagementState.resolvedRepairsCount} • Score: ${engagementState.satisfactionScore()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DiagnosticCyan
                    )
                    Text(
                        text = "Scheduler: $lastEvaluationReason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (recentEvents.isEmpty()) {
                        Text(
                            text = "No logged events yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentEvents.take(8).forEach { ev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${ev.category}: ${ev.eventName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateFormat.format(Date(ev.createdAtMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.privacy_policy_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = false
                            loadUrl("file:///android_asset/privacy_policy.html")
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
