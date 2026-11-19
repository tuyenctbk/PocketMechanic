package com.example.engagement

import com.example.model.DiagnosticSeverity

/**
 * Pure, deterministic scheduling algorithm that selects the best auto-popup moment
 * while strictly preventing startup interruptions, over-prompting, and repeat spam.
 */
object EngagementScheduler {

    const val SECOND_MS = 1_000L
    const val HOUR_MS = 3_600L * SECOND_MS
    const val DAY_MS = 24L * HOUR_MS

    const val MIN_SESSION_WARMUP_MS = 8L * SECOND_MS
    const val MAX_AUTO_PROMPTS_PER_SESSION = 1

    const val GLOBAL_CROSS_PROMPT_COOLDOWN_MS = 72L * HOUR_MS
    const val UPDATE_DEFER_COOLDOWN_MS = 7L * DAY_MS
    const val UPDATE_SAME_VERSION_SKIP_COOLDOWN_MS = 14L * DAY_MS
    const val RATE_COOLDOWN_MS = 30L * DAY_MS
    const val SHARE_DISMISS_COOLDOWN_MS = 21L * DAY_MS
    const val SHARE_AFTER_SHARED_COOLDOWN_MS = 45L * DAY_MS
    const val SHARE_HIGH_DISMISS_COOLDOWN_MS = 60L * DAY_MS
    const val DONATION_AUTO_PROMPT_COOLDOWN_MS = 30L * DAY_MS
    const val DONATION_INLINE_BANNER_COOLDOWN_MS = 14L * DAY_MS

    const val MIN_DAYS_FOR_ENGAGEMENT = 2
    const val MIN_LAUNCHES_FOR_ENGAGEMENT = 3
    const val MIN_DIAGNOSES_FOR_RATE = 3
    const val MIN_DIAGNOSES_FOR_SHARE = 4
    const val MIN_DIAGNOSES_FOR_MILESTONE = 7
    const val MAX_RATE_DISMISSALS = 3
    const val MAX_SHARE_DISMISSALS = 3

    val DONATION_MILESTONES = listOf(7, 10, 25, 50)

    fun evaluate(input: PromptEvaluationInput): PromptDecision {
        val prefs = input.prefs
        val now = input.nowMillis

        if (input.sessionAutoPromptsShown >= MAX_AUTO_PROMPTS_PER_SESSION) {
            return PromptDecision.None("Session auto-popup cap ($MAX_AUTO_PROMPTS_PER_SESSION) reached")
        }

        // Global cooldown prevents back-to-back popups across different types (e.g. Update today, Rate tomorrow).
        if (prefs.lastAutoPromptAtMillis > 0L &&
            (now - prefs.lastAutoPromptAtMillis) < GLOBAL_CROSS_PROMPT_COOLDOWN_MS
        ) {
            return PromptDecision.None("Global 72h cross-prompt cooldown active")
        }

        // Never interrupt emergency / critical safety hazard inspections with promotional popups.
        if (input.lastDiagnosisSeverity == DiagnosticSeverity.CRITICAL &&
            input.triggerContext != PromptTriggerContext.REPAIR_RESOLVED
        ) {
            return PromptDecision.None("Suppressed during CRITICAL safety hazard diagnosis")
        }

        // Hard Startup Guard: NEVER show Rate, Share, or Donate at app start.
        // Only surface a genuinely urgent Play Store update on startup if stale/high-priority.
        if (input.triggerContext == PromptTriggerContext.APP_START) {
            val updateCandidate = evaluateUpdateCandidate(input, isAppStart = true)
            return updateCandidate ?: PromptDecision.None("App start shielded from non-critical popups")
        }

        // Session warmup guard: never pop up within the first 8 seconds of launch.
        if ((now - input.sessionStartedAtMillis) < MIN_SESSION_WARMUP_MS) {
            return PromptDecision.None("Session warmup threshold not reached")
        }

        val candidates = buildList {
            evaluateUpdateCandidate(input, isAppStart = false)?.let(::add)
            if (!input.hasRecentFailureInSession) {
                evaluateRateCandidate(input)?.let(::add)
                evaluateDonationMilestoneCandidate(input)?.let(::add)
                evaluateShareCandidate(input)?.let(::add)
            }
        }

        if (candidates.isEmpty()) {
            return PromptDecision.None("No prompt met satisfaction and cooldown thresholds")
        }

        return candidates.maxBy { it.score }
    }

    fun shouldShowInlineSupportBanner(
        prefs: EngagementPreferencesState,
        nowMillis: Long
    ): Boolean {
        if (prefs.isActiveSupporter) return false
        if (prefs.daysSinceInstall(nowMillis) < MIN_DAYS_FOR_ENGAGEMENT) return false
        if (prefs.appLaunchCount < MIN_LAUNCHES_FOR_ENGAGEMENT) return false
        if (prefs.successfulDiagnosesCount < 3) return false
        if (prefs.lastDonationBannerDismissedAtMillis > 0L &&
            (nowMillis - prefs.lastDonationBannerDismissedAtMillis) < DONATION_INLINE_BANNER_COOLDOWN_MS
        ) {
            return false
        }
        return true
    }

    private fun evaluateUpdateCandidate(
        input: PromptEvaluationInput,
        isAppStart: Boolean
    ): PromptDecision.ShowAutoPopup? {
        val update = input.updateInfo
        val prefs = input.prefs
        val now = input.nowMillis

        if (!update.isUpdateAvailable) return null
        if (update.availableVersionCode <= update.currentVersionCode) return null

        // If user already deferred this exact version, respect a 14-day cooldown for that version.
        if (prefs.dismissedUpdateVersionCode == update.availableVersionCode &&
            prefs.lastUpdatePromptAtMillis > 0L &&
            (now - prefs.lastUpdatePromptAtMillis) < UPDATE_SAME_VERSION_SKIP_COOLDOWN_MS
        ) {
            return null
        }

        if (prefs.lastUpdatePromptAtMillis > 0L &&
            (now - prefs.lastUpdatePromptAtMillis) < UPDATE_DEFER_COOLDOWN_MS
        ) {
            return null
        }

        // At app start, only notify if Play Store flags high priority (>=4) or update is >=5 days stale
        // and user is past their very first launch.
        if (isAppStart) {
            val isUrgent = update.updatePriority >= 4 || update.stalenessDays >= 5
            if (!isUrgent || prefs.appLaunchCount < 2) return null
        }

        val score = 80 + (update.updatePriority * 5) + update.stalenessDays.coerceAtMost(14)
        return PromptDecision.ShowAutoPopup(
            type = AutoPromptType.UPDATE_APP,
            title = "Workshop Update Ready",
            message = "A newer version of PocketMechanic is available with improved acoustic & diagnostic accuracy.",
            primaryActionLabel = "Update Now",
            secondaryActionLabel = "Later",
            optOutActionLabel = "Skip Version",
            reason = "Play update v${update.availableVersionCode} available (priority=${update.updatePriority}, staleness=${update.stalenessDays}d)",
            score = score
        )
    }

    private fun evaluateRateCandidate(input: PromptEvaluationInput): PromptDecision.ShowAutoPopup? {
        val prefs = input.prefs
        val now = input.nowMillis

        if (prefs.hasRatedApp) return null
        if (prefs.ratePromptDismissCount >= MAX_RATE_DISMISSALS) return null
        if (prefs.daysSinceInstall(now) < MIN_DAYS_FOR_ENGAGEMENT) return null
        if (prefs.appLaunchCount < MIN_LAUNCHES_FOR_ENGAGEMENT) return null
        if (prefs.successfulDiagnosesCount < MIN_DIAGNOSES_FOR_RATE) return null

        if (prefs.lastRatePromptAtMillis > 0L &&
            (now - prefs.lastRatePromptAtMillis) < RATE_COOLDOWN_MS
        ) {
            return null
        }

        val contextFitScore = when (input.triggerContext) {
            PromptTriggerContext.REPAIR_RESOLVED -> 95
            PromptTriggerContext.DIAGNOSIS_COMPLETED -> if (input.lastDiagnosisConfidence >= 70) 82 else return null
            PromptTriggerContext.REPORT_SAVED -> 78
            PromptTriggerContext.HANDS_FREE_SUCCESS -> 74
            PromptTriggerContext.APP_START -> return null
        }

        val engagementBonus = (prefs.satisfactionScore() / 2).coerceAtMost(15)
        return PromptDecision.ShowAutoPopup(
            type = AutoPromptType.RATE_APP,
            title = "Did PocketMechanic Save You a Trip?",
            message = "If our diagnostics helped pinpoint the issue, a quick Play Store rating helps other DIYers find us.",
            primaryActionLabel = "Rate App",
            secondaryActionLabel = "Maybe Later",
            optOutActionLabel = "Already Rated / Don't Ask",
            reason = "Satisfied user (ops=${prefs.successfulDiagnosesCount}, days=${prefs.daysSinceInstall(now)}, ctx=${input.triggerContext})",
            score = contextFitScore + engagementBonus
        )
    }

    private fun evaluateDonationMilestoneCandidate(
        input: PromptEvaluationInput
    ): PromptDecision.ShowAutoPopup? {
        val prefs = input.prefs
        val now = input.nowMillis

        if (prefs.isActiveSupporter) return null
        if (prefs.daysSinceInstall(now) < MIN_DAYS_FOR_ENGAGEMENT) return null
        if (prefs.appLaunchCount < MIN_LAUNCHES_FOR_ENGAGEMENT) return null
        if (prefs.successfulDiagnosesCount < MIN_DIAGNOSES_FOR_MILESTONE) return null

        if (prefs.lastDonationAutoPromptAtMillis > 0L &&
            (now - prefs.lastDonationAutoPromptAtMillis) < DONATION_AUTO_PROMPT_COOLDOWN_MS
        ) {
            return null
        }

        val reachedMilestone = DONATION_MILESTONES
            .lastOrNull { it <= prefs.successfulDiagnosesCount && it > prefs.lastDonationMilestonePrompted }
            ?: return null

        val contextFit = when (input.triggerContext) {
            PromptTriggerContext.REPAIR_RESOLVED -> 90
            PromptTriggerContext.REPORT_SAVED -> 86
            PromptTriggerContext.DIAGNOSIS_COMPLETED -> if (input.lastDiagnosisConfidence >= 70) 84 else return null
            PromptTriggerContext.HANDS_FREE_SUCCESS -> 78
            PromptTriggerContext.APP_START -> return null
        }

        return PromptDecision.ShowAutoPopup(
            type = AutoPromptType.DONATE_SUPPORT,
            title = "Workshop Milestone: $reachedMilestone Diagnoses!",
            message = "PocketMechanic stays 100% ad-free thanks to community supporters. Want to buy the dev a coffee?",
            primaryActionLabel = "Support Options",
            secondaryActionLabel = "Not Now",
            optOutActionLabel = null,
            reason = "Reached diagnosis milestone $reachedMilestone (cooldown clear)",
            milestoneValue = reachedMilestone,
            score = contextFit
        )
    }

    private fun evaluateShareCandidate(input: PromptEvaluationInput): PromptDecision.ShowAutoPopup? {
        val prefs = input.prefs
        val now = input.nowMillis

        if (prefs.sharePromptDismissCount >= MAX_SHARE_DISMISSALS) return null
        if (prefs.daysSinceInstall(now) < MIN_DAYS_FOR_ENGAGEMENT) return null
        if (prefs.appLaunchCount < MIN_LAUNCHES_FOR_ENGAGEMENT) return null
        if (prefs.successfulDiagnosesCount < MIN_DIAGNOSES_FOR_SHARE) return null

        val requiredCooldown = when {
            prefs.hasSharedApp -> SHARE_AFTER_SHARED_COOLDOWN_MS
            prefs.sharePromptDismissCount >= 2 -> SHARE_HIGH_DISMISS_COOLDOWN_MS
            else -> SHARE_DISMISS_COOLDOWN_MS
        }
        if (prefs.lastSharePromptAtMillis > 0L &&
            (now - prefs.lastSharePromptAtMillis) < requiredCooldown
        ) {
            return null
        }

        val baseScore = when (input.triggerContext) {
            PromptTriggerContext.REPAIR_RESOLVED -> 85
            PromptTriggerContext.REPORT_SAVED -> 76
            PromptTriggerContext.DIAGNOSIS_COMPLETED -> if (input.lastDiagnosisConfidence >= 75) 72 else return null
            PromptTriggerContext.HANDS_FREE_SUCCESS -> 68
            PromptTriggerContext.APP_START -> return null
        }

        return PromptDecision.ShowAutoPopup(
            type = AutoPromptType.SHARE_APP,
            title = "Share PocketMechanic with a Friend",
            message = "Know someone troubleshooting a noisy belt, error code, or leaky sink? Send them PocketMechanic.",
            primaryActionLabel = "Share App",
            secondaryActionLabel = "Later",
            optOutActionLabel = "Not Interested",
            reason = "High satisfaction share moment (ops=${prefs.successfulDiagnosesCount}, ctx=${input.triggerContext})",
            score = baseScore
        )
    }
}
