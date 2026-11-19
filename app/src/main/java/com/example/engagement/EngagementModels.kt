package com.example.engagement

import com.example.model.DiagnosticSeverity

enum class AutoPromptType(val storageKey: String, val displayLabel: String) {
    UPDATE_APP("update_app", "App Update"),
    RATE_APP("rate_app", "Rate PocketMechanic"),
    SHARE_APP("share_app", "Share PocketMechanic"),
    DONATE_SUPPORT("donate_support", "Support Milestone")
}

enum class PromptTriggerContext {
    APP_START,
    DIAGNOSIS_COMPLETED,
    REPAIR_RESOLVED,
    REPORT_SAVED,
    HANDS_FREE_SUCCESS
}

data class UpdateAvailabilitySnapshot(
    val isUpdateAvailable: Boolean = false,
    val availableVersionCode: Int = 0,
    val currentVersionCode: Int = 1,
    val updatePriority: Int = 0,
    val stalenessDays: Int = 0
)

data class EngagementPreferencesState(
    val firstInstallAtMillis: Long = 0L,
    val appLaunchCount: Int = 0,
    val lastLaunchAtMillis: Long = 0L,
    val distinctActiveDays: Int = 0,
    val lastActiveDayEpoch: Long = 0L,
    val successfulDiagnosesCount: Int = 0,
    val resolvedRepairsCount: Int = 0,
    val savedReportsCount: Int = 0,
    val followUpCount: Int = 0,
    val lastAutoPromptAtMillis: Long = 0L,
    val lastAutoPromptType: String? = null,
    val lastUpdatePromptAtMillis: Long = 0L,
    val dismissedUpdateVersionCode: Int = 0,
    val lastRatePromptAtMillis: Long = 0L,
    val ratePromptDismissCount: Int = 0,
    val hasRatedApp: Boolean = false,
    val lastSharePromptAtMillis: Long = 0L,
    val sharePromptDismissCount: Int = 0,
    val hasSharedApp: Boolean = false,
    val lastDonationBannerDismissedAtMillis: Long = 0L,
    val lastDonationAutoPromptAtMillis: Long = 0L,
    val lastDonationMilestonePrompted: Int = 0,
    val isActiveSupporter: Boolean = false,
    val supporterTierId: String? = null
) {
    fun daysSinceInstall(nowMillis: Long): Int {
        if (firstInstallAtMillis <= 0L || nowMillis <= firstInstallAtMillis) return 0
        return ((nowMillis - firstInstallAtMillis) / EngagementScheduler.DAY_MS).toInt()
    }

    fun satisfactionScore(): Int {
        return (successfulDiagnosesCount * 2) +
            (resolvedRepairsCount * 3) +
            (savedReportsCount * 2) +
            followUpCount +
            distinctActiveDays
    }
}

data class PromptEvaluationInput(
    val prefs: EngagementPreferencesState,
    val triggerContext: PromptTriggerContext,
    val nowMillis: Long,
    val sessionStartedAtMillis: Long,
    val sessionAutoPromptsShown: Int,
    val updateInfo: UpdateAvailabilitySnapshot = UpdateAvailabilitySnapshot(),
    val lastDiagnosisSeverity: DiagnosticSeverity? = null,
    val lastDiagnosisConfidence: Int = 0,
    val hasRecentFailureInSession: Boolean = false
)

sealed interface PromptDecision {
    data class None(val reason: String) : PromptDecision
    data class ShowAutoPopup(
        val type: AutoPromptType,
        val title: String,
        val message: String,
        val primaryActionLabel: String,
        val secondaryActionLabel: String,
        val optOutActionLabel: String? = null,
        val reason: String,
        val milestoneValue: Int = 0,
        val score: Int = 0
    ) : PromptDecision
}
