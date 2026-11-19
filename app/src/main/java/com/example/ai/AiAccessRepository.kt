package com.example.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiAccessDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pocket_mechanic_ai_access"
)

enum class AiProviderMode(val label: String, val shortLabel: String) {
    AUTO_SMART("Smart Auto", "Auto"),
    FREE_SHARED("Free Shared Tier", "Free Tier"),
    BYOK_GEMINI("Bring Your Own Key", "BYOK"),
    OFFLINE_ONLY("Offline Reference", "Offline")
}

enum class AiEngineSource(val badgeLabel: String) {
    FREE_SHARED_FLASH("Free AI • Flash"),
    FREE_SHARED_LITE_FALLBACK("Free AI • Flash-Lite"),
    BYOK_PERSONAL_KEY("Personal BYOK Key"),
    OFFLINE_REFERENCE("Offline Reference (No AI)")
}

data class AiAccessState(
    val preferredMode: AiProviderMode = AiProviderMode.AUTO_SMART,
    val byokApiKey: String = "",
    val byokModelName: String = DEFAULT_BYOK_MODEL,
    val dailyFreeUsedCount: Int = 0,
    val dailyFreeEpochDay: Long = 0L
) {
    val hasByokKey: Boolean get() = byokApiKey.trim().length >= 20

    companion object {
        const val DEFAULT_BYOK_MODEL = "gemini-3.8-flash"
        const val LITE_BYOK_MODEL = "gemini-3.5-flash-lite"
        const val DAILY_FREE_LIMIT = 5
        const val DAILY_SUPPORTER_LIMIT = 25
        const val DAY_MS = 86_400_000L
        const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"
    }
}

sealed interface AiExecutionPlan {
    data class UseByok(val apiKey: String, val modelName: String) : AiExecutionPlan
    data class UseFreeShared(val remainingBeforeCall: Int, val dailyLimit: Int) : AiExecutionPlan
    data class QuotaReachedNeedChoice(val usedToday: Int, val dailyLimit: Int) : AiExecutionPlan
    data object UseOfflineReference : AiExecutionPlan
}

class AiAccessRepository(private val context: Context) {

    private object Keys {
        val PREFERRED_MODE = stringPreferencesKey("preferred_ai_mode")
        val BYOK_API_KEY = stringPreferencesKey("byok_gemini_api_key")
        val BYOK_MODEL_NAME = stringPreferencesKey("byok_model_name")
        val DAILY_FREE_USED = intPreferencesKey("daily_free_used_count")
        val DAILY_FREE_EPOCH_DAY = longPreferencesKey("daily_free_epoch_day")
    }

    val stateFlow: Flow<AiAccessState> = context.aiAccessDataStore.data.map { prefs ->
        val mode = prefs[Keys.PREFERRED_MODE]
            ?.let { raw -> AiProviderMode.entries.firstOrNull { it.name == raw } }
            ?: AiProviderMode.AUTO_SMART
        val model = prefs[Keys.BYOK_MODEL_NAME]
            ?.takeIf { it.isNotBlank() }
            ?: AiAccessState.DEFAULT_BYOK_MODEL

        AiAccessState(
            preferredMode = mode,
            byokApiKey = prefs[Keys.BYOK_API_KEY].orEmpty(),
            byokModelName = model,
            dailyFreeUsedCount = prefs[Keys.DAILY_FREE_USED] ?: 0,
            dailyFreeEpochDay = prefs[Keys.DAILY_FREE_EPOCH_DAY] ?: 0L
        )
    }

    suspend fun currentState(): AiAccessState = stateFlow.first()

    suspend fun setPreferredMode(mode: AiProviderMode) {
        context.aiAccessDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_MODE] = mode.name
        }
    }

    suspend fun saveByokConfig(
        apiKey: String,
        modelName: String = AiAccessState.DEFAULT_BYOK_MODEL,
        switchToByokMode: Boolean = true
    ) {
        val trimmedKey = apiKey.trim()
        val cleanModel = modelName.trim().ifBlank { AiAccessState.DEFAULT_BYOK_MODEL }
        context.aiAccessDataStore.edit { prefs ->
            prefs[Keys.BYOK_API_KEY] = trimmedKey
            prefs[Keys.BYOK_MODEL_NAME] = cleanModel
            if (switchToByokMode && trimmedKey.length >= 20) {
                prefs[Keys.PREFERRED_MODE] = AiProviderMode.BYOK_GEMINI.name
            }
        }
    }

    suspend fun clearByokKey() {
        context.aiAccessDataStore.edit { prefs ->
            prefs.remove(Keys.BYOK_API_KEY)
            if (prefs[Keys.PREFERRED_MODE] == AiProviderMode.BYOK_GEMINI.name) {
                prefs[Keys.PREFERRED_MODE] = AiProviderMode.AUTO_SMART.name
            }
        }
    }

    suspend fun recordFreeSharedCall(nowMillis: Long = System.currentTimeMillis()) {
        val today = currentEpochDay(nowMillis)
        context.aiAccessDataStore.edit { prefs ->
            val storedDay = prefs[Keys.DAILY_FREE_EPOCH_DAY] ?: 0L
            val currentCount = if (storedDay == today) (prefs[Keys.DAILY_FREE_USED] ?: 0) else 0
            prefs[Keys.DAILY_FREE_EPOCH_DAY] = today
            prefs[Keys.DAILY_FREE_USED] = currentCount + 1
        }
    }

    companion object {
        fun currentEpochDay(nowMillis: Long = System.currentTimeMillis()): Long =
            nowMillis / AiAccessState.DAY_MS

        fun usedToday(state: AiAccessState, nowMillis: Long = System.currentTimeMillis()): Int {
            return if (state.dailyFreeEpochDay == currentEpochDay(nowMillis)) {
                state.dailyFreeUsedCount.coerceAtLeast(0)
            } else {
                0
            }
        }

        fun dailyLimitFor(isActiveSupporter: Boolean): Int =
            if (isActiveSupporter) AiAccessState.DAILY_SUPPORTER_LIMIT else AiAccessState.DAILY_FREE_LIMIT

        fun remainingDailyQuota(
            state: AiAccessState,
            isActiveSupporter: Boolean,
            nowMillis: Long = System.currentTimeMillis()
        ): Int {
            val limit = dailyLimitFor(isActiveSupporter)
            return (limit - usedToday(state, nowMillis)).coerceAtLeast(0)
        }

        fun resolveExecutionPlan(
            state: AiAccessState,
            isActiveSupporter: Boolean,
            nowMillis: Long = System.currentTimeMillis()
        ): AiExecutionPlan {
            val trimmedKey = state.byokApiKey.trim()
            val hasKey = trimmedKey.length >= 20
            val limit = dailyLimitFor(isActiveSupporter)
            val used = usedToday(state, nowMillis)
            val remaining = (limit - used).coerceAtLeast(0)

            return when (state.preferredMode) {
                AiProviderMode.OFFLINE_ONLY -> AiExecutionPlan.UseOfflineReference
                AiProviderMode.BYOK_GEMINI -> {
                    if (hasKey) {
                        AiExecutionPlan.UseByok(trimmedKey, state.byokModelName)
                    } else if (remaining > 0) {
                        AiExecutionPlan.UseFreeShared(remaining, limit)
                    } else {
                        AiExecutionPlan.QuotaReachedNeedChoice(used, limit)
                    }
                }
                AiProviderMode.FREE_SHARED -> {
                    if (remaining > 0) {
                        AiExecutionPlan.UseFreeShared(remaining, limit)
                    } else {
                        AiExecutionPlan.QuotaReachedNeedChoice(used, limit)
                    }
                }
                AiProviderMode.AUTO_SMART -> {
                    // Prefer user's own free Gemini key whenever configured so developer pays $0
                    // and user enjoys unlimited quota; otherwise use fair daily free shared allowance.
                    if (hasKey) {
                        AiExecutionPlan.UseByok(trimmedKey, state.byokModelName)
                    } else if (remaining > 0) {
                        AiExecutionPlan.UseFreeShared(remaining, limit)
                    } else {
                        AiExecutionPlan.QuotaReachedNeedChoice(used, limit)
                    }
                }
            }
        }

        fun maskApiKey(rawKey: String): String {
            val clean = rawKey.trim()
            if (clean.length <= 10) return if (clean.isEmpty()) "" else "••••••••"
            return "${clean.take(5)}••••••${clean.takeLast(4)}"
        }
    }
}
