package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.donation.BillingUiState
import com.example.donation.DonationConfig
import com.example.donation.DonationTier
import com.example.donation.PayPalPreset
import com.example.donation.QrCodeGenerator
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusOkGreen
import com.example.util.ExternalOpenOutcome
import com.example.util.ShareOutcome
import com.example.util.copyTextToClipboard
import com.example.util.openExternalUrl
import com.example.util.sharePlainText

enum class DonationChannelTab(val label: String) {
    GOOGLE_PLAY("Google Play"),
    PAYPAL_QR("PayPal & QR"),
    KO_FI("Ko-fi")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationModalSheet(
    billingState: BillingUiState,
    isActiveSupporter: Boolean,
    supporterTierId: String?,
    onSelectChannel: (DonationChannelTab) -> Unit,
    onPurchaseTier: (DonationTier) -> Unit,
    onExternalFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedChannel by remember { mutableStateOf(DonationChannelTab.GOOGLE_PLAY) }
    var selectedPayPalPreset by remember { mutableStateOf(PayPalPreset.FIVE) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val payPalUrl = remember(selectedPayPalPreset) {
        DonationConfig.buildPayPalUrl(selectedPayPalPreset.amountUsd)
    }
    val kofiUrl = remember { DonationConfig.buildKofiUrl() }

    LaunchedEffect(payPalUrl) {
        qrBitmap = QrCodeGenerator.generateBitmap(payPalUrl, sizePx = 480)
    }

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
                    color = SafetyAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isActiveSupporter) Icons.Rounded.Verified else Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = SafetyAmber
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActiveSupporter) "Thank You, Supporter!" else "Support PocketMechanic",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "100% ad-free indie DIY workshop tool. Pick any channel below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isActiveSupporter) {
                Surface(
                    color = StatusOkGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusOkGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, tint = StatusOkGreen)
                        Column {
                            Text(
                                text = "Active Supporter Status Unlocked",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusOkGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "All donation prompts are permanently muted.${supporterTierId?.let { " ($it)" }.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DonationChannelTab.entries.forEachIndexed { index, channel ->
                    SegmentedButton(
                        selected = selectedChannel == channel,
                        onClick = {
                            selectedChannel = channel
                            onSelectChannel(channel)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DonationChannelTab.entries.size
                        ),
                        icon = {
                            val icon = when (channel) {
                                DonationChannelTab.GOOGLE_PLAY -> Icons.Rounded.ShoppingBag
                                DonationChannelTab.PAYPAL_QR -> Icons.Rounded.QrCode2
                                DonationChannelTab.KO_FI -> Icons.Rounded.LocalCafe
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("donation_tab_${channel.name.lowercase()}")
                    ) {
                        Text(
                            text = channel.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedChannel,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "donation_channel_transition"
            ) { channelTab ->
                when (channelTab) {
                DonationChannelTab.GOOGLE_PLAY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "One-tap Google Play Billing — permanently activates your Active Supporter badge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        billingState.tiers.forEach { tierDisplay ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !billingState.isBusy) {
                                        onPurchaseTier(tierDisplay.tier)
                                    }
                                    .testTag("donation_play_tier_${tierDisplay.tier.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${tierDisplay.tier.badgeLabel} • ${tierDisplay.tier.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (tierDisplay.isPlayCatalogLoaded) {
                                                "Google Play verified SKU"
                                            } else {
                                                "1-Tap Google Play Checkout"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        color = SafetyAmber,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = tierDisplay.formattedPrice,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF281800),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (billingState.isBusy || billingState.isConnecting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SafetyAmber
                                )
                                Text(
                                    text = "Connecting to Google Play Billing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = !billingState.statusMessage.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = billingState.statusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                DonationChannelTab.PAYPAL_QR -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PayPalPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = selectedPayPalPreset == preset,
                                    onClick = { selectedPayPalPreset = preset },
                                    label = {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SafetyAmber.copy(alpha = 0.22f),
                                        selectedLabelColor = SafetyAmber
                                    ),
                                    modifier = Modifier.testTag("paypal_preset_${preset.name.lowercase()}")
                                )
                            }
                        }

                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .size(210.dp)
                                .border(2.dp, DiagnosticCyan.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Crossfade(
                                    targetState = qrBitmap,
                                    animationSpec = tween(240),
                                    label = "paypal_qr_fade"
                                ) { bmp ->
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "PayPal QR code for $payPalUrl",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("paypal_qr_image")
                                        )
                                    } else {
                                        CircularProgressIndicator(color = DiagnosticCyan)
                                    }
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = payPalUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DiagnosticCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val ok = context.copyTextToClipboard("PayPal Donation Link", payPalUrl)
                                        onExternalFeedback(
                                            if (ok) "PayPal link copied." else "Could not access clipboard."
                                        )
                                    },
                                    modifier = Modifier.testTag("paypal_copy_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy PayPal URL",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    when (
                                        context.sharePlainText(
                                            chooserTitle = "Share PayPal Tip Link",
                                            subject = "Support PocketMechanic",
                                            text = payPalUrl
                                        )
                                    ) {
                                        ShareOutcome.OpenedChooser -> Unit
                                        ShareOutcome.CopiedFallback ->
                                            onExternalFeedback("PayPal link copied.")
                                        ShareOutcome.Failed ->
                                            onExternalFeedback("Unable to share PayPal link.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("paypal_share_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }

                            Button(
                                onClick = {
                                    when (context.openExternalUrl(payPalUrl, "PayPal URL")) {
                                        ExternalOpenOutcome.Opened ->
                                            onExternalFeedback("Opening PayPal...")
                                        ExternalOpenOutcome.CopiedFallback ->
                                            onExternalFeedback("No browser found — PayPal link copied to clipboard.")
                                        is ExternalOpenOutcome.Failed ->
                                            onExternalFeedback("Unable to open PayPal link.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("paypal_open_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DiagnosticCyan,
                                    contentColor = Color(0xFF002029)
                                )
                            ) {
                                Text("Open PayPal")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                DonationChannelTab.KO_FI -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.LocalCafe,
                                    contentDescription = null,
                                    tint = SafetyAmber
                                )
                                Column {
                                    Text(
                                        text = "Buy the Developer a Ko-fi",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Quick web tip jar — zero account lock-in.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = kofiUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = DiagnosticCyan
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val ok = context.copyTextToClipboard("Ko-fi URL", kofiUrl)
                                        onExternalFeedback(
                                            if (ok) "Ko-fi link copied." else "Clipboard unavailable."
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("kofi_copy_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy")
                                }

                                Button(
                                    onClick = {
                                        when (context.openExternalUrl(kofiUrl, "Ko-fi URL")) {
                                            ExternalOpenOutcome.Opened ->
                                                onExternalFeedback("Opening Ko-fi...")
                                            ExternalOpenOutcome.CopiedFallback ->
                                                onExternalFeedback("No browser found — Ko-fi link copied to clipboard.")
                                            is ExternalOpenOutcome.Failed ->
                                                onExternalFeedback("Unable to open Ko-fi link.")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp)
                                        .testTag("kofi_open_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyAmber,
                                        contentColor = Color(0xFF281800)
                                    )
                                ) {
                                    Text("Open Ko-fi")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
