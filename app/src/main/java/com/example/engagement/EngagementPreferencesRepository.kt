package com.example.engagement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.engagementDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pocket_mechanic_engagement"
)

class EngagementPreferencesRepository(private val context: Context) {

    private object Keys {
        val FIRST_INSTALL_AT = longPreferencesKey("first_install_at_millis")
        val APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")
        val LAST_LAUNCH_AT = longPreferencesKey("last_launch_at_millis")
        val DISTINCT_ACTIVE_DAYS = intPreferencesKey("distinct_active_days")
        val LAST_ACTIVE_DAY_EPOCH = longPreferencesKey("last_active_day_epoch")
        val SUCCESSFUL_DIAGNOSES = intPreferencesKey("successful_diagnoses_count")
        val RESOLVED_REPAIRS = intPreferencesKey("resolved_repairs_count")
        val SAVED_REPORTS = intPreferencesKey("saved_reports_count")
        val FOLLOW_UP_COUNT = intPreferencesKey("follow_up_count")

        val LAST_AUTO_PROMPT_AT = longPreferencesKey("last_auto_prompt_at_millis")
        val LAST_AUTO_PROMPT_TYPE = stringPreferencesKey("last_auto_prompt_type")

        val LAST_UPDATE_PROMPT_AT = longPreferencesKey("last_update_prompt_at_millis")
        val DISMISSED_UPDATE_VERSION = intPreferencesKey("dismissed_update_version_code")

        val LAST_RATE_PROMPT_AT = longPreferencesKey("last_rate_prompt_at_millis")
        val RATE_DISMISS_COUNT = intPreferencesKey("rate_prompt_dismiss_count")
        val HAS_RATED_APP = booleanPreferencesKey("has_rated_app")

        val LAST_SHARE_PROMPT_AT = longPreferencesKey("last_share_prompt_at_millis")
        val SHARE_DISMISS_COUNT = intPreferencesKey("share_prompt_dismiss_count")
        val HAS_SHARED_APP = booleanPreferencesKey("has_shared_app")

        val LAST_DONATION_BANNER_DISMISSED_AT = longPreferencesKey("last_donation_banner_dismissed_at")
        val LAST_DONATION_AUTO_PROMPT_AT = longPreferencesKey("last_donation_auto_prompt_at")
        val LAST_DONATION_MILESTONE = intPreferencesKey("last_donation_milestone_prompted")

        val IS_ACTIVE_SUPPORTER = booleanPreferencesKey("is_active_supporter")
        val SUPPORTER_TIER_ID = stringPreferencesKey("supporter_tier_id")
    }

    val stateFlow: Flow<EngagementPreferencesState> = context.engagementDataStore.data.map { prefs ->
        EngagementPreferencesState(
            firstInstallAtMillis = prefs[Keys.FIRST_INSTALL_AT] ?: 0L,
            appLaunchCount = prefs[Keys.APP_LAUNCH_COUNT] ?: 0,
            lastLaunchAtMillis = prefs[Keys.LAST_LAUNCH_AT] ?: 0L,
            distinctActiveDays = prefs[Keys.DISTINCT_ACTIVE_DAYS] ?: 0,
            lastActiveDayEpoch = prefs[Keys.LAST_ACTIVE_DAY_EPOCH] ?: 0L,
            successfulDiagnosesCount = prefs[Keys.SUCCESSFUL_DIAGNOSES] ?: 0,
            resolvedRepairsCount = prefs[Keys.RESOLVED_REPAIRS] ?: 0,
            savedReportsCount = prefs[Keys.SAVED_REPORTS] ?: 0,
            followUpCount = prefs[Keys.FOLLOW_UP_COUNT] ?: 0,
            lastAutoPromptAtMillis = prefs[Keys.LAST_AUTO_PROMPT_AT] ?: 0L,
            lastAutoPromptType = prefs[Keys.LAST_AUTO_PROMPT_TYPE],
            lastUpdatePromptAtMillis = prefs[Keys.LAST_UPDATE_PROMPT_AT] ?: 0L,
            dismissedUpdateVersionCode = prefs[Keys.DISMISSED_UPDATE_VERSION] ?: 0,
            lastRatePromptAtMillis = prefs[Keys.LAST_RATE_PROMPT_AT] ?: 0L,
            ratePromptDismissCount = prefs[Keys.RATE_DISMISS_COUNT] ?: 0,
            hasRatedApp = prefs[Keys.HAS_RATED_APP] ?: false,
            lastSharePromptAtMillis = prefs[Keys.LAST_SHARE_PROMPT_AT] ?: 0L,
            sharePromptDismissCount = prefs[Keys.SHARE_DISMISS_COUNT] ?: 0,
            hasSharedApp = prefs[Keys.HAS_SHARED_APP] ?: false,
            lastDonationBannerDismissedAtMillis = prefs[Keys.LAST_DONATION_BANNER_DISMISSED_AT] ?: 0L,
            lastDonationAutoPromptAtMillis = prefs[Keys.LAST_DONATION_AUTO_PROMPT_AT] ?: 0L,
            lastDonationMilestonePrompted = prefs[Keys.LAST_DONATION_MILESTONE] ?: 0,
            isActiveSupporter = prefs[Keys.IS_ACTIVE_SUPPORTER] ?: false,
            supporterTierId = prefs[Keys.SUPPORTER_TIER_ID]
        )
    }

    suspend fun currentState(): EngagementPreferencesState = stateFlow.first()

    suspend fun recordAppLaunch(nowMillis: Long = System.currentTimeMillis()): EngagementPreferencesState {
        var updated = EngagementPreferencesState()
        context.engagementDataStore.edit { prefs ->
            if ((prefs[Keys.FIRST_INSTALL_AT] ?: 0L) <= 0L) {
                prefs[Keys.FIRST_INSTALL_AT] = nowMillis
            }
            prefs[Keys.APP_LAUNCH_COUNT] = (prefs[Keys.APP_LAUNCH_COUNT] ?: 0) + 1
            prefs[Keys.LAST_LAUNCH_AT] = nowMillis

            val dayEpoch = nowMillis / EngagementScheduler.DAY_MS
            val prevDayEpoch = prefs[Keys.LAST_ACTIVE_DAY_EPOCH] ?: 0L
            if (dayEpoch > prevDayEpoch) {
                prefs[Keys.LAST_ACTIVE_DAY_EPOCH] = dayEpoch
                prefs[Keys.DISTINCT_ACTIVE_DAYS] = (prefs[Keys.DISTINCT_ACTIVE_DAYS] ?: 0) + 1
            }
        }
        updated = currentState()
        return updated
    }

    suspend fun recordSuccessfulDiagnosis(): EngagementPreferencesState {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.SUCCESSFUL_DIAGNOSES] = (prefs[Keys.SUCCESSFUL_DIAGNOSES] ?: 0) + 1
        }
        return currentState()
    }

    suspend fun recordRepairResolved(): EngagementPreferencesState {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.RESOLVED_REPAIRS] = (prefs[Keys.RESOLVED_REPAIRS] ?: 0) + 1
        }
        return currentState()
    }

    suspend fun recordSavedReport(): EngagementPreferencesState {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.SAVED_REPORTS] = (prefs[Keys.SAVED_REPORTS] ?: 0) + 1
        }
        return currentState()
    }

    suspend fun recordFollowUpCompleted(): EngagementPreferencesState {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.FOLLOW_UP_COUNT] = (prefs[Keys.FOLLOW_UP_COUNT] ?: 0) + 1
        }
        return currentState()
    }

    suspend fun recordAutoPromptShown(
        type: AutoPromptType,
        milestoneValue: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.LAST_AUTO_PROMPT_AT] = nowMillis
            prefs[Keys.LAST_AUTO_PROMPT_TYPE] = type.storageKey
            when (type) {
                AutoPromptType.UPDATE_APP -> prefs[Keys.LAST_UPDATE_PROMPT_AT] = nowMillis
                AutoPromptType.RATE_APP -> prefs[Keys.LAST_RATE_PROMPT_AT] = nowMillis
                AutoPromptType.SHARE_APP -> prefs[Keys.LAST_SHARE_PROMPT_AT] = nowMillis
                AutoPromptType.DONATE_SUPPORT -> {
                    prefs[Keys.LAST_DONATION_AUTO_PROMPT_AT] = nowMillis
                    if (milestoneValue > (prefs[Keys.LAST_DONATION_MILESTONE] ?: 0)) {
                        prefs[Keys.LAST_DONATION_MILESTONE] = milestoneValue
                    }
                }
            }
        }
    }

    suspend fun recordUpdateDismissed(
        versionCode: Int,
        skipVersion: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_PROMPT_AT] = nowMillis
            if (skipVersion && versionCode > 0) {
                prefs[Keys.DISMISSED_UPDATE_VERSION] = versionCode
            }
        }
    }

    suspend fun recordRateOutcome(
        ratedOrOptOut: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.LAST_RATE_PROMPT_AT] = nowMillis
            if (ratedOrOptOut) {
                prefs[Keys.HAS_RATED_APP] = true
            } else {
                prefs[Keys.RATE_DISMISS_COUNT] = (prefs[Keys.RATE_DISMISS_COUNT] ?: 0) + 1
            }
        }
    }

    suspend fun recordShareOutcome(
        shared: Boolean,
        optOut: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.LAST_SHARE_PROMPT_AT] = nowMillis
            if (shared) {
                prefs[Keys.HAS_SHARED_APP] = true
            } else {
                val inc = if (optOut) 3 else 1
                prefs[Keys.SHARE_DISMISS_COUNT] = (prefs[Keys.SHARE_DISMISS_COUNT] ?: 0) + inc
            }
        }
    }

    suspend fun recordDonationBannerDismissed(nowMillis: Long = System.currentTimeMillis()) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.LAST_DONATION_BANNER_DISMISSED_AT] = nowMillis
        }
    }

    suspend fun setActiveSupporter(isSupporter: Boolean, tierId: String?) {
        context.engagementDataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE_SUPPORTER] = isSupporter
            if (!tierId.isNullOrBlank()) {
                prefs[Keys.SUPPORTER_TIER_ID] = tierId
            }
        }
    }
}
