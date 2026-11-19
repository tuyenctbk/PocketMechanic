package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analytics.AppEventCategory
import com.example.analytics.AppEventNames
import com.example.analytics.AppEventTracker
import com.example.data.local.PocketMechanicDatabase
import com.example.donation.BillingManager
import com.example.donation.DonationConfig
import com.example.donation.DonationTier
import com.example.donation.PayPalPreset
import com.example.donation.QrCodeGenerator
import com.example.engagement.AutoPromptType
import com.example.engagement.EngagementPreferencesRepository
import com.example.engagement.EngagementPreferencesState
import com.example.engagement.EngagementScheduler
import com.example.engagement.PromptDecision
import com.example.engagement.PromptEvaluationInput
import com.example.engagement.PromptTriggerContext
import com.example.engagement.UpdateAvailabilitySnapshot
import com.example.model.DiagnosticSeverity
import com.example.ui.BillingViewModel
import com.example.util.findActiveActivity
import com.example.util.findActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngagementAndDonationTest {

    private val dayMs = EngagementScheduler.DAY_MS

    @Test
    fun `never shows rate share or donate prompt on app start`() {
        val now = 10L * dayMs
        val satisfiedPrefs = EngagementPreferencesState(
            firstInstallAtMillis = 1L * dayMs,
            appLaunchCount = 10,
            distinctActiveDays = 6,
            successfulDiagnosesCount = 12,
            resolvedRepairsCount = 4
        )

        val decision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = satisfiedPrefs,
                triggerContext = PromptTriggerContext.APP_START,
                nowMillis = now,
                sessionStartedAtMillis = now - 60_000L,
                sessionAutoPromptsShown = 0,
                updateInfo = UpdateAvailabilitySnapshot(isUpdateAvailable = false)
            )
        )

        assertTrue(decision is PromptDecision.None)
    }

    @Test
    fun `shows rate prompt only after satisfaction criteria and repair resolved or diagnosis success`() {
        val installTime = 1_000_000L
        val now = installTime + (3L * dayMs)
        val sessionStart = now - 20_000L

        // Casual / brand new user blocked
        val newUserDecision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = EngagementPreferencesState(
                    firstInstallAtMillis = now - (1L * EngagementScheduler.HOUR_MS),
                    appLaunchCount = 1,
                    successfulDiagnosesCount = 1
                ),
                triggerContext = PromptTriggerContext.DIAGNOSIS_COMPLETED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 0,
                lastDiagnosisSeverity = DiagnosticSeverity.MEDIUM,
                lastDiagnosisConfidence = 90
            )
        )
        assertTrue(newUserDecision is PromptDecision.None)

        // Satisfied returning user resolving a repair receives RATE_APP
        val satisfiedDecision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = EngagementPreferencesState(
                    firstInstallAtMillis = installTime,
                    appLaunchCount = 4,
                    distinctActiveDays = 3,
                    successfulDiagnosesCount = 4,
                    resolvedRepairsCount = 1
                ),
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 0
            )
        )
        assertTrue(satisfiedDecision is PromptDecision.ShowAutoPopup)
        assertEquals(AutoPromptType.RATE_APP, (satisfiedDecision as PromptDecision.ShowAutoPopup).type)
    }

    @Test
    fun `enforces 72h global cooldown, session cap, and critical hazard guard`() {
        val installTime = 1_000_000L
        val now = installTime + (10L * dayMs)
        val sessionStart = now - 30_000L

        val basePrefs = EngagementPreferencesState(
            firstInstallAtMillis = installTime,
            appLaunchCount = 8,
            distinctActiveDays = 5,
            successfulDiagnosesCount = 7,
            hasRatedApp = true,
            lastAutoPromptAtMillis = now - (24L * EngagementScheduler.HOUR_MS) // only 24h ago
        )

        // Blocked by 72h cross-prompt cooldown
        val blockedByGlobal = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = basePrefs,
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 0
            )
        )
        assertTrue(blockedByGlobal is PromptDecision.None)

        // Blocked by session cap even when global cooldown is clear
        val clearedCooldownPrefs = basePrefs.copy(lastAutoPromptAtMillis = now - (5L * dayMs))
        val blockedBySession = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = clearedCooldownPrefs,
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 1
            )
        )
        assertTrue(blockedBySession is PromptDecision.None)

        // Blocked during CRITICAL safety diagnosis
        val blockedByCritical = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = clearedCooldownPrefs,
                triggerContext = PromptTriggerContext.DIAGNOSIS_COMPLETED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 0,
                lastDiagnosisSeverity = DiagnosticSeverity.CRITICAL,
                lastDiagnosisConfidence = 95
            )
        )
        assertTrue(blockedByCritical is PromptDecision.None)

        // Milestone donation shows once cooldown clear & not critical
        val milestoneDecision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = clearedCooldownPrefs,
                triggerContext = PromptTriggerContext.DIAGNOSIS_COMPLETED,
                nowMillis = now,
                sessionStartedAtMillis = sessionStart,
                sessionAutoPromptsShown = 0,
                lastDiagnosisSeverity = DiagnosticSeverity.LOW,
                lastDiagnosisConfidence = 88
            )
        )
        assertTrue(milestoneDecision is PromptDecision.ShowAutoPopup)
        assertEquals(AutoPromptType.DONATE_SUPPORT, (milestoneDecision as PromptDecision.ShowAutoPopup).type)
    }

    @Test
    fun `active supporter disables inline support banner and donation milestones permanently`() {
        val now = 20L * dayMs
        val supporterPrefs = EngagementPreferencesState(
            firstInstallAtMillis = 1L * dayMs,
            appLaunchCount = 12,
            successfulDiagnosesCount = 10,
            isActiveSupporter = true,
            supporterTierId = "pocket_tip_tier_2",
            hasRatedApp = true
        )

        assertFalse(EngagementScheduler.shouldShowInlineSupportBanner(supporterPrefs, now))

        val decision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = supporterPrefs,
                triggerContext = PromptTriggerContext.DIAGNOSIS_COMPLETED,
                nowMillis = now,
                sessionStartedAtMillis = now - 20_000L,
                sessionAutoPromptsShown = 0,
                lastDiagnosisSeverity = DiagnosticSeverity.LOW,
                lastDiagnosisConfidence = 90
            )
        )
        // Only SHARE_APP remains eligible after rating + active supporter
        assertTrue(decision is PromptDecision.ShowAutoPopup)
        assertEquals(AutoPromptType.SHARE_APP, (decision as PromptDecision.ShowAutoPopup).type)
    }

    @Test
    fun `inline support banner respects 14 day dismissal cooldown`() {
        val now = 20L * dayMs
        val prefs = EngagementPreferencesState(
            firstInstallAtMillis = 1L * dayMs,
            appLaunchCount = 5,
            successfulDiagnosesCount = 4,
            lastDonationBannerDismissedAtMillis = now - (5L * dayMs)
        )
        assertFalse(EngagementScheduler.shouldShowInlineSupportBanner(prefs, now))

        val expiredCooldownPrefs = prefs.copy(lastDonationBannerDismissedAtMillis = now - (15L * dayMs))
        assertTrue(EngagementScheduler.shouldShowInlineSupportBanner(expiredCooldownPrefs, now))
    }

    @Test
    fun `permanent share opt-out stops future share prompts after 3 dismissals`() {
        val now = 120L * dayMs
        val optedOutPrefs = EngagementPreferencesState(
            firstInstallAtMillis = 1L * dayMs,
            appLaunchCount = 20,
            distinctActiveDays = 10,
            successfulDiagnosesCount = 15,
            resolvedRepairsCount = 6,
            hasRatedApp = true,
            isActiveSupporter = true,
            sharePromptDismissCount = 3,
            lastSharePromptAtMillis = now - (90L * dayMs)
        )

        val decision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = optedOutPrefs,
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED,
                nowMillis = now,
                sessionStartedAtMillis = now - 30_000L,
                sessionAutoPromptsShown = 0
            )
        )
        assertTrue(decision is PromptDecision.None)
    }

    @Test
    fun `suppresses rate share and donate popups after a recent failure in the same session`() {
        val now = 20L * dayMs
        val satisfiedPrefs = EngagementPreferencesState(
            firstInstallAtMillis = 1L * dayMs,
            appLaunchCount = 10,
            distinctActiveDays = 5,
            successfulDiagnosesCount = 8,
            resolvedRepairsCount = 2
        )

        val suppressedDecision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = satisfiedPrefs,
                triggerContext = PromptTriggerContext.REPAIR_RESOLVED,
                nowMillis = now,
                sessionStartedAtMillis = now - 30_000L,
                sessionAutoPromptsShown = 0,
                hasRecentFailureInSession = true
            )
        )
        assertTrue(suppressedDecision is PromptDecision.None)

        // Urgent Play Store update is still allowed even if an error occurred earlier
        val urgentUpdateDecision = EngagementScheduler.evaluate(
            PromptEvaluationInput(
                prefs = satisfiedPrefs,
                triggerContext = PromptTriggerContext.APP_START,
                nowMillis = now,
                sessionStartedAtMillis = now - 30_000L,
                sessionAutoPromptsShown = 0,
                hasRecentFailureInSession = true,
                updateInfo = UpdateAvailabilitySnapshot(
                    isUpdateAvailable = true,
                    availableVersionCode = 99,
                    updatePriority = 5
                )
            )
        )
        assertTrue(urgentUpdateDecision is PromptDecision.ShowAutoPopup)
        assertEquals(AutoPromptType.UPDATE_APP, (urgentUpdateDecision as PromptDecision.ShowAutoPopup).type)
    }

    @Test
    fun `unwraps nested ContextWrappers to find live Activity for Play Billing`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val wrappedOnce = ContextThemeWrapper(activity, R.style.Theme_MyApplication)
        val wrappedTwice = ContextWrapper(wrappedOnce)

        assertSame(activity, wrappedTwice.findActivity())
        assertSame(activity, wrappedTwice.findActiveActivity())

        val appContext = ApplicationProvider.getApplicationContext<Application>()
        assertNull(appContext.findActivity())
        assertNull(appContext.findActiveActivity())
    }

    @Test
    fun `generates PayPal Kofi links and non-empty QR bitmap`() {
        assertEquals("https://paypal.me/pocketmechanic/3USD", DonationConfig.buildPayPalUrl(PayPalPreset.THREE.amountUsd))
        assertEquals("https://paypal.me/pocketmechanic/5USD", DonationConfig.buildPayPalUrl(PayPalPreset.FIVE.amountUsd))
        assertEquals("https://paypal.me/pocketmechanic/10USD", DonationConfig.buildPayPalUrl(PayPalPreset.TEN.amountUsd))
        assertEquals("https://paypal.me/pocketmechanic", DonationConfig.buildPayPalUrl(PayPalPreset.ANY.amountUsd))
        assertEquals("https://ko-fi.com/pocketmechanic", DonationConfig.buildKofiUrl())

        val qr = QrCodeGenerator.generateBitmapSync(DonationConfig.buildPayPalUrl(5), sizePx = 256)
        assertNotNull(qr)
        assertEquals(256, qr!!.width)
        assertEquals(256, qr.height)
    }

    @Test
    fun `persists and prunes app events via Room AppEventDao`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PocketMechanicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.appEventDao()
            val tracker = AppEventTracker(dao)

            tracker.trackImmediate(
                userId = "user_42",
                eventName = AppEventNames.APP_LAUNCHED,
                category = AppEventCategory.LIFECYCLE,
                properties = mapOf("launchCount" to 1),
                timestampMillis = 1000L
            )
            tracker.trackImmediate(
                userId = "user_42",
                eventName = AppEventNames.DIAGNOSIS_COMPLETED,
                category = AppEventCategory.DIAGNOSTIC,
                properties = mapOf("mode" to "AUDIO", "confidence" to 92),
                timestampMillis = 2000L
            )

            val recent = dao.observeRecentEvents("user_42", 10).first()
            assertEquals(2, recent.size)
            assertEquals(AppEventNames.DIAGNOSIS_COMPLETED, recent.first().eventName)
            assertEquals(1, dao.countByName("user_42", AppEventNames.APP_LAUNCHED))

            dao.pruneExcessEvents("user_42", keepLatest = 1)
            val afterPrune = dao.observeRecentEvents("user_42", 10).first()
            assertEquals(1, afterPrune.size)
            assertEquals(AppEventNames.DIAGNOSIS_COMPLETED, afterPrune.first().eventName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `billingViewModel rejects missing or finishing Activity session and observes lifecycle safely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PocketMechanicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefsRepo = EngagementPreferencesRepository(context)
            val tracker = AppEventTracker(db.appEventDao())
            val billingManager = BillingManager(context)
            val vm = BillingViewModel(
                billingManager = billingManager,
                preferencesRepo = prefsRepo,
                eventTracker = tracker,
                userId = "billing_test_user"
            )

            // Attach as DefaultLifecycleObserver and transition through ON_RESUME
            val owner = object : LifecycleOwner {
                private val reg = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = reg
                fun moveToResumed() {
                    reg.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    reg.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    reg.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
            }
            owner.lifecycle.addObserver(vm)
            owner.moveToResumed()

            // Null activity must be caught cleanly with helpful message and isBusy = false
            vm.launchPurchase(null, DonationTier.TIER_1)
            val nullState = vm.billingState.value
            assertFalse(nullState.isBusy)
            assertTrue(nullState.statusMessage?.contains("active screen session", ignoreCase = true) == true)

            // Finishing activity must also be rejected before BillingFlow is invoked
            vm.clearStatusMessage()
            val controller = Robolectric.buildActivity(Activity::class.java).create()
            val finishingActivity = controller.get()
            finishingActivity.finish()
            vm.launchPurchase(finishingActivity, DonationTier.TIER_2)
            val finishingState = vm.billingState.value
            assertFalse(finishingState.isBusy)
            assertTrue(finishingState.statusMessage?.contains("active screen session", ignoreCase = true) == true)

            owner.lifecycle.removeObserver(vm)
        } finally {
            db.close()
        }
    }

    @Test
    fun `billingManager isReady is false and release is safe before any connection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val billingManager = BillingManager(context)
        assertFalse(billingManager.isReady())
        billingManager.release()
        assertFalse(billingManager.isReady())
    }

    @Test
    fun `triggerManualShare handles null activity failure without marking app shared or claiming clipboard`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PocketMechanicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefsRepo = EngagementPreferencesRepository(context)
            val tracker = AppEventTracker(db.appEventDao())
            val playManager = com.example.engagement.PlayEngagementManager(context)
            val vm = com.example.ui.EngagementViewModel(
                preferencesRepo = prefsRepo,
                eventTracker = tracker,
                playEngagementManager = playManager,
                userId = "share_test_user"
            )

            // Null activity must report failure (not clipboard fallback) and must not set hasSharedApp
            vm.triggerManualShare(activity = null, customText = null)
            assertEquals("Unable to open share sheet on this screen.", vm.engagementNotice.value)
            assertFalse(prefsRepo.currentState().hasSharedApp)

            // Sharing a custom diagnostic report on an Activity must not mark the app itself as shared
            val activity = Robolectric.buildActivity(Activity::class.java).create().get()
            vm.triggerManualShare(activity = activity, customText = "Diagnostic report summary")
            assertFalse(prefsRepo.currentState().hasSharedApp)
        } finally {
            db.close()
        }
    }
}
