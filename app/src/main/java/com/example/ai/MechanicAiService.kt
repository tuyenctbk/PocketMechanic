package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.GroundedSource
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarning
import com.example.model.SafetyWarningLevel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AiDiagnosisOutcome(
    val result: DiagnosticResult,
    val engineSource: AiEngineSource
)

data class ByokKeyVerificationResult(
    val isValid: Boolean,
    val message: String,
    val resolvedModel: String = AiAccessState.DEFAULT_BYOK_MODEL
)

class MechanicAiService {

    private val diagnosticSystemPrompt = """
        You are PocketMechanic, an expert ASE-certified master mechanic, licensed plumber, and appliance technician.
        Analyze user-provided audio recordings (engine noises, rhythmic knocks, belt squeals, motor hums, leaks),
        photos (error codes, blown fuses, pipe fittings, worn belts), and make/model descriptions.
        ALWAYS use Google Search grounding when a make, model, part number, or error code is provided to cross-reference
        official manufacturer repair manuals, service bulletins, torque/clearance specs, and safety warnings.

        Format your response using these EXACT section headers on their own lines so the diagnostic card parses cleanly:
        TITLE: <short 3-8 word diagnostic title>
        CULPRIT: <single concise sentence naming the most likely mechanical/electrical culprit>
        CONFIDENCE: <integer 0-100>
        SEVERITY: <one of LOW, MEDIUM, HIGH, CRITICAL>
        SUMMARY: <2-4 crisp sentences explaining what the sound/image/code indicates and why>
        SAFETY_WARNINGS:
        - [DANGER|WARNING|CAUTION] Short Hazard Title: actionable safety warning (power off, hot surfaces, jack stands, pressure relief, etc.)
        - [DANGER|WARNING|CAUTION] Short Hazard Title: actionable safety warning 2
        DIY_STEPS:
        1. <clear step 1>
        2. <clear step 2>
        3. <clear step 3>
        PARTS_TOOLS:
        - <specific tool or replacement part 1>
        - <specific tool or replacement part 2>
    """.trimIndent()

    private val handsFreeSystemPrompt = """
        You are PocketMechanic in Hands-Free Workshop Mode. The user has greasy hands and cannot look at the screen.
        Keep answers concise, spoken-friendly, and practical (3 to 5 short sentences max).
        State any critical safety warning first, then give the next immediate diagnostic or repair action.
        Do NOT use markdown tables, asterisks, or bullet symbols that sound awkward when read aloud by Text-to-Speech.
        Use Google Search grounding whenever a specific make, model, or error code is mentioned.
    """.trimIndent()

    private val followUpSystemPrompt = """
        You are PocketMechanic, an ASE-certified master mechanic, licensed plumber, and appliance technician.
        Answer the user's follow-up repair question directly, clearly, and practically in plain conversational prose.
        Lead with any critical safety precaution first if relevant, then give exact torque specs, tool sizes,
        part numbers, or step-by-step troubleshooting guidance.
        Do NOT repeat diagnostic card templates (no TITLE:/CULPRIT:/CONFIDENCE:/SEVERITY: headers).
        Use Google Search grounding whenever exact manufacturer specs, service bulletins, or part numbers are needed.
    """.trimIndent()

    private val diagnosticModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = PRIMARY_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(diagnosticSystemPrompt) }
        )
    }

    // Zero-billing high-volume fallback model if primary Flash hits rate limits
    private val liteDiagnosticModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = LITE_FALLBACK_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(diagnosticSystemPrompt) }
        )
    }

    private val followUpModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = PRIMARY_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(followUpSystemPrompt) }
        )
    }

    private val liteFollowUpModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = LITE_FALLBACK_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(followUpSystemPrompt) }
        )
    }

    private val handsFreeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = PRIMARY_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(handsFreeSystemPrompt) }
        )
    }

    private val liteHandsFreeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = LITE_FALLBACK_MODEL,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(handsFreeSystemPrompt) }
        )
    }

    private fun requireFirebaseConfigured() {
        val hasApp = runCatching { FirebaseApp.getInstance() }.isSuccess
        if (!hasApp) {
            throw IllegalStateException(
                "Cloud Free AI is unavailable in offline/local build. Add your free BYOK Gemini key in AI Engine settings or tap Offline Ref."
            )
        }
    }

    suspend fun diagnose(
        category: EquipmentCategory,
        equipmentName: String,
        symptomNotes: String,
        audioWavBytes: ByteArray? = null,
        imageBitmap: Bitmap? = null,
        byokApiKey: String? = null,
        byokModelName: String = PRIMARY_MODEL
    ): AiDiagnosisOutcome {
        val cleanKey = byokApiKey?.trim().orEmpty()
        if (cleanKey.length >= 20) {
            val result = diagnoseWithByok(
                apiKey = cleanKey,
                preferredModel = byokModelName,
                category = category,
                equipmentName = equipmentName,
                symptomNotes = symptomNotes,
                audioWavBytes = audioWavBytes,
                imageBitmap = imageBitmap
            )
            return AiDiagnosisOutcome(result = result, engineSource = AiEngineSource.BYOK_PERSONAL_KEY)
        }

        requireFirebaseConfigured()

        val promptContent = buildDiagnosticPrompt(
            category = category,
            equipmentName = equipmentName,
            symptomNotes = symptomNotes,
            audioWavBytes = audioWavBytes,
            imageBitmap = imageBitmap
        )

        return try {
            val response = withTimeout(60_000L) {
                diagnosticModel.generateContent(promptContent)
            }
            val rawText = response.text?.trim()
                ?: throw IllegalStateException("Diagnostic model returned an empty response.")
            AiDiagnosisOutcome(
                result = parseDiagnosticResponse(
                    rawText = rawText,
                    sources = extractSources(response),
                    searchEntryHtml = response.candidates.firstOrNull()?.groundingMetadata?.searchEntryPoint?.renderedContent
                ),
                engineSource = AiEngineSource.FREE_SHARED_FLASH
            )
        } catch (e: CancellationException) {
            throw e
        } catch (primaryErr: Exception) {
            if (!shouldFallbackToLite(primaryErr)) throw primaryErr
            Log.w(TAG, "Primary Flash unavailable/throttled; falling back to $LITE_FALLBACK_MODEL", primaryErr)
            val fallbackResponse = withTimeout(60_000L) {
                liteDiagnosticModel.generateContent(promptContent)
            }
            val rawText = fallbackResponse.text?.trim()
                ?: throw IllegalStateException("Fallback diagnostic model returned an empty response.")
            AiDiagnosisOutcome(
                result = parseDiagnosticResponse(
                    rawText = rawText,
                    sources = extractSources(fallbackResponse),
                    searchEntryHtml = fallbackResponse.candidates.firstOrNull()?.groundingMetadata?.searchEntryPoint?.renderedContent
                ),
                engineSource = AiEngineSource.FREE_SHARED_LITE_FALLBACK
            )
        }
    }

    suspend fun askFollowUp(
        reportContext: DiagnosticResult,
        equipmentName: String,
        question: String,
        byokApiKey: String? = null,
        byokModelName: String = PRIMARY_MODEL
    ): Pair<String, List<GroundedSource>> {
        val promptText = """
            Context from current PocketMechanic diagnosis:
            Equipment: ${equipmentName.ifBlank { "Unspecified" }}
            Title: ${reportContext.title}
            Primary Culprit: ${reportContext.primaryCulprit}
            Summary: ${reportContext.summary}

            User follow-up question: $question
            Answer clearly and practically. Use Google Search if exact torque specs, part numbers, or manual steps are needed.
        """.trimIndent()

        val cleanKey = byokApiKey?.trim().orEmpty()
        if (cleanKey.length >= 20) {
            val rest = callByokRestWithFallback(
                apiKey = cleanKey,
                preferredModel = byokModelName,
                systemInstruction = followUpSystemPrompt,
                userText = promptText,
                audioWavBytes = null,
                imageBitmap = null
            )
            return rest.text to rest.sources
        }

        requireFirebaseConfigured()

        val prompt = content { text(promptText) }
        val response = try {
            withTimeout(60_000L) { followUpModel.generateContent(prompt) }
        } catch (e: CancellationException) {
            throw e
        } catch (err: Exception) {
            if (!shouldFallbackToLite(err)) throw err
            withTimeout(60_000L) { liteFollowUpModel.generateContent(prompt) }
        }

        val reply = response.text?.trim()
            ?: throw IllegalStateException("No follow-up response returned.")
        return reply to extractSources(response)
    }

    suspend fun handsFreeTurn(
        equipmentContext: String,
        historyLines: List<String>,
        spokenText: String?,
        audioWavBytes: ByteArray? = null,
        byokApiKey: String? = null,
        byokModelName: String = PRIMARY_MODEL
    ): Pair<String, List<GroundedSource>> {
        val historyBlock = if (historyLines.isEmpty()) {
            "No prior turns."
        } else {
            historyLines.takeLast(6).joinToString("\n")
        }
        val userAsk = spokenText?.takeIf { it.isNotBlank() }
            ?: "Listen to the user's voice request or mechanical sound in the attached audio and reply concisely."

        val promptText = """
            Equipment being serviced: ${equipmentContext.ifBlank { "General vehicle or home equipment" }}
            Recent conversation:
            $historyBlock

            Current user input: $userAsk
        """.trimIndent()

        val cleanKey = byokApiKey?.trim().orEmpty()
        if (cleanKey.length >= 20) {
            val rest = callByokRestWithFallback(
                apiKey = cleanKey,
                preferredModel = byokModelName,
                systemInstruction = handsFreeSystemPrompt,
                userText = promptText,
                audioWavBytes = audioWavBytes,
                imageBitmap = null
            )
            return rest.text to rest.sources
        }

        requireFirebaseConfigured()

        val prompt = content {
            if (audioWavBytes != null && audioWavBytes.isNotEmpty()) {
                inlineData(audioWavBytes, "audio/wav")
            }
            text(promptText)
        }

        val response = try {
            withTimeout(60_000L) { handsFreeModel.generateContent(prompt) }
        } catch (e: CancellationException) {
            throw e
        } catch (err: Exception) {
            if (!shouldFallbackToLite(err)) throw err
            withTimeout(60_000L) { liteHandsFreeModel.generateContent(prompt) }
        }

        val reply = response.text?.trim()
            ?: throw IllegalStateException("Hands-free assistant returned an empty reply.")
        return reply to extractSources(response)
    }

    suspend fun verifyByokKey(
        apiKey: String,
        preferredModel: String = PRIMARY_MODEL
    ): ByokKeyVerificationResult {
        val trimmed = apiKey.trim()
        if (trimmed.length < 20) {
            return ByokKeyVerificationResult(
                isValid = false,
                message = "API key looks too short. Paste your full Gemini API key from Google AI Studio."
            )
        }

        return try {
            val rest = callByokRestWithFallback(
                apiKey = trimmed,
                preferredModel = preferredModel,
                systemInstruction = null,
                userText = "Reply with OK.",
                audioWavBytes = null,
                imageBitmap = null,
                enableSearch = false
            )
            ByokKeyVerificationResult(
                isValid = true,
                message = "Verified! Connected via ${rest.usedModel} (unlimited personal tier).",
                resolvedModel = rest.usedModel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ByokKeyVerificationResult(
                isValid = false,
                message = e.localizedMessage ?: "Could not verify Gemini API key."
            )
        }
    }

    private suspend fun diagnoseWithByok(
        apiKey: String,
        preferredModel: String,
        category: EquipmentCategory,
        equipmentName: String,
        symptomNotes: String,
        audioWavBytes: ByteArray?,
        imageBitmap: Bitmap?
    ): DiagnosticResult {
        val promptText = buildDiagnosticTextBlock(
            category = category,
            equipmentName = equipmentName,
            symptomNotes = symptomNotes,
            hasAudio = audioWavBytes != null && audioWavBytes.isNotEmpty(),
            hasImage = imageBitmap != null
        )

        val rest = callByokRestWithFallback(
            apiKey = apiKey,
            preferredModel = preferredModel,
            systemInstruction = diagnosticSystemPrompt,
            userText = promptText,
            audioWavBytes = audioWavBytes,
            imageBitmap = imageBitmap
        )

        return parseDiagnosticResponse(
            rawText = rest.text,
            sources = rest.sources,
            searchEntryHtml = rest.searchEntryHtml
        )
    }

    private data class ByokRestResponse(
        val text: String,
        val sources: List<GroundedSource>,
        val searchEntryHtml: String?,
        val usedModel: String
    )

    private suspend fun callByokRestWithFallback(
        apiKey: String,
        preferredModel: String,
        systemInstruction: String?,
        userText: String,
        audioWavBytes: ByteArray?,
        imageBitmap: Bitmap?,
        enableSearch: Boolean = true
    ): ByokRestResponse {
        val candidateModels = buildList {
            val cleanPreferred = preferredModel.trim().ifBlank { PRIMARY_MODEL }
            add(cleanPreferred)
            if (cleanPreferred != LITE_FALLBACK_MODEL) add(LITE_FALLBACK_MODEL)
            if (cleanPreferred != PRIMARY_MODEL) add(PRIMARY_MODEL)
        }

        var lastException: Exception? = null
        for (modelId in candidateModels) {
            try {
                return withTimeout(60_000L) {
                    executeByokRestRequest(
                        apiKey = apiKey,
                        modelName = modelId,
                        systemInstruction = systemInstruction,
                        userText = userText,
                        audioWavBytes = audioWavBytes,
                        imageBitmap = imageBitmap,
                        enableSearch = enableSearch
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val msg = e.message.orEmpty().lowercase()
                val isAuthError = msg.contains("401") || msg.contains("403") || msg.contains("api key")
                if (isAuthError) throw e
                Log.w(TAG, "BYOK call with $modelId failed (${e.message}), trying fallback if available")
            }
        }
        throw lastException ?: IllegalStateException("BYOK Gemini request failed.")
    }

    private suspend fun executeByokRestRequest(
        apiKey: String,
        modelName: String,
        systemInstruction: String?,
        userText: String,
        audioWavBytes: ByteArray?,
        imageBitmap: Bitmap?,
        enableSearch: Boolean
    ): ByokRestResponse = withContext(Dispatchers.IO) {
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 55_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val parts = JSONArray()
            if (audioWavBytes != null && audioWavBytes.isNotEmpty()) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "audio/wav")
                            .put("data", Base64.encodeToString(audioWavBytes, Base64.NO_WRAP))
                    )
                )
            }
            if (imageBitmap != null) {
                val scaled = scaleBitmapForUpload(imageBitmap)
                val jpegStream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, jpegStream)
                if (scaled !== imageBitmap) scaled.recycle()
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", Base64.encodeToString(jpegStream.toByteArray(), Base64.NO_WRAP))
                    )
                )
            }
            parts.put(JSONObject().put("text", userText))

            val body = JSONObject().apply {
                if (!systemInstruction.isNullOrBlank()) {
                    put(
                        "system_instruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemInstruction))
                        )
                    )
                }
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", parts)
                    )
                )
                if (enableSearch) {
                    put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                }
            }

            conn.outputStream.use { out ->
                out.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val rawJson = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val apiErrorMsg = runCatching {
                    JSONObject(rawJson).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                val friendly = when (code) {
                    400 -> apiErrorMsg.ifBlank { "Invalid Gemini request or API key." }
                    401, 403 -> "API key rejected ($code). Verify your Gemini key in AI Engine & BYOK settings."
                    404 -> "Model $modelName unavailable on your key ($code)."
                    429 -> "Personal API key rate limit reached (429). Wait a moment or switch to Flash-Lite."
                    else -> apiErrorMsg.ifBlank { "Gemini API error ($code)." }
                }
                throw IllegalStateException(friendly)
            }

            val root = JSONObject(rawJson)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IllegalStateException("Gemini returned no candidate response.")

            val textParts = candidate.optJSONObject("content")?.optJSONArray("parts")
            val combinedText = buildString {
                if (textParts != null) {
                    for (i in 0 until textParts.length()) {
                        val partText = textParts.optJSONObject(i)?.optString("text").orEmpty()
                        if (partText.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(partText)
                        }
                    }
                }
            }.trim()

            if (combinedText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }

            val grounding = candidate.optJSONObject("groundingMetadata")
            val sources = parseRestGroundingSources(grounding)
            val searchHtml = grounding?.optJSONObject("searchEntryPoint")
                ?.optString("renderedContent")
                ?.takeIf { it.isNotBlank() }

            ByokRestResponse(
                text = combinedText,
                sources = sources,
                searchEntryHtml = searchHtml,
                usedModel = modelName
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRestGroundingSources(grounding: JSONObject?): List<GroundedSource> {
        val chunks = grounding?.optJSONArray("groundingChunks") ?: return emptyList()
        val list = mutableListOf<GroundedSource>()
        for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val uri = web.optString("uri").trim()
            if (uri.isNotEmpty()) {
                val title = web.optString("title").trim().ifBlank { uri }
                list.add(GroundedSource(title = title, uri = uri))
            }
        }
        return list.distinctBy { it.uri }.take(8)
    }

    private fun shouldFallbackToLite(error: Exception): Boolean {
        val msg = (error.message.orEmpty() + " " + error.cause?.message.orEmpty()).lowercase()
        return msg.contains("429") ||
            msg.contains("quota") ||
            msg.contains("resource_exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("503")
    }

    private fun buildDiagnosticPrompt(
        category: EquipmentCategory,
        equipmentName: String,
        symptomNotes: String,
        audioWavBytes: ByteArray?,
        imageBitmap: Bitmap?
    ): Content = content {
        if (audioWavBytes != null && audioWavBytes.isNotEmpty()) {
            inlineData(audioWavBytes, "audio/wav")
        }
        if (imageBitmap != null) {
            image(scaleBitmapForUpload(imageBitmap))
        }

        text(
            buildDiagnosticTextBlock(
                category = category,
                equipmentName = equipmentName,
                symptomNotes = symptomNotes,
                hasAudio = audioWavBytes != null && audioWavBytes.isNotEmpty(),
                hasImage = imageBitmap != null
            )
        )
    }

    private fun buildDiagnosticTextBlock(
        category: EquipmentCategory,
        equipmentName: String,
        symptomNotes: String,
        hasAudio: Boolean,
        hasImage: Boolean
    ): String {
        val modalities = buildList {
            if (hasAudio) add("an audio recording of the mechanical noise")
            if (hasImage) add("a photo of the component / error display")
            if (symptomNotes.isNotBlank()) add("written symptom notes")
        }.joinToString(" + ").ifBlank { "symptom description" }

        return """
            Category: ${category.displayName}
            Make / Model / Equipment: ${equipmentName.ifBlank { "Not specified" }}
            Inputs attached: $modalities
            Symptom / Error Code / Notes: ${symptomNotes.ifBlank { "Analyze the attached media carefully." }}

            Instructions:
            - If audio is attached, analyze the rhythmic cadence, pitch, squeal, knock, rattle, or hum pattern to identify the most likely mechanical culprit.
            - If an image is attached, inspect any visible error codes, leaks, corrosion, blown fuses, belt wear, or pipe fitting types.
            - Cross-reference the exact make/model and error code via Google Search grounding for manufacturer repair guidance and safety warnings.
        """.trimIndent()
    }

    private fun scaleBitmapForUpload(source: Bitmap): Bitmap {
        val maxDimension = 1280
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun extractSources(response: GenerateContentResponse): List<GroundedSource> {
        val chunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks
            ?: return emptyList()
        return chunks.mapNotNull { chunk ->
            val web = chunk.web
            val uri = web?.uri?.trim().orEmpty()
            if (uri.isNotEmpty()) {
                val title = web?.title?.trim().orEmpty().ifBlank { uri }
                GroundedSource(title = title, uri = uri)
            } else {
                null
            }
        }.distinctBy { it.uri }.take(8)
    }

    private fun parseDiagnosticResponse(
        rawText: String,
        sources: List<GroundedSource>,
        searchEntryHtml: String?
    ): DiagnosticResult = parseDiagnosticResponseText(rawText, sources, searchEntryHtml)

    companion object {
        private const val TAG = "MechanicAiService"
        const val PRIMARY_MODEL = "gemini-2.5-flash"
        const val LITE_FALLBACK_MODEL = "gemini-2.5-flash-lite"

        internal fun parseDiagnosticResponseText(
            rawText: String,
            sources: List<GroundedSource> = emptyList(),
            searchEntryHtml: String? = null
        ): DiagnosticResult {
            val lines = rawText.lines()
            var title = ""
            var culprit = ""
            var confidence = 78
            var severity = DiagnosticSeverity.MEDIUM
            val summaryLines = mutableListOf<String>()
            val safetyWarnings = mutableListOf<String>()
            val diySteps = mutableListOf<String>()
            val partsTools = mutableListOf<String>()

            var currentSection = ""

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val normalizedHeader = line.trimStart('#', '*', '_', ' ').replace("**", "").replace("__", "")

                when {
                    normalizedHeader.startsWith("TITLE:", ignoreCase = true) -> {
                        title = normalizedHeader.substringAfter(":").trim()
                        currentSection = "TITLE"
                    }
                    normalizedHeader.startsWith("CULPRIT:", ignoreCase = true) -> {
                        culprit = normalizedHeader.substringAfter(":").trim()
                        currentSection = "CULPRIT"
                    }
                    normalizedHeader.startsWith("CONFIDENCE:", ignoreCase = true) -> {
                        val digits = normalizedHeader.substringAfter(":").filter { it.isDigit() }
                        confidence = digits.toIntOrNull()?.coerceIn(0, 100) ?: 78
                        currentSection = "CONFIDENCE"
                    }
                    normalizedHeader.startsWith("SEVERITY:", ignoreCase = true) -> {
                        val sevText = normalizedHeader.substringAfter(":").trim().uppercase()
                        severity = DiagnosticSeverity.entries.firstOrNull { sevText.contains(it.name) }
                            ?: DiagnosticSeverity.MEDIUM
                        currentSection = "SEVERITY"
                    }
                    normalizedHeader.startsWith("SUMMARY:", ignoreCase = true) -> {
                        val inline = normalizedHeader.substringAfter(":").trim()
                        if (inline.isNotEmpty()) summaryLines.add(inline)
                        currentSection = "SUMMARY"
                    }
                    normalizedHeader.startsWith("SAFETY_WARNINGS:", ignoreCase = true) -> {
                        currentSection = "SAFETY"
                    }
                    normalizedHeader.startsWith("DIY_STEPS:", ignoreCase = true) -> {
                        currentSection = "STEPS"
                    }
                    normalizedHeader.startsWith("PARTS_TOOLS:", ignoreCase = true) -> {
                        currentSection = "PARTS"
                    }
                    else -> {
                        val cleanedItem = line
                            .removePrefix("- ")
                            .removePrefix("* ")
                            .replace(Regex("^\\d+[.)]\\s*"), "")
                            .trim()
                        when (currentSection) {
                            "SUMMARY" -> summaryLines.add(line)
                            "SAFETY" -> if (cleanedItem.isNotEmpty()) safetyWarnings.add(cleanedItem)
                            "STEPS" -> if (cleanedItem.isNotEmpty()) diySteps.add(cleanedItem)
                            "PARTS" -> if (cleanedItem.isNotEmpty()) partsTools.add(cleanedItem)
                        }
                    }
                }
            }

            val finalSummary = summaryLines.joinToString(" ").ifBlank { rawText.take(1800) }
            val finalCulprit = culprit.ifBlank {
                finalSummary.lineSequence().firstOrNull()?.take(180) ?: "Mechanical inspection needed"
            }
            val finalTitle = title.ifBlank { finalCulprit.take(64) }

            val parsedWarnings = safetyWarnings
                .mapIndexed { idx, raw -> SafetyWarning.fromRaw(idx, raw, severity) }
                .ifEmpty {
                    listOf(
                        SafetyWarning(
                            id = "warn_0",
                            title = "Disconnect Power & Allow Cooldown",
                            detail = "Disconnect power or shut off fuel/water supply and let hot components cool completely before inspecting or disassembling.",
                            level = if (severity == DiagnosticSeverity.CRITICAL) SafetyWarningLevel.DANGER else SafetyWarningLevel.WARNING,
                            hazardType = SafetyHazardType.GENERAL,
                            actionRequiredBeforeWork = true
                        )
                    )
                }
                .sortedByDescending { it.level.priority }

            return DiagnosticResult(
                title = finalTitle,
                primaryCulprit = finalCulprit,
                confidencePercent = confidence,
                severity = severity,
                summary = finalSummary,
                safetyWarnings = parsedWarnings,
                diySteps = diySteps.ifEmpty {
                    listOf("Review the diagnostic summary above and inspect the highlighted component closely.")
                },
                partsTools = partsTools,
                sources = sources,
                searchEntryPointHtml = searchEntryHtml
            )
        }
    }
}
