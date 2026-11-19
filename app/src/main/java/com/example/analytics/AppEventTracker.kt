package com.example.analytics

import android.util.Log
import com.example.data.local.AppEventDao
import com.example.data.local.AppEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AppEventCategory {
    LIFECYCLE,
    DIAGNOSTIC,
    UTILITY,
    ENGAGEMENT,
    MONETIZATION
}

object AppEventNames {
    const val APP_LAUNCHED = "app_launched"
    const val DIAGNOSIS_STARTED = "diagnosis_started"
    const val DIAGNOSIS_COMPLETED = "diagnosis_completed"
    const val DIAGNOSIS_FAILED = "diagnosis_failed"
    const val DIAGNOSIS_SAVED = "diagnosis_saved"
    const val REPAIR_RESOLVED = "repair_resolved"
    const val FOLLOW_UP_ASKED = "follow_up_asked"
    const val HANDS_FREE_TURN = "hands_free_turn"
    const val FLASHLIGHT_TOGGLED = "flashlight_toggled"
    const val EQUIPMENT_SAVED = "equipment_saved"

    const val PROMPT_SUPPRESSED = "prompt_suppressed"
    const val PROMPT_SHOWN = "prompt_shown"
    const val PROMPT_ACCEPTED = "prompt_accepted"
    const val PROMPT_DISMISSED = "prompt_dismissed"
    const val PROMPT_OPTED_OUT = "prompt_opted_out"

    const val RATE_FLOW_LAUNCHED = "rate_flow_launched"
    const val UPDATE_CHECKED = "update_checked"
    const val UPDATE_FLOW_LAUNCHED = "update_flow_launched"
    const val APP_SHARED = "app_shared"

    const val DONATION_SHEET_OPENED = "donation_sheet_opened"
    const val DONATION_CHANNEL_SELECTED = "donation_channel_selected"
    const val DONATION_PURCHASE_INITIATED = "donation_purchase_initiated"
    const val DONATION_COMPLETED = "donation_completed"
    const val DONATION_FAILED = "donation_failed"
}

class AppEventTracker(
    private val dao: AppEventDao,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun observeRecentEvents(userId: String, limit: Int = 40): Flow<List<AppEventEntity>> =
        dao.observeRecentEvents(userId, limit)

    fun observeTotalEventCount(userId: String): Flow<Int> =
        dao.observeTotalCount(userId)

    fun track(
        userId: String,
        eventName: String,
        category: AppEventCategory,
        properties: Map<String, Any?> = emptyMap(),
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val safeUserId = userId.ifBlank { "anonymous" }
        val metadataJson = encodeProperties(properties)
        Log.d(TAG, "event=$eventName category=${category.name} props=$metadataJson")

        scope.launch {
            runCatching {
                dao.insert(
                    AppEventEntity(
                        userId = safeUserId,
                        eventName = eventName,
                        category = category.name,
                        metadataJson = metadataJson,
                        createdAtMillis = timestampMillis
                    )
                )
                if (eventName == AppEventNames.APP_LAUNCHED) {
                    dao.pruneExcessEvents(safeUserId, keepLatest = 300)
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed to persist event $eventName", err)
            }
        }
    }

    suspend fun trackImmediate(
        userId: String,
        eventName: String,
        category: AppEventCategory,
        properties: Map<String, Any?> = emptyMap(),
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val safeUserId = userId.ifBlank { "anonymous" }
        dao.insert(
            AppEventEntity(
                userId = safeUserId,
                eventName = eventName,
                category = category.name,
                metadataJson = encodeProperties(properties),
                createdAtMillis = timestampMillis
            )
        )
    }

    companion object {
        private const val TAG = "AppEventTracker"

        fun encodeProperties(properties: Map<String, Any?>): String {
            if (properties.isEmpty()) return "{}"
            return JSONObject().apply {
                properties.forEach { (k, v) ->
                    put(k, v ?: JSONObject.NULL)
                }
            }.toString()
        }
    }
}
