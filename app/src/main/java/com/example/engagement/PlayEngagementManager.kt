package com.example.engagement

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.donation.DonationConfig
import com.example.util.ExternalOpenOutcome
import com.example.util.openExternalUrl
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface PlayReviewOutcome {
    data object CompletedInApp : PlayReviewOutcome
    data object OpenedStoreFallback : PlayReviewOutcome
    data class Unavailable(val message: String) : PlayReviewOutcome
}

sealed interface PlayUpdateOutcome {
    data object FlowLaunched : PlayUpdateOutcome
    data object UpToDate : PlayUpdateOutcome
    data object OpenedStoreFallback : PlayUpdateOutcome
    data class Unavailable(val message: String) : PlayUpdateOutcome
}

class PlayEngagementManager(private val appContext: Context) {

    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(appContext) }
    private val reviewManager by lazy { ReviewManagerFactory.create(appContext) }

    fun currentVersionCode(): Int {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(1)
    }

    suspend fun checkForUpdate(): UpdateAvailabilitySnapshot {
        val currentCode = currentVersionCode()
        return try {
            val info = appUpdateManager.requestAppUpdateInfo()
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
                info.installStatus() == InstallStatus.DOWNLOADED
            UpdateAvailabilitySnapshot(
                isUpdateAvailable = available,
                availableVersionCode = info.availableVersionCode(),
                currentVersionCode = currentCode,
                updatePriority = info.updatePriority(),
                stalenessDays = info.clientVersionStalenessDays() ?: 0
            )
        } catch (e: Exception) {
            Log.d(TAG, "Play In-App Update check unavailable on this build: ${e.message}")
            UpdateAvailabilitySnapshot(
                isUpdateAvailable = false,
                availableVersionCode = currentCode,
                currentVersionCode = currentCode
            )
        }
    }

    suspend fun startAppUpdateOrFallback(activity: Activity?): PlayUpdateOutcome {
        val activeActivity = activity?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: return PlayUpdateOutcome.Unavailable("An active screen session is required to launch updates.")

        return try {
            val info = appUpdateManager.requestAppUpdateInfo()
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
                return PlayUpdateOutcome.FlowLaunched
            }
            val availability = info.updateAvailability()
            if (availability == UpdateAvailability.UPDATE_AVAILABLE ||
                availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                val updateType = when {
                    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                    info.updatePriority() >= 4 && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                        AppUpdateType.IMMEDIATE
                    else -> AppUpdateType.FLEXIBLE
                }
                if (info.isUpdateTypeAllowed(updateType)) {
                    appUpdateManager.startUpdateFlow(
                        info,
                        activeActivity,
                        AppUpdateOptions.defaultOptions(updateType)
                    )
                    return PlayUpdateOutcome.FlowLaunched
                }
            }
            PlayUpdateOutcome.UpToDate
        } catch (e: Exception) {
            Log.w(TAG, "Play In-App Update flow unavailable, opening Play Store listing fallback", e)
            when (
                activeActivity.openExternalUrl(
                    DonationConfig.buildPlayStoreUrl(activeActivity.packageName),
                    "Play Store URL"
                )
            ) {
                ExternalOpenOutcome.Opened -> PlayUpdateOutcome.OpenedStoreFallback
                ExternalOpenOutcome.CopiedFallback -> PlayUpdateOutcome.Unavailable(
                    "Update check unavailable in offline/debug build. Play Store link copied."
                )
                is ExternalOpenOutcome.Failed -> PlayUpdateOutcome.Unavailable(
                    "PocketMechanic is up to date on this build."
                )
            }
        }
    }

    suspend fun launchInAppReview(activity: Activity?): PlayReviewOutcome {
        val activeActivity = activity?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: return PlayReviewOutcome.Unavailable("An active screen session is required to open Google Play Review.")

        val reviewInfo = suspendCancellableCoroutine { cont ->
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                if (!cont.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    cont.resume(task.result)
                } else {
                    Log.w(TAG, "requestReviewFlow failed: ${task.exception?.message}")
                    cont.resume(null)
                }
            }
        }

        if (reviewInfo != null) {
            val completed = suspendCancellableCoroutine { cont ->
                reviewManager.launchReviewFlow(activeActivity, reviewInfo)
                    .addOnCompleteListener { task ->
                        if (!cont.isActive) return@addOnCompleteListener
                        cont.resume(task.isSuccessful)
                    }
            }
            if (completed) {
                return PlayReviewOutcome.CompletedInApp
            }
        }

        return when (
            activeActivity.openExternalUrl(
                DonationConfig.buildPlayStoreUrl(activeActivity.packageName),
                "Play Store Rating URL"
            )
        ) {
            ExternalOpenOutcome.Opened -> PlayReviewOutcome.OpenedStoreFallback
            ExternalOpenOutcome.CopiedFallback -> PlayReviewOutcome.Unavailable(
                "Play Store app unavailable here — store rating link copied to clipboard."
            )
            is ExternalOpenOutcome.Failed -> PlayReviewOutcome.Unavailable(
                "Google Play Store is unavailable on this device."
            )
        }
    }

    private companion object {
        const val TAG = "PlayEngagementMgr"
    }
}
