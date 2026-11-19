package com.example.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.AppEventCategory
import com.example.analytics.AppEventNames
import com.example.analytics.AppEventTracker
import com.example.data.local.AppEventEntity
import com.example.donation.DonationConfig
import com.example.engagement.AutoPromptType
import com.example.engagement.EngagementPreferencesRepository
import com.example.engagement.EngagementPreferencesState
import com.example.engagement.EngagementScheduler
import com.example.engagement.PlayEngagementManager
import com.example.engagement.PlayReviewOutcome
import com.example.engagement.PlayUpdateOutcome
import com.example.engagement.PromptDecision
import com.example.engagement.PromptEvaluationInput
import com.example.engagement.PromptTriggerContext
import com.example.engagement.UpdateAvailabilitySnapshot
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticSeverity
import com.example.ui.components.DonationChannelTab
import com.example.util.ShareOutcome
import com.example.util.sharePlainText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EngagementViewModel(
    private val preferencesRepo: EngagementPreferencesRepository,
    private val eventTracker: AppEventTracker,
    private val playEngagementManager: PlayEngagementManager,
    private val userId: String,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val sessionStartedAtMillis = clock()
    private var sessionAutoPromptsShown = 0
    private var hasRecentFailureInSession = false
    private var latestUpdateSnapshot = UpdateAvailabilitySnapshot()

    private val hasReachedValueMomentInSession = MutableStateFlow(false)

    val engagementState: StateFlow<EngagementPreferencesState> =
        preferencesRepo.stateFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = EngagementPreferencesState()
        )

    val recentEvents: StateFlow<List<AppEventEntity>> =
        eventTracker.observeRecentEvents(userId, limit = 35).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val totalEventCount: StateFlow<Int> =
        eventTracker.observeTotalEventCount(userId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    /**
     * Reactively hides the inline support banner immediately if the user becomes an Active Supporter
     * or dismisses it, while strictly gating display until after a Value Realization moment in session.
     */
    val showInlineSupportBanner: StateFlow<Boolean> =
        combine(engagementState, hasReachedValueMomentInSession) { prefs, hasValueMoment ->
            hasValueMoment && EngagementScheduler.shouldShowInlineSupportBanner(prefs, clock())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val _activePopup = MutableStateFlow<PromptDecision.ShowAutoPopup?>(null)
    val activePopup: StateFlow<PromptDecision.ShowAutoPopup?> = _activePopup.asStateFlow()

    private val _isDonationSheetVisible = MutableStateFlow(false)
    val isDonationSheetVisible: StateFlow<Boolean> = _isDonationSheetVisible.asStateFlow()

    private val _lastEvaluationReason = MutableStateFlow("Waiting for core workshop session activity")
    val lastEvaluationReason: StateFlow<String> = _lastEvaluationReason.asStateFlow()

    private val _engagementNotice = MutableStateFlow<String?>(null)
    val engagementNotice: StateFlow<String?> = _engagementNotice.asStateFlow()

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            val prefs = preferencesRepo.recordAppLaunch(clock())
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.APP_LAUNCHED,
                category = AppEventCategory.LIFECYCLE,
                properties = mapOf(
                    "launchCount" to prefs.appLaunchCount,
                    "activeDays" to prefs.distinctActiveDays,
                    "supporter" to prefs.isActiveSupporter
                )
            )

            latestUpdateSnapshot = playEngagementManager.checkForUpdate()
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.UPDATE_CHECKED,
                category = AppEventCategory.ENGAGEMENT,
                properties = mapOf(
                    "available" to latestUpdateSnapshot.isUpdateAvailable,
                    "versionCode" to latestUpdateSnapshot.availableVersionCode,
                    "priority" to latestUpdateSnapshot.updatePriority
                )
            )

            evaluateAndMaybeTriggerPopup(
                prefs = prefs,
                triggerContext = PromptTriggerContext.APP_START
            )
        }
    }

    fun clearNotice() {
        _engagementNotice.value = null
    }

    fun onDiagnosisStarted(mode: DiagnosticMode) {
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.DIAGNOSIS_STARTED,
            category = AppEventCategory.DIAGNOSTIC,
            properties = mapOf("mode" to mode.name)
        )
    }

    fun onDiagnosisCompleted(
        mode: DiagnosticMode,
        severity: DiagnosticSeverity,
        confidencePercent: Int
    ) {
        viewModelScope.launch {
            hasRecentFailureInSession = false
            val prefs = preferencesRepo.recordSuccessfulDiagnosis()
            hasReachedValueMomentInSession.value = true
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.DIAGNOSIS_COMPLETED,
                category = AppEventCategory.DIAGNOSTIC,
                properties = mapOf(
                    "mode" to mode.name,
                    "severity" to severity.name,
                    "confidence" to confidencePercent,
                    "totalDiagnoses" to prefs.successfulDiagnosesCount
                )
            )
            evaluateAndMaybeTriggerPopup(
                prefs = prefs,
                triggerContext = PromptTriggerContext.DIAGNOSIS_COMPLETED,
                severity = severity,
                confidence = confidencePercent
            )
        }
    }

    fun onDiagnosisFailed(reason: String) {
        hasRecentFailureInSession = true
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.DIAGNOSIS_FAILED,
            category = AppEventCategory.DIAGNOSTIC,
            properties = mapOf("reason" to reason.take(120))
        )
    }

    fun onReportSaved() {
        viewModelScope.launch {
            val prefs = preferencesRepo.recordSavedReport()
            hasReachedValueMomentInSession.value = true
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.DIAGNOSIS_SAVED,
                category = AppEventCategory.DIAGNOSTIC,
                properties = mapOf("savedCount" to prefs.savedReportsCount)
            )
            evaluateAndMaybeTriggerPopup(
                prefs = prefs,
                triggerContext = PromptTriggerContext.REPORT_SAVED
            )
        }
    }

    fun onRepairResolved() {
        viewModelScope.launch {
            val prefs = preferencesRepo.recordRepairResolved()
            hasReachedValueMomentInSession.value = true
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.REPAIR_RESOLVED,
                category = AppEventCategory.DIAGNOSTIC,
                properties = mapOf("resolvedCount" to prefs.resolvedRepairsCount)
            )
            evaluateAndMaybeTriggerPopup(
                prefs = prefs,
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED
            )
        }
    }

    fun onFollowUpCompleted() {
        viewModelScope.launch {
            val prefs = preferencesRepo.recordFollowUpCompleted()
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.FOLLOW_UP_ASKED,
                category = AppEventCategory.DIAGNOSTIC,
                properties = mapOf("followUpCount" to prefs.followUpCount)
            )
        }
    }

    fun onHandsFreeTurnCompleted() {
        viewModelScope.launch {
            val prefs = preferencesRepo.currentState()
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.HANDS_FREE_TURN,
                category = AppEventCategory.UTILITY
            )
            evaluateAndMaybeTriggerPopup(
                prefs = prefs,
                triggerContext = PromptTriggerContext.HANDS_FREE_SUCCESS
            )
        }
    }

    fun onFlashlightToggled(isOn: Boolean) {
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.FLASHLIGHT_TOGGLED,
            category = AppEventCategory.UTILITY,
            properties = mapOf("torchOn" to isOn)
        )
    }

    fun onEquipmentSaved(isNew: Boolean) {
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.EQUIPMENT_SAVED,
            category = AppEventCategory.UTILITY,
            properties = mapOf("isNew" to isNew)
        )
    }

    private suspend fun evaluateAndMaybeTriggerPopup(
        prefs: EngagementPreferencesState,
        triggerContext: PromptTriggerContext,
        severity: DiagnosticSeverity? = null,
        confidence: Int = 0
    ) {
        if (_activePopup.value != null) return

        val decision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = prefs,
                triggerContext = triggerContext,
                nowMillis = clock(),
                sessionStartedAtMillis = sessionStartedAtMillis,
                sessionAutoPromptsShown = sessionAutoPromptsShown,
                updateInfo = latestUpdateSnapshot,
                lastDiagnosisSeverity = severity,
                lastDiagnosisConfidence = confidence,
                hasRecentFailureInSession = hasRecentFailureInSession
            )
        )

        when (decision) {
            is PromptDecision.None -> {
                _lastEvaluationReason.value = "${triggerContext.name}: ${decision.reason}"
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.PROMPT_SUPPRESSED,
                    category = AppEventCategory.ENGAGEMENT,
                    properties = mapOf(
                        "trigger" to triggerContext.name,
                        "reason" to decision.reason
                    )
                )
            }

            is PromptDecision.ShowAutoPopup -> {
                sessionAutoPromptsShown += 1
                _lastEvaluationReason.value = "Shown ${decision.type.name}: ${decision.reason}"
                preferencesRepo.recordAutoPromptShown(
                    type = decision.type,
                    milestoneValue = decision.milestoneValue,
                    nowMillis = clock()
                )
                eventTracker.track(
                    userId = userId,
                    eventName = AppEventNames.PROMPT_SHOWN,
                    category = AppEventCategory.ENGAGEMENT,
                    properties = mapOf(
                        "type" to decision.type.storageKey,
                        "trigger" to triggerContext.name,
                        "reason" to decision.reason
                    )
                )
                _activePopup.value = decision
            }
        }
    }

    fun dismissInlineSupportBanner() {
        viewModelScope.launch {
            preferencesRepo.recordDonationBannerDismissed(clock())
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.PROMPT_DISMISSED,
                category = AppEventCategory.ENGAGEMENT,
                properties = mapOf("type" to "inline_support_banner")
            )
        }
    }

    fun openDonationSheet(source: String) {
        _isDonationSheetVisible.value = true
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.DONATION_SHEET_OPENED,
            category = AppEventCategory.MONETIZATION,
            properties = mapOf("source" to source)
        )
    }

    fun dismissDonationSheet() {
        _isDonationSheetVisible.value = false
    }

    fun onDonationChannelSelected(channel: DonationChannelTab) {
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.DONATION_CHANNEL_SELECTED,
            category = AppEventCategory.MONETIZATION,
            properties = mapOf("channel" to channel.name.lowercase())
        )
    }

    fun showExternalFeedback(message: String) {
        _engagementNotice.value = message
    }

    fun acceptActivePopup(activity: Activity?) {
        val popup = _activePopup.value ?: return
        _activePopup.value = null
        eventTracker.track(
            userId = userId,
            eventName = AppEventNames.PROMPT_ACCEPTED,
            category = AppEventCategory.ENGAGEMENT,
            properties = mapOf("type" to popup.type.storageKey)
        )

        when (popup.type) {
            AutoPromptType.UPDATE_APP -> triggerManualUpdateCheck(activity)
            AutoPromptType.RATE_APP -> triggerManualRate(activity)
            AutoPromptType.SHARE_APP -> triggerManualShare(activity)
            AutoPromptType.DONATE_SUPPORT -> openDonationSheet(source = "milestone_popup")
        }
    }

    fun dismissActivePopup(optOut: Boolean) {
        val popup = _activePopup.value ?: return
        _activePopup.value = null
        val now = clock()
        viewModelScope.launch {
            when (popup.type) {
                AutoPromptType.UPDATE_APP -> {
                    preferencesRepo.recordUpdateDismissed(
                        versionCode = latestUpdateSnapshot.availableVersionCode,
                        skipVersion = optOut,
                        nowMillis = now
                    )
                }
                AutoPromptType.RATE_APP -> {
                    preferencesRepo.recordRateOutcome(ratedOrOptOut = optOut, nowMillis = now)
                }
                AutoPromptType.SHARE_APP -> {
                    preferencesRepo.recordShareOutcome(shared = false, optOut = optOut, nowMillis = now)
                }
                AutoPromptType.DONATE_SUPPORT -> Unit
            }
            eventTracker.track(
                userId = userId,
                eventName = if (optOut) AppEventNames.PROMPT_OPTED_OUT else AppEventNames.PROMPT_DISMISSED,
                category = AppEventCategory.ENGAGEMENT,
                properties = mapOf("type" to popup.type.storageKey)
            )
        }
    }

    fun triggerManualRate(activity: Activity?) {
        viewModelScope.launch {
            preferencesRepo.recordRateOutcome(ratedOrOptOut = true, nowMillis = clock())
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.RATE_FLOW_LAUNCHED,
                category = AppEventCategory.ENGAGEMENT
            )
            when (val outcome = playEngagementManager.launchInAppReview(activity)) {
                PlayReviewOutcome.CompletedInApp ->
                    _engagementNotice.value = "Thanks for rating PocketMechanic!"
                PlayReviewOutcome.OpenedStoreFallback ->
                    _engagementNotice.value = "Opening Google Play Store rating page..."
                is PlayReviewOutcome.Unavailable ->
                    _engagementNotice.value = outcome.message
            }
        }
    }

    fun triggerManualShare(activity: Activity?, customText: String? = null) {
        val pkg = activity?.packageName ?: "com.soloprono.pocketmechanic"
        val shareText = customText ?: DonationConfig.buildShareAppText(pkg)
        val outcome = activity?.sharePlainText(
            chooserTitle = "Share PocketMechanic",
            subject = "Try PocketMechanic — Visual & Audio DIY Diagnoser",
            text = shareText
        ) ?: ShareOutcome.Failed

        viewModelScope.launch {
            if (customText == null && outcome != ShareOutcome.Failed) {
                preferencesRepo.recordShareOutcome(shared = true, nowMillis = clock())
            }
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.APP_SHARED,
                category = AppEventCategory.ENGAGEMENT,
                properties = mapOf(
                    "outcome" to outcome::class.simpleName.orEmpty(),
                    "custom" to (customText != null)
                )
            )
        }
        when (outcome) {
            ShareOutcome.OpenedChooser -> Unit
            ShareOutcome.CopiedFallback ->
                _engagementNotice.value = "Share message copied to clipboard."
            ShareOutcome.Failed ->
                _engagementNotice.value = "Unable to open share sheet on this screen."
        }
    }

    fun triggerManualUpdateCheck(activity: Activity?) {
        viewModelScope.launch {
            eventTracker.track(
                userId = userId,
                eventName = AppEventNames.UPDATE_FLOW_LAUNCHED,
                category = AppEventCategory.ENGAGEMENT
            )
            when (val outcome = playEngagementManager.startAppUpdateOrFallback(activity)) {
                PlayUpdateOutcome.FlowLaunched ->
                    _engagementNotice.value = "Google Play update flow started."
                PlayUpdateOutcome.UpToDate ->
                    _engagementNotice.value = "PocketMechanic is already on the latest version."
                PlayUpdateOutcome.OpenedStoreFallback ->
                    _engagementNotice.value = "Checking Google Play Store for updates..."
                is PlayUpdateOutcome.Unavailable ->
                    _engagementNotice.value = outcome.message
            }
        }
    }
}
