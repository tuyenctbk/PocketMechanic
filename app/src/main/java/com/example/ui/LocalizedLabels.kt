package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ai.AiEngineSource
import com.example.ai.AiProviderMode
import com.example.donation.DonationTier
import com.example.donation.PayPalPreset
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarningLevel

@StringRes
fun EquipmentCategory.labelRes(): Int = when (this) {
    EquipmentCategory.VEHICLE -> R.string.cat_vehicle
    EquipmentCategory.APPLIANCE -> R.string.cat_appliance
    EquipmentCategory.PLUMBING -> R.string.cat_plumbing
    EquipmentCategory.HVAC -> R.string.cat_hvac
    EquipmentCategory.YARD_TOOL -> R.string.cat_yard
    EquipmentCategory.OTHER -> R.string.cat_other
}

@Composable
fun EquipmentCategory.localizedName(): String = stringResource(labelRes())

@StringRes
fun DiagnosticMode.labelRes(): Int = when (this) {
    DiagnosticMode.AUDIO -> R.string.mode_audio
    DiagnosticMode.VISION -> R.string.mode_vision
    DiagnosticMode.VOICE -> R.string.mode_voice
    DiagnosticMode.TEXT -> R.string.mode_text
}

@Composable
fun DiagnosticMode.localizedName(): String = stringResource(labelRes())

@StringRes
fun DiagnosticSeverity.labelRes(): Int = when (this) {
    DiagnosticSeverity.LOW -> R.string.severity_low
    DiagnosticSeverity.MEDIUM -> R.string.severity_medium
    DiagnosticSeverity.HIGH -> R.string.severity_high
    DiagnosticSeverity.CRITICAL -> R.string.severity_critical
}

@Composable
fun DiagnosticSeverity.localizedLabel(): String = stringResource(labelRes())

@StringRes
fun SafetyWarningLevel.labelRes(): Int = when (this) {
    SafetyWarningLevel.DANGER -> R.string.safety_level_danger
    SafetyWarningLevel.WARNING -> R.string.safety_level_warning
    SafetyWarningLevel.CAUTION -> R.string.safety_level_caution
}

@Composable
fun SafetyWarningLevel.localizedLabel(): String = stringResource(labelRes())

@StringRes
fun SafetyHazardType.labelRes(): Int = when (this) {
    SafetyHazardType.ELECTRICAL -> R.string.hazard_electrical
    SafetyHazardType.THERMAL -> R.string.hazard_hot
    SafetyHazardType.PRESSURE -> R.string.hazard_pressure
    SafetyHazardType.MECHANICAL -> R.string.hazard_moving
    SafetyHazardType.CHEMICAL -> R.string.hazard_fuel
    SafetyHazardType.STRUCTURAL -> R.string.hazard_lift
    SafetyHazardType.GENERAL -> R.string.hazard_general
}

@Composable
fun SafetyHazardType.localizedLabel(): String = stringResource(labelRes())

@StringRes
fun AiProviderMode.labelRes(): Int = when (this) {
    AiProviderMode.AUTO_SMART -> R.string.ai_mode_auto
    AiProviderMode.FREE_SHARED -> R.string.ai_mode_free
    AiProviderMode.BYOK_GEMINI -> R.string.ai_mode_byok
    AiProviderMode.OFFLINE_ONLY -> R.string.ai_mode_offline
}

@StringRes
fun AiProviderMode.shortLabelRes(): Int = when (this) {
    AiProviderMode.AUTO_SMART -> R.string.ai_mode_auto_short
    AiProviderMode.FREE_SHARED -> R.string.ai_mode_free_short
    AiProviderMode.BYOK_GEMINI -> R.string.ai_mode_byok_short
    AiProviderMode.OFFLINE_ONLY -> R.string.ai_mode_offline_short
}

@Composable
fun AiProviderMode.localizedLabel(): String = stringResource(labelRes())

@Composable
fun AiProviderMode.localizedShortLabel(): String = stringResource(shortLabelRes())

@StringRes
fun AiEngineSource.badgeLabelRes(): Int = when (this) {
    AiEngineSource.FREE_SHARED_FLASH -> R.string.ai_source_free_flash
    AiEngineSource.FREE_SHARED_LITE_FALLBACK -> R.string.ai_source_free_lite
    AiEngineSource.BYOK_PERSONAL_KEY -> R.string.ai_source_byok
    AiEngineSource.OFFLINE_REFERENCE -> R.string.ai_source_offline
}

@Composable
fun AiEngineSource.localizedBadgeLabel(): String = stringResource(badgeLabelRes())

@StringRes
fun DonationTier.titleRes(): Int = when (this) {
    DonationTier.TIER_1 -> R.string.donate_tier_coffee
    DonationTier.TIER_2 -> R.string.donate_tier_wrench
    DonationTier.TIER_3 -> R.string.donate_tier_pro
    DonationTier.TIER_4 -> R.string.donate_tier_master
}

@Composable
fun DonationTier.localizedTitle(): String = stringResource(titleRes())

@Composable
fun PayPalPreset.localizedLabel(): String = when (this) {
    PayPalPreset.ANY -> stringResource(R.string.donate_any_amount)
    else -> label
}
